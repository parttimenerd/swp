package nildumu;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

import swp.util.Pair;

import static nildumu.Parser.*;
import static nildumu.Parser.LexerTerminal.*;
import static nildumu.Util.p;

public class MetaOperatorTransformator implements NodeVisitor<MJNode> {

    private final int maxBitWidth;

    private final DefaultMap<ExpressionNode, ExpressionNode> replacedMap = new DefaultMap<>((map, node) -> {
        return repl(node);
    });

    private final DefaultMap<ConditionalStatementNode, ConditionalStatementNode> replacedCondStmtsMap = new DefaultMap<>((map, stmt) -> stmt);

    private final Map<WhileStatementNode, WhileStatementNode> whileStmtMap = new HashMap<>();

    MetaOperatorTransformator(int maxBitWidth) {
        this.maxBitWidth = maxBitWidth;
    }

    public ProgramNode process(ProgramNode program){
        ProgramNode newProgram = (ProgramNode)visit(program);
        setDefiningAndConditionalExpressions(newProgram);
        return newProgram;
    }

    ExpressionNode replace(ExpressionNode expression){
        return replacedMap.get(expression);
    }

    private ExpressionNode repl(ExpressionNode expression){
        return replaceExpressionWithExpression(expression, (node) -> node instanceof BinaryOperatorNode || node instanceof UnaryOperatorNode,  (node) -> {
            if (node instanceof BinaryOperatorNode) {
                BinaryOperatorNode binOp = (BinaryOperatorNode) node;
                switch (binOp.operator) {
                    case GREATER:
                        return new BinaryOperatorNode(binOp.right, binOp.left, LOWER);
                    case LOWER_EQUALS:
                    case GREATER_EQUALS:
                        ExpressionNode left = binOp.left;
                        ExpressionNode right = binOp.right;
                        LexerTerminal op = LOWER;
                        if (binOp.operator == GREATER_EQUALS) {
                            ExpressionNode tmp = left;
                            left = right;
                            right = tmp;
                            op = GREATER;
                        }
                        return new BinaryOperatorNode(new BinaryOperatorNode(left, right, op), new BinaryOperatorNode(left, right, op), BOR);
                    case MINUS:
                        return repl(new BinaryOperatorNode(new BinaryOperatorNode(binOp.left, new UnaryOperatorNode(binOp.right, TILDE), PLUS), new IntegerLiteralNode(binOp.location, Lattices.ValueLattice.get().parse(1)), PLUS));
                    case PLUS:
                        return plus(binOp);
                    default:
                        return binOp;
                }
            } else if (node instanceof UnaryOperatorNode){
                UnaryOperatorNode unOp = (UnaryOperatorNode)node;
                switch (unOp.operator){
                    case MINUS:
                        return new BinaryOperatorNode(new IntegerLiteralNode(unOp.location, Lattices.ValueLattice.get().parse(1)), new UnaryOperatorNode(unOp.expression, INVERT), PLUS);
                }
                return unOp;
            }
            return node;
        });
    }

    private Pair<ExpressionNode, ExpressionNode> halfAdder(ExpressionNode a, ExpressionNode b){
        return p(binop(XOR, a, b), binop(BAND, a, b));
    }

    private Pair<ExpressionNode, ExpressionNode> fullAdder(ExpressionNode a, ExpressionNode b, ExpressionNode c){
        Pair<ExpressionNode, ExpressionNode> pair = halfAdder(a, b);
        Pair<ExpressionNode, ExpressionNode> pair2 = halfAdder(pair.first, c);
        ExpressionNode carry = binop(BOR, pair.second, pair2.second);
        return p(pair2.first, carry);
    }

    private ExpressionNode plus(BinaryOperatorNode node){
        List<ExpressionNode> res = new ArrayList<>();
        ExpressionNode zero = new IntegerLiteralNode(node.location, Lattices.ValueLattice.get().parse(0));
        ExpressionNode result = zero;
        ExpressionNode carry = zero;
        for (int i = 1; i <= maxBitWidth; i++){
            Pair<ExpressionNode, ExpressionNode> rCarry = fullAdder(new SingleUnaryOperatorNode(node.left, SELECT_OP, i),
                    new SingleUnaryOperatorNode(node.right, SELECT_OP, i), carry);
            carry = rCarry.second;
            result = binop(BOR, result, new SingleUnaryOperatorNode(rCarry.first, PLACE_OP, i));
        }
        return result;
    }


    private BinaryOperatorNode binop(LexerTerminal op, ExpressionNode left, ExpressionNode right){
        return new BinaryOperatorNode(left, right, op);
    }

    ExpressionNode replaceExpressionWithExpression(ExpressionNode node, Predicate<ExpressionNode> matcher, Function<ExpressionNode, ExpressionNode> replacement){
        if (node == null){
            return null;
        }
        ExpressionNode replExpr = node.accept(new NodeVisitor<ExpressionNode>() {
            @Override
            public ExpressionNode visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public ExpressionNode visit(ExpressionNode expression) {
                return replace(expression);
            }

            @Override
            public ExpressionNode visit(UnaryOperatorNode unaryOperator) {
                return replace(new UnaryOperatorNode(visitAndReplace(unaryOperator.expression), unaryOperator.operator));
            }

            @Override
            public ExpressionNode visit(SingleUnaryOperatorNode unaryOperator) {
                return replace(new SingleUnaryOperatorNode(visitAndReplace(unaryOperator.expression), unaryOperator.operator, unaryOperator.index));
            }

            @Override
            public ExpressionNode visit(BinaryOperatorNode binaryOperator) {
                return replace(new BinaryOperatorNode(visitAndReplace(binaryOperator.left), visitAndReplace(binaryOperator.right), binaryOperator.operator));
            }

            @Override
            public ExpressionNode visit(PrimaryExpressionNode primaryExpression) {
                return replace(primaryExpression);
            }

            @Override
            public ExpressionNode visit(PhiNode phi) {
                PhiNode node = new PhiNode(phi.location, phi.controlDeps.stream().map(e -> e.accept(this)).collect(Collectors.toList()), new ArrayList<>(phi.joinedVariables.stream().map(j -> (VariableAccessNode)visit(j)).collect(Collectors.toList())));
                node.controlDepStatement = phi.controlDepStatement;
                return node;
            }

            private ExpressionNode replace(ExpressionNode node){
                if (matcher.test(node)){
                    return replacement.apply(node);
                }
                return node;
            }

            private ExpressionNode visitAndReplace(ExpressionNode node){
                node = replace(node);
                node = node.accept(this);
                node = replace(node);
                return node;
            }

            @Override
            public ExpressionNode visit(MethodInvocationNode methodInvocation) {
                MethodInvocationNode node = new MethodInvocationNode(methodInvocation.location, methodInvocation.method, new Parser.ArgumentsNode(methodInvocation.arguments.location, methodInvocation.arguments.arguments.stream().map(this::replace).collect(Collectors.toList())));
                node.definition = methodInvocation.definition;
                return node;
            }

            @Override
            public ExpressionNode visit(VariableAssignmentNode assignment) {
                assignment.expression = assignment.expression.accept(this);
                return null;
            }
        });
        replacedMap.put(node, replExpr);
        return replExpr;
    }

    @Override
    public MJNode visit(MJNode node) {
        visitChildrenDiscardReturn(node);
        return node;
    }

    @Override
    public MJNode visit(ProgramNode program) {
        List<StatementNode> newStatements = program.globalBlock.statementNodes.stream().map(s -> (StatementNode)s.accept(this)).collect(Collectors.toList());
        program.globalBlock.statementNodes.clear();
        program.globalBlock.statementNodes.addAll(newStatements);
        for (String methodName : program.getMethodNames()){
            MethodNode method = program.getMethod(methodName);
            visit(method);
        }
        return program;
    }

    @Override
    public MJNode visit(MethodNode method) {
        List<StatementNode> newStatementNodes = method.body.statementNodes.stream().map(v -> (StatementNode)v.accept(this)).collect(Collectors.toList());
        method.body.statementNodes.clear();
        method.body.statementNodes.addAll(newStatementNodes);
        return null;
    }

    @Override
    public MJNode visit(VariableAssignmentNode assignment){
        VariableAssignmentNode node = new VariableAssignmentNode(assignment.location, assignment.variable, replace(assignment.expression));
        node.definition = assignment.definition;
        return node;
    }

    @Override
    public MJNode visit(VariableDeclarationNode variableDeclaration){
        VariableDeclarationNode node = new VariableDeclarationNode(variableDeclaration.location, variableDeclaration.variable, replace(variableDeclaration.expression));
        node.definition = variableDeclaration.definition;
        return node;
    }

    @Override
    public MJNode visit(OutputVariableDeclarationNode decl){
        OutputVariableDeclarationNode node = new OutputVariableDeclarationNode(decl.location, decl.variable, replace(decl.expression), decl.secLevel);
        node.definition = decl.definition;
        return node;
    }

    @Override
    public MJNode visit(InputVariableDeclarationNode decl){
        InputVariableDeclarationNode node = new InputVariableDeclarationNode(decl.location, decl.variable, (IntegerLiteralNode) replace(decl.expression), decl.secLevel);
        node.definition = decl.definition;
        return node;
    }

    @Override
    public MJNode visit(BlockNode block){
        return new BlockNode(block.location, block.statementNodes.stream().map(s -> (StatementNode)s.accept(this)).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    @Override
    public MJNode visit(IfStatementNode ifStatement){
        ConditionalStatementNode stmt = new IfStatementNode(ifStatement.location, replace(ifStatement.conditionalExpression),
                (StatementNode)ifStatement.ifBlock.accept(this), (StatementNode)ifStatement.elseBlock.accept(this));
        replacedCondStmtsMap.put(ifStatement, stmt);
        return stmt;
    }

    @Override
    public MJNode visit(IfStatementEndNode ifEndStatement){
        return ifEndStatement;
    }

    @Override
    public MJNode visit(WhileStatementNode whileStatement){
        ConditionalStatementNode stmt = new WhileStatementNode(whileStatement.location, replace(whileStatement.conditionalExpression),
                (StatementNode)whileStatement.body.accept(this));
        replacedCondStmtsMap.put(whileStatement, stmt);
        return stmt;
    }

    @Override
    public MJNode visit(WhileStatementEndNode whileEndStatement){
        return null;
    }

    @Override
    public MJNode visit(ExpressionStatementNode expressionStatement){
        return new ExpressionStatementNode(replace(expressionStatement.expression));
    }

    @Override
    public MJNode visit(ReturnStatementNode returnStatement){
        if (returnStatement.hasReturnExpression()){
            return new ReturnStatementNode(replace(returnStatement.expression));
        }
        return new ReturnStatementNode(returnStatement.location);
    }

    public void setDefiningAndConditionalExpressions(MJNode node){
        Map<Variable, ExpressionNode> variableToExpr = new HashMap<>();
        node.accept(new NodeVisitor<Object>() {
            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(VariableAccessNode variableAccess) {
                variableAccess.definingExpression = replace(variableAccess.definingExpression);
                return null;
            }

            @Override
            public Object visit(PhiNode phi) {
                visitChildrenDiscardReturn(phi);
                phi.controlDeps = phi.controlDeps.stream().map(MetaOperatorTransformator.this::replace).collect(Collectors.toList());
                phi.joinedVariables.forEach(v -> v.definingExpression = replace(v.definingExpression));
                return null;
            }

            @Override
            public Object visit(VariableAssignmentNode assignment) {
                visitChildrenDiscardReturn(assignment);
                //assignment.expression = replace(assignment.expression);
                variableToExpr.put(assignment.definition, assignment.expression);
                return null;
            }
        });
        node.accept(new NodeVisitor<Object>() {
            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(VariableAccessNode variableAccess) {
                variableAccess.definingExpression = variableToExpr.get(variableAccess.definition);
                return null;
            }

            @Override
            public Object visit(PhiNode phi) {
                visitChildrenDiscardReturn(phi);
                phi.controlDepStatement = replacedCondStmtsMap.get(phi.controlDepStatement);
                assert phi.controlDeps.size() == 1;
                phi.controlDeps = Collections.singletonList(phi.controlDepStatement.conditionalExpression);
                return null;
            }
        });
    }
}

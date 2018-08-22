package nildumu;

import swp.parser.lr.BaseAST;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Does the conversion of a non SSA to a SSA AST, introduces new phi-nodes and variables
 */
public class SSAResolution implements Parser.NodeVisitor<SSAResolution.VisRet> {

    /**
     * Result of visiting a statement
     */
    static class VisRet {

        static final VisRet DEFAULT = new VisRet(false, Collections.emptyList());

        final boolean removeCurrentStatement;

        final List<Parser.StatementNode> statementsToAdd;

        VisRet(boolean removeCurrentStatement, List<Parser.StatementNode> statementsToAdd) {
            this.removeCurrentStatement = removeCurrentStatement;
            this.statementsToAdd = statementsToAdd;
        }

        VisRet(boolean removeCurrentStatement, Parser.StatementNode... statementsToAdd) {
            this(removeCurrentStatement, Arrays.asList(statementsToAdd));
        }

        boolean isDefault(){
            return !removeCurrentStatement && statementsToAdd.isEmpty();
        }
    }

    private final Parser.ProgramNode program;
    /**
     * Newly introduced variables for an old one
     */
    private Stack<Map<Variable, Variable>> newVariables;

    /**
     * Maps newly introduced variables to their origins
     */
    private Map<Variable, Variable> reverseMapping;

    /**
     * Variable → Variable it overrides
     */
    private Map<Variable, Variable> directPredecessor;

    private Map<Variable, Integer> versionCount;

    public SSAResolution(Parser.ProgramNode program) {
        this.program = program;
    }

    public void resolve(){
        reverseMapping = new HashMap<>();
        versionCount = new HashMap<>();
        directPredecessor = new HashMap<>();
        newVariables = new Stack<>();
        program.accept(this);
    }

    @Override
    public VisRet visit(Parser.MJNode node) {
        visitChildrenDiscardReturn(node);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(Parser.ProgramNode program) {
        newVariables.push(new HashMap<>());
        visitChildrenDiscardReturn(program);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(Parser.VariableDeclarationNode declaration) {
        return visit((Parser.MJNode)declaration);
    }

    @Override
    public VisRet visit(Parser.VariableAssignmentNode assignment) {
        visitChildrenDiscardReturn(assignment.expression);
        Variable newVariable = create(assignment.definition);
        assignment.definition = newVariable;
        return new VisRet(true,
                new Parser.VariableDeclarationNode(assignment.location, newVariable, assignment.expression));
    }

    @Override
    public VisRet visit(Parser.BlockNode block) {
        List<Parser.StatementNode> blockPartNodes = new ArrayList<>();
        for (Parser.StatementNode child : block.statementNodes){
            VisRet ret = child.accept(this);
            if (!ret.removeCurrentStatement) {
                blockPartNodes.add(child);
            }
            blockPartNodes.addAll(ret.statementsToAdd);
        }
        block.statementNodes.clear();
        block.statementNodes.addAll(blockPartNodes);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(Parser.MethodNode method) {
        method.parameters.accept(this);
        return method.body.accept(this);
    }

    @Override
    public VisRet visit(Parser.ParameterNode parameter) {
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(Parser.IfStatementNode ifStatement) {
        visitChildrenDiscardReturn(ifStatement.conditionalExpression);

        newVariables.push(new IdentityHashMap<>());

        VisRet toAppend = ifStatement.ifBlock.accept(this);
        ifStatement.ifBlock.statementNodes.addAll(toAppend.statementsToAdd);
        Map<Variable, Variable> ifRedefines = newVariables.pop();

        newVariables.push(new IdentityHashMap<>());
        toAppend = ifStatement.elseBlock.accept(this);
        ifStatement.elseBlock.statementNodes.addAll(toAppend.statementsToAdd);
        Map<Variable, Variable> elseRedefines = newVariables.pop();

        Set<Variable> redefinedVariables = new HashSet<>();
        redefinedVariables.addAll(ifRedefines.keySet());
        redefinedVariables.addAll(elseRedefines.keySet());

        List<Parser.StatementNode> phiStatements = new ArrayList<>();
        for (Variable var : redefinedVariables){
            List<Variable> varsToJoin = new ArrayList<>();
            varsToJoin.add(ifRedefines.getOrDefault(var, var));
            varsToJoin.add(elseRedefines.getOrDefault(var, var));
            Variable created = create(var);
            Parser.VariableDeclarationNode localVarDecl =
                    new Parser.VariableDeclarationNode(ifStatement.location, created.name, new Parser.PhiNode(ifStatement.location, Collections.singletonList(ifStatement.conditionalExpression), varsToJoin));
            localVarDecl.definition = created;
            phiStatements.add(localVarDecl);
        }
        return new VisRet(false, phiStatements);
    }

    @Override
    public VisRet visit(Parser.WhileStatementNode whileStatement) {
        visitChildrenCollectReturn(whileStatement.conditionalExpression);
        newVariables.push(new HashMap<>());
        whileStatement.body.accept(this);
        Set<Variable> variablesAssigned = getAssignedOutsideVariables(whileStatement.body);
        Map<Variable, Variable> variableAndWhileEnd = new HashMap<>();
        for (Variable variable : variablesAssigned){
            Variable whileEndVariable = resolve(variable);
            Variable newVariable = create(variable);
            whileStatement.body.statementNodes.add(0,
                    new Parser.VariableDeclarationNode(whileStatement.location, newVariable,
                        new Parser.PhiNode(whileStatement.location, Collections.singletonList(whileStatement.conditionalExpression), Arrays.asList(variable, whileEndVariable))));
            replaceVariable(variable, newVariable, whileStatement.body);
            whileStatement.conditionalExpression = replaceVariableWithExpression(variable, new Parser.PhiNode(whileStatement.location, Collections.singletonList(whileStatement.conditionalExpression), Arrays.asList(variable, whileEndVariable)), whileStatement.conditionalExpression);
            variableAndWhileEnd.put(variable, whileEndVariable);
        }
        newVariables.pop();
        return new VisRet(false,
                variableAndWhileEnd.entrySet().stream().map(e ->
                new Parser.VariableDeclarationNode(whileStatement.location, create(e.getKey()),
                        new Parser.PhiNode(whileStatement.location, Collections.singletonList(whileStatement.conditionalExpression), Arrays.asList(e.getKey(), e.getValue()))))
                .collect(Collectors.toList()));
    }

    @Override
    public VisRet visit(Parser.VariableAccessNode variableAccess) {
        variableAccess.definition = resolve(variableAccess.definition);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(Parser.MethodInvocationNode methodInvocation) {
        methodInvocation.definition = program.getMethod(methodInvocation.method);
        visitChildrenDiscardReturn(methodInvocation);
        return VisRet.DEFAULT;
    }

    /**
     * Resolve the current version of the variable
     */
    private Variable resolve(Variable variable){
        for (int i = newVariables.size() - 1; i >= 0; i--){
            if (newVariables.get(i).containsKey(variable)){
                return newVariables.get(i).get(variable);
            }
        }
        return variable;
    }

    private Variable resolveOrigin(Variable variable){
        return reverseMapping.getOrDefault(variable, variable);
    }

    private int numberOfVersions(Variable variable){
        return versionCount.getOrDefault(variable, 0);
    }

    /**
     * Create a new variable
     */
    private Variable create(Variable variable){
        Variable origin = resolveOrigin(variable);
        String name = origin.name + (numberOfVersions(origin) + 1);
        Variable newVariable = new Variable(name, false, false);
        versionCount.put(origin, numberOfVersions(variable) + 1);
        reverseMapping.put(newVariable, origin);
        newVariables.get(newVariables.size() - 1).put(origin, newVariable);
        directPredecessor.put(newVariable, variable);
        return newVariable;
    }

    /**
     * Visit all child nodes and collect the return (flatten the resulting list
     */
    private VisRet visitChildrenCollectReturn(Parser.MJNode node){
        if (!(node instanceof Parser.StatementNode)){
            visitChildrenDiscardReturn(node);
            return VisRet.DEFAULT;
        }
        List<Parser.StatementNode> retStatements = new ArrayList<>();
        for (BaseAST childAst : node.children()){
            Parser.MJNode child = (Parser.MJNode)childAst;
            if (child instanceof Parser.StatementNode){
                VisRet ret = child.accept(this);
                if (!ret.removeCurrentStatement){
                    retStatements.add((Parser.StatementNode)child);
                }
                retStatements.addAll(ret.statementsToAdd);
            } else {
                child.accept(this);
            }
        }
        return new VisRet(true, retStatements);
    }

    /**
     * Get variables that are assigned in the passed node, but asigned outside of it
     *
     * @return assigned nodes that are not defined in the node
     */
    Set<Variable> getAssignedOutsideVariables(Parser.MJNode node){
        Set<Variable> assignedVariables = new HashSet<>();
        Set<Variable> definedVariables = new HashSet<>();
        node.accept(new Parser.NodeVisitor<Object>() {
            @Override
            public Object visit(Parser.MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(Parser.VariableDeclarationNode variableDeclaration) {
                if (directPredecessor.containsKey(variableDeclaration.definition)) {
                    assignedVariables.add(directPredecessor.get(variableDeclaration.definition));
                }
                definedVariables.add(variableDeclaration.definition);
                return null;
            }
        });
        assignedVariables.removeAll(definedVariables);
        return assignedVariables;
    }

    static void replaceVariable(Variable search, Variable replacement, Parser.MJNode node){
        node.accept(new Parser.NodeVisitor<Object>() {
            @Override
            public Object visit(Parser.MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(Parser.VariableAccessNode variableAccess) {
                if (variableAccess.definition == search){
                    variableAccess.definition = replacement;
                }
                return null;
            }
        });
    }

    static Parser.ExpressionNode replaceVariableWithExpression(Variable search, Parser.ExpressionNode replacement, Parser.ExpressionNode node){
        return node.accept(new Parser.NodeVisitor<Parser.ExpressionNode>() {
            @Override
            public Parser.ExpressionNode visit(Parser.MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Parser.ExpressionNode visit(Parser.ExpressionNode expression) {
                return expression;
            }

            @Override
            public Parser.ExpressionNode visit(Parser.PhiNode phi) {
                throw new RuntimeException("Shouldn't occur");
            }

            @Override
            public Parser.ExpressionNode visit(Parser.UnaryOperatorNode unaryOperator) {
                return new Parser.UnaryOperatorNode(visitAndReplaceVariable(unaryOperator.expression), unaryOperator.operator);
            }

            @Override
            public Parser.ExpressionNode visit(Parser.BinaryOperatorNode binaryOperator) {
                return new Parser.BinaryOperatorNode(visitAndReplaceVariable(binaryOperator.left), visitAndReplaceVariable(binaryOperator.right), binaryOperator.operator);
            }

            @Override
            public Parser.ExpressionNode visit(Parser.PrimaryExpressionNode primaryExpression) {
                if (isNodeToReplace(primaryExpression)){
                    return replacement;
                }
                return primaryExpression;
            }

            private Parser.ExpressionNode visitAndReplaceVariable(Parser.ExpressionNode node){
                if (isNodeToReplace(node)){
                    return replacement;
                }
                return node.accept(this);
            }

            private boolean isNodeToReplace(Parser.ExpressionNode node){
                return node instanceof Parser.VariableAccessNode && ((Parser.VariableAccessNode) node).definition == search;
            }
        });
    }
}

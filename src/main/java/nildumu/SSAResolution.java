package nildumu;

import java.util.*;

import swp.parser.lr.BaseAST;

import static nildumu.Parser.*;

/**
 * Does the conversion of a non SSA to a SSA AST, introduces new phi-nodes and variables
 */
public class SSAResolution implements NodeVisitor<SSAResolution.VisRet> {

    /**
     * Result of visiting a statement
     */
    static class VisRet {

        static final VisRet DEFAULT = new VisRet(false, Collections.emptyList(), Collections.emptyList());

        final boolean removeCurrentStatement;

        final List<StatementNode> statementsToAdd;

        final List<StatementNode> statementsToPrepend;

        VisRet(boolean removeCurrentStatement, List<StatementNode> statementsToAdd, List<StatementNode> statementsToPrepend) {
            this.removeCurrentStatement = removeCurrentStatement;
            this.statementsToAdd = statementsToAdd;
            this.statementsToPrepend = statementsToPrepend;
        }

        VisRet(boolean removeCurrentStatement, StatementNode... statementsToAdd) {
            this(removeCurrentStatement, Arrays.asList(statementsToAdd), Collections.emptyList());
        }

        boolean isDefault(){
            return !removeCurrentStatement && statementsToAdd.isEmpty() && statementsToPrepend.isEmpty();
        }
    }

    private final ProgramNode program;
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

    private MethodNode currentMethod;

    public SSAResolution(ProgramNode program) {
        reverseMapping = new HashMap<>();
        versionCount = new HashMap<>();
        directPredecessor = new HashMap<>();
        newVariables = new Stack<>();
        this.program = program;
    }

    public void resolve(){
        resolve(program);
    }

    public void resolve(MJNode node){
        node.accept(this);
        assignDefiningExpressions(node);
    }

    void pushNewVariablesScope(){
        newVariables.push(new IdentityHashMap<>());
    }

    void popNewVariablesScope(){
        newVariables.pop();
    }

    @Override
    public VisRet visit(MJNode node) {
        visitChildrenDiscardReturn(node);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(ProgramNode program) {
        pushNewVariablesScope();
        visitChildrenDiscardReturn(program);
        popNewVariablesScope();
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(VariableDeclarationNode declaration) {
        return visit((MJNode)declaration);
    }

    @Override
    public VisRet visit(VariableAssignmentNode assignment) {
        visitChildrenDiscardReturn(assignment.expression);
        Variable newVariable = create(assignment.definition);
        assignment.definition = newVariable;
        return new VisRet(true,
                new VariableDeclarationNode(assignment.location, newVariable, assignment.expression));
    }

    @Override
    public VisRet visit(BlockNode block) {
        List<StatementNode> blockPartNodes = new ArrayList<>();
        for (StatementNode child : block.statementNodes){
            VisRet ret = child.accept(this);
            blockPartNodes.addAll(ret.statementsToPrepend);
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
    public VisRet visit(MethodNode method) {
        SSAResolution resolution = new SSAResolution(program);
        resolution.currentMethod = method;
        resolution.pushNewVariablesScope();
        resolution.resolve(method.parameters);
        resolution.resolve(method.body);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(ParameterNode parameter) {
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(IfStatementNode ifStatement) {
        return visit(ifStatement, ifStatement.ifBlock, ifStatement.elseBlock);
    }

    public VisRet visit(ConditionalStatementNode statement, BlockNode ifBlock, BlockNode elseBlock) {
        visitChildrenDiscardReturn(statement.conditionalExpression);

        pushNewVariablesScope();

        VisRet toAppend = ifBlock.accept(this);
        ifBlock.statementNodes.addAll(0, toAppend.statementsToPrepend);
        ifBlock.statementNodes.addAll(toAppend.statementsToAdd);
        Map<Variable, Variable> ifRedefines = newVariables.pop();

        pushNewVariablesScope();
        toAppend = elseBlock.accept(this);
        elseBlock.statementNodes.addAll(0, toAppend.statementsToPrepend);
        elseBlock.statementNodes.addAll(toAppend.statementsToAdd);
        Map<Variable, Variable> elseRedefines = newVariables.pop();

        Set<Variable> redefinedVariables = new HashSet<>();
        redefinedVariables.addAll(ifRedefines.keySet());
        redefinedVariables.addAll(elseRedefines.keySet());

        List<StatementNode> phiStatements = new ArrayList<>();
        for (Variable var : redefinedVariables){
            List<Variable> varsToJoin = new ArrayList<>();
            varsToJoin.add(ifRedefines.getOrDefault(var, resolve(var)));
            varsToJoin.add(elseRedefines.getOrDefault(var, resolve(var)));
            Variable created = create(var);
            PhiNode phi = new PhiNode(statement.location, Collections.singletonList(statement.conditionalExpression), varsToJoin);
            phi.controlDepStatement = statement;
            VariableDeclarationNode localVarDecl =
                    new VariableDeclarationNode(statement.location, created.name, phi);
            localVarDecl.definition = created;
            phiStatements.add(localVarDecl);
        }
        return new VisRet(false, phiStatements, Collections.emptyList());
    }

    @Override
    public VisRet visit(WhileStatementNode whileStatement) {
        VisRet ret = visit(whileStatement, whileStatement.body, new BlockNode(whileStatement.location, new ArrayList<>()));
        List<StatementNode> prepend = new ArrayList<>(ret.statementsToPrepend);
        List<StatementNode> stmtsToAdd = new ArrayList<>();
        for (StatementNode statementNode : ret.statementsToAdd) {
            PhiNode phi = (PhiNode)((VariableDeclarationNode)statementNode).expression;
            Variable whileEnd = phi.joinedVariables.get(0).definition;
            Variable beforeWhile = phi.joinedVariables.get(1).definition;
            Variable newWhilePhiPreVar = create(whileEnd);
            Variable newWhilePhiVar = create(newWhilePhiPreVar);

            replaceVariable(beforeWhile, newWhilePhiVar, whileStatement.body);
            //prepend.add(new VariableDeclarationNode(whileStatement.location, newWhilePhiVar, phi));
            whileStatement.addPreCondVarDefs(Arrays.asList(new VariableDeclarationNode(whileStatement.location, newWhilePhiPreVar, phi)));
            //System.err.println(newWhilePhiPreVar + "  " + newWhilePhiVar);
            replaceVariable(beforeWhile, newWhilePhiPreVar, whileStatement.conditionalExpression);

            whileStatement.body.statementNodes.add(0, new VariableDeclarationNode(whileStatement.location, newWhilePhiVar, phi));
            stmtsToAdd.add(new VariableDeclarationNode(statementNode.location, newWhilePhiVar, phi));
        }
        return new VisRet(ret.removeCurrentStatement, stmtsToAdd, prepend);
    }

    /**
     * Replaces all controlDeps that are exactly the passed variable
     */
    private void replaceVariableInPhiConds(Variable var, ExpressionNode expr, MJNode node){
        node.accept(new NodeVisitor<Object>() {

            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(PhiNode phi) {
                phi.alterCondDeps(e -> e instanceof VariableAccessNode && ((VariableAccessNode) e).definition == var ? expr : e);
                return null;
            }
        });
    }

    @Override
    public VisRet visit(VariableAccessNode variableAccess) {
        variableAccess.definition = resolve(variableAccess.definition);
        if (currentMethod != null && currentMethod.parameters.parameterNodes.stream().anyMatch(p -> p.definition == variableAccess.definition)){
            variableAccess.definingExpression = new ParameterAccessNode(variableAccess.location, variableAccess.ident);
            ((ParameterAccessNode) variableAccess.definingExpression).definition = variableAccess.definition;
        }
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(MethodInvocationNode methodInvocation) {
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
        Variable pred = resolve(variable);
        directPredecessor.put(newVariable, pred);
        versionCount.put(origin, numberOfVersions(origin) + 1);
        reverseMapping.put(newVariable, origin);
        newVariables.get(newVariables.size() - 1).put(origin, newVariable);
        return newVariable;
    }

    private Variable basicCreate(Variable variable){
        Variable origin = resolveOrigin(variable);
        String name = origin.name + (numberOfVersions(origin) + 1);
        Variable newVariable = new Variable(name, false, false);
        reverseMapping.put(newVariable, resolveOrigin(variable));
        return newVariable;
    }
    /**
     * Visit all child nodes and collect the return (flatten the resulting list
     */
    private VisRet visitChildrenCollectReturn(MJNode node){
        if (!(node instanceof StatementNode)){
            visitChildrenDiscardReturn(node);
            return VisRet.DEFAULT;
        }
        List<StatementNode> retStatements = new ArrayList<>();
        for (BaseAST childAst : node.children()){
            MJNode child = (MJNode)childAst;
            if (child instanceof StatementNode){
                VisRet ret = child.accept(this);
                retStatements.addAll(ret.statementsToPrepend);
                if (!ret.removeCurrentStatement){
                    retStatements.add((StatementNode)child);
                }
                retStatements.addAll(ret.statementsToAdd);
            } else {
                child.accept(this);
            }
        }
        return new VisRet(true, retStatements, Collections.emptyList());
    }

    /**
     * Get variables that are assigned in the passed node, but asigned outside of it
     *
     * @return assigned nodes that are not defined in the node
     */
    Set<Variable> getAssignedOutsideVariables(MJNode node){
        Set<Variable> assignedVariables = new HashSet<>();
        Set<Variable> definedVariables = new HashSet<>();
        node.accept(new NodeVisitor<Object>() {
            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(VariableDeclarationNode variableDeclaration) {
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

    static void replaceVariable(Variable search, Variable replacement, MJNode node){
        node.accept(new NodeVisitor<Object>() {
            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(VariableAccessNode variableAccess) {
                if (variableAccess.definition == search){
                    variableAccess.definition = replacement;
                }
                return null;
            }
        });
    }

    static void replaceVariable(Variable search, ExpressionNode replacement, MJNode node){
        node.accept(new NodeVisitor<Object>() {
            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(VariableAccessNode variableAccess) {
                if (variableAccess.definition == search){
                    variableAccess.definingExpression = replacement;
                }
                return null;
            }
        });
    }


    static ExpressionNode replaceVariableWithExpression(Variable search, ExpressionNode replacement, ExpressionNode node){
        return node.accept(new NodeVisitor<ExpressionNode>() {
            @Override
            public ExpressionNode visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public ExpressionNode visit(ExpressionNode expression) {
                return expression;
            }

            @Override
            public ExpressionNode visit(PhiNode phi) {
                //throw new RuntimeException("Shouldn't occur");
                return phi;
            }

            @Override
            public ExpressionNode visit(UnaryOperatorNode unaryOperator) {
                return new UnaryOperatorNode(visitAndReplaceVariable(unaryOperator.expression), unaryOperator.operator);
            }

            @Override
            public ExpressionNode visit(BinaryOperatorNode binaryOperator) {
                return new BinaryOperatorNode(visitAndReplaceVariable(binaryOperator.left), visitAndReplaceVariable(binaryOperator.right), binaryOperator.operator);
            }

            @Override
            public ExpressionNode visit(PrimaryExpressionNode primaryExpression) {
                if (isNodeToReplace(primaryExpression)){
                    return replacement;
                }
                return primaryExpression;
            }

            private ExpressionNode visitAndReplaceVariable(ExpressionNode node){
                if (isNodeToReplace(node)){
                    return replacement;
                }
                return node.accept(this);
            }

            private boolean isNodeToReplace(ExpressionNode node){
                return node instanceof VariableAccessNode && ((VariableAccessNode) node).definition == search;
            }
        });
    }
    
    void assignDefiningExpressions(MJNode node){
        Map<Variable, ExpressionNode> definingExprs = new HashMap<>();
        // collect the defining expressions
        node.accept(new NodeVisitor<Object>(){

            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(ExpressionNode expression) {
                return null;
            }

            @Override
            public Object visit(VariableAssignmentNode assignment) {
                definingExprs.put(assignment.definition, assignment.expression);
                return null;
            }

            @Override
            public Object visit(MethodNode method) {
                return null;
            }
        });
        if (currentMethod != null){
            currentMethod.parameters.parameterNodes.forEach(p -> {
                ParameterAccessNode access = new ParameterAccessNode(p.location, p.name);
                access.definition = p.definition;
                definingExprs.put(p.definition, access);
            });
        }
        Set<VariableAccessNode> accessesToAccesses = new HashSet<>();
        // set defining expressions
        node.accept(new NodeVisitor<Object>() {
            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(VariableAccessNode variableAccess) {
               // System.err.println("----" + variableAccess + "  " + variableAccess.definingExpression);
                if (variableAccess.definingExpression != null){
                    return null;
                }
                variableAccess.definingExpression = definingExprs.get(variableAccess.definition);
                //System.err.println("----" + variableAccess + "  " + variableAccess.definingExpression);
                if (variableAccess.definingExpression instanceof VariableAccessNode && !(variableAccess.definingExpression instanceof ParameterAccessNode)){
                    accessesToAccesses.add(variableAccess);
                }
                return null;
            }

            @Override
            public Object visit(ParameterAccessNode variableAccess) {
                return null;
            }

            @Override
            public Object visit(MethodNode method) {
                return null;
            }
        });
        while (accessesToAccesses.size() > 0){
            new HashSet<>(accessesToAccesses).forEach(access -> {
                ExpressionNode newDefining = definingExprs.get(((VariableAccessNode)access.definingExpression).definition);
                if (!(newDefining instanceof VariableAccessNode) || newDefining instanceof ParameterAccessNode){
                    accessesToAccesses.remove(access);
                }
            });
        }
    }
}

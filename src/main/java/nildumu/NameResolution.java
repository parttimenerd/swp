package nildumu;

import static nildumu.Parser.*;

/**
 * A simple name resolution that sets the {@code definition} variable in {@link VariableAssignmentNode},
 * {@link VariableDeclarationNode}, {@link VariableAccessNode} and {@link ParameterNode}.
 *
 * Also connects the {@link MethodInvocationNode} with the correct {@link MethodNode}.
 */
public class NameResolution implements Parser.NodeVisitor<Object> {

    public static class WrongNumberOfArgumentsError extends NildumuError {
        public WrongNumberOfArgumentsError(MethodInvocationNode invocation, String msg){
            super(String.format("%s: %s", invocation, msg));
        }
    }

    private SymbolTable symbolTable;
    private final ProgramNode program;

    public NameResolution(ProgramNode program) {
        this.program = program;
        this.symbolTable = new SymbolTable();
    }

    public void resolve(){
        program.accept(this);
    }

    @Override
    public Object visit(Parser.MJNode node) {
        visitChildrenDiscardReturn(node);
        return null;
    }

    @Override
    public Object visit(ProgramNode program) {
        visitChildrenDiscardReturn(program);
        NodeVisitor<Object> visitor = new NodeVisitor<Object>(){


            @Override
            public Object visit(MJNode node) {
                return null;
            }

            @Override
            public Object visit(VariableDeclarationNode variableDeclaration) {
                Variable var = variableDeclaration.definition;
                if (var.isInput){
                    program.addInputVariable(var);
                }
                if (var.isOutput){
                    program.addOuputVariable(var);
                }
                return null;
            }
        };
        program.globalBlock.children().forEach(n -> ((MJNode)n).accept(visitor));
        return null;
    }

    @Override
    public Object visit(VariableDeclarationNode variableDeclaration) {
        if (symbolTable.isDirectlyInCurrentScope(variableDeclaration.variable)){
            throw new MJError(String.format("Variable %s already defined in scope", variableDeclaration.variable));
        }
        Variable definition = new Variable(variableDeclaration.variable,
                variableDeclaration instanceof InputVariableDeclarationNode,
                variableDeclaration instanceof OutputVariableDeclarationNode);
        symbolTable.insert(variableDeclaration.variable, definition);
        variableDeclaration.definition = definition;
        return visit((VariableAssignmentNode)variableDeclaration);
    }

    @Override
    public Object visit(VariableAssignmentNode assignment) {
        symbolTable.throwIfNotInCurrentScope(assignment.variable);
        assignment.definition = symbolTable.lookup(assignment.variable);
        if (assignment.expression != null){
            assignment.expression.accept(this);
        }
        return null;
    }

    @Override
    public Object visit(VariableAccessNode variableAccess) {
        symbolTable.throwIfNotInCurrentScope(variableAccess.ident);
        variableAccess.definition = symbolTable.lookup(variableAccess.ident);
        return null;
    }

    @Override
    public Object visit(BlockNode block) {
        symbolTable.enterScope();
        visitChildrenDiscardReturn(block);
        symbolTable.leaveScope();
        return null;
    }

    @Override
    public Object visit(MethodNode method) {
        SymbolTable oldSymbolTable = symbolTable;
        symbolTable = new SymbolTable();
        symbolTable.enterScope();
        visitChildrenDiscardReturn(method);
        symbolTable.leaveScope();
        symbolTable = oldSymbolTable;
        return null;
    }

    @Override
    public Object visit(ParameterNode parameter) {
        if (symbolTable.isDirectlyInCurrentScope(parameter.name)) {
            throw new MJError(String.format("A parameter with the name %s already is already defined for the method", parameter.name));
        }
        Variable definition = new Variable(parameter.name);
        symbolTable.insert(parameter.name, definition);
        parameter.definition = definition;
        return null;
    }

    @Override
    public Object visit(MethodInvocationNode methodInvocation) {
        if (!program.hasMethod(methodInvocation.method)){
            throw new MJError(String.format("%s: No such method %s", methodInvocation, methodInvocation.method));
        }
        MethodNode method = program.getMethod(methodInvocation.method);
        methodInvocation.definition = method;
        visitChildrenDiscardReturn(methodInvocation);
        if (methodInvocation.arguments.size() != method.parameters.size()){
            throw new WrongNumberOfArgumentsError(methodInvocation, String.format("Expected %d arguments got %d", method.parameters.size(), methodInvocation.arguments.size()));
        }
        return null;
    }
}

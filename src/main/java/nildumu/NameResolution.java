package nildumu;

import static nildumu.Parser.*;

/**
 * A simple name resolution that sets the {@code definition} variable in {@link VariableAssignmentNode},
 * {@link VariableDeclarationNode}, {@link VariableAccessNode} and {@link ParameterNode}.
 *
 * Also connects the {@link MethodInvocationNode} with the correct {@link MethodNode}.
 */
public class NameResolution implements Parser.NodeVisitor<Object> {

    private final SymbolTable symbolTable;
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
        symbolTable.enterScope();
        visitChildrenDiscardReturn(method);
        symbolTable.leaveScope();
        return null;
    }

    @Override
    public Object visit(ParameterNode parameter) {
        if (symbolTable.isDirectlyInCurrentScope(parameter.name)){
            throw new MJError(String.format("Parameter %s already defined for this method", parameter.name));
        }
        Variable definition = new Variable(parameter.name, false, false);
        return null;
    }

    @Override
    public Object visit(MethodInvocationNode methodInvocation) {
        methodInvocation.definition = program.getMethod(methodInvocation.method);
        visitChildrenDiscardReturn(methodInvocation);
        return null;
    }
}

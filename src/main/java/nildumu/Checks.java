package nildumu;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static nildumu.Parser.*;

/**
 * Sanity checks for the AST
 */
public class Checks {

    public static enum ErrorType {
        GENERAL,
        DEFINITION_MISSING,
        UNEVALUATED;

        @Override
        public String toString() {
            return name().toLowerCase().replace('_', ' ');
        }
    }

    public static class ErrorMessage {
        private final ErrorType type;
        private final String message;

        public ErrorMessage(ErrorType type, String message) {
            this.type = type;
            this.message = message;
        }

        public ErrorType getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s", type, message);
        }
    }

    public static class LocatedErrorMessage extends ErrorMessage {
        private final List<MJNode> path;

        public LocatedErrorMessage(ErrorType type, String message, List<MJNode> path) {
            super(type, message);
            this.path = path;
        }

        @Override
        public String toString() {
            return String.format("[%s|%s] %s", getType(), path.stream().map(MJNode::getTextualId).collect(Collectors.joining(", ")), getMessage());
        }
    }

    /**
     * List of errors
     */
    public static class ErrorPipe extends AbstractList<ErrorMessage> {
        private final List<ErrorMessage> errors;

        ErrorPipe() {
            errors = new ArrayList<>();
        }

        @Override
        public ErrorMessage get(int index) {
            return errors.get(index);
        }

        @Override
        public int size() {
            return errors.size();
        }

        @Override
        public String toString() {
            return errors.stream().collect(Collectors.groupingBy(ErrorMessage::getType, Collectors.counting())).entrySet().stream().map(e -> String.format("%s: %d", e.getKey(), e.getValue())).collect(Collectors.joining(", "));
        }

        public String toLongString(){
            return errors.stream().map(ErrorMessage::toString).collect(Collectors.joining("\n"));
        }

        @Override
        public boolean add(ErrorMessage errorMessage) {
            return errors.add(errorMessage);
        }
    }

    public static class CheckError extends NildumuError {
        final ErrorPipe errors;

        public CheckError(ErrorPipe errors) {
            super(errors.toLongString());
            this.errors = errors;
        }
    }

    @FunctionalInterface
    public static interface NullaryFunction {
        public void call();
    }

    public static abstract class ErrorReportingVisitor implements NodeVisitor<ErrorPipe> {
        final Stack<MJNode> currentPath = new Stack<>();
        final ErrorPipe errors = new ErrorPipe();
        final ErrorType type;

        public ErrorReportingVisitor(){
            this(ErrorType.GENERAL);
        }

        public ErrorReportingVisitor(ErrorType type){
            this.type = type;
        }

        public ErrorReportingVisitor error(String message, Object... args){
            return error(type, String.format(message, args));
        }

        public ErrorReportingVisitor error(ErrorType type, String message){
            errors.add(new LocatedErrorMessage(type, message, new ArrayList<>(currentPath)));
            return this;
        }

        @Override
        public ErrorPipe visit(MJNode node) {
            currentPath.add(node);
            visitChildrenDiscardReturn(node);
            currentPath.pop();
            return errors;
        }

        public <T extends MJNode> ErrorPipe process(T node, Consumer<T> consumer){
            currentPath.push(node);
            consumer.accept(node);
            currentPath.pop();
            return errors;
        }

        public <T extends MJNode> ErrorPipe process(T node, NullaryFunction function){
            currentPath.push(node);
            function.call();
            currentPath.pop();
            return errors;
        }
    }

    /**
     * Checks for basic definitions
     */
    static ErrorPipe definitionChecks(MJNode node) {
        return node.accept(new ErrorReportingVisitor(ErrorType.DEFINITION_MISSING) {

            @Override
            public ErrorPipe visit(VariableAccessNode variableAccess) {
                return process(variableAccess, node -> {
                    if (variableAccess.definition == null) {
                        error("%s has no associated definition", node);
                    }
                    if (variableAccess.definingExpression == null) {
                        error("%s has no defining expression", node);
                    }
                });
            }

            @Override
            public ErrorPipe visit(ParameterAccessNode variableAccess) {
                return process(variableAccess, () -> {
                    if (variableAccess.definition == null) {
                        error("%s has no associated definition", node);
                    }
                });
            }


            @Override
            public ErrorPipe visit(Parser.ParameterNode parameter) {
                return errors;
            }

            @Override
            public ErrorPipe visit(VariableDeclarationNode variableDeclaration) {
                return process(variableDeclaration, () -> {
                    visitChildrenDiscardReturn(variableDeclaration);
                    if (variableDeclaration.definition == null) {
                        error("%s has no associated definition", variableDeclaration);
                    }
                });
            }

            @Override
            public ErrorPipe visit(PhiNode phi) {
                return process(phi, () -> phi.joinedVariables.forEach(this::visit));
            }
        });
    }

    static ErrorPipe checkForReferencesToInvalidExpressionsAndVariables(MJNode node){
        final Set<ExpressionNode> validExpressions = new HashSet<>();
        final Set<Variable> validVariables = new HashSet<>();
        // collect
        node.accept(new NodeVisitor<Object>(){

            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(ExpressionNode expression) {
                visitChildrenDiscardReturn(expression);
                validExpressions.add(expression);
                return null;
            }

            @Override
            public Object visit(VariableAssignmentNode variableAssignment) {
                visitChildrenDiscardReturn(variableAssignment);
                validVariables.add(variableAssignment.definition);
                validExpressions.add(variableAssignment.expression);
                return null;
            }

            @Override
            public Object visit(PhiNode phi) {
                validExpressions.add(phi);
                return null;
            }

            @Override
            public Object visit(VariableAccessNode variableAccess) {
                validExpressions.add(variableAccess);
                return null;
            }
        });
        // check
        return node.accept(new ErrorReportingVisitor(ErrorType.UNEVALUATED) {

            void check(Variable variable){
                if (!validVariables.contains(variable)){
                    error("Not evaluated variable %s", variable);
                }
            }

            void check(ExpressionNode expression){
                if (!validExpressions.contains(expression)){
                    error("Not evaluated expression %s", expression);
                }
            }

            @Override
            public ErrorPipe visit(VariableAssignmentNode assignment) {
                return process(assignment, () -> {
                    visitChildrenDiscardReturn(assignment);
                    check(assignment.definition);
                    check(assignment.expression);
                });
            }

            @Override
            public ErrorPipe visit(ParameterAccessNode variableAccess) {
                return errors;
            }

            @Override
            public ErrorPipe visit(PhiNode phi) {
                return process(phi, () -> {
                    phi.controlDeps.forEach(this::check);
                    phi.joinedVariables.forEach(v -> {
                        check(v.definition);
                        check(v.definingExpression);
                    });
                    check(phi.controlDepStatement.conditionalExpression);
                });
            }

            @Override
            public ErrorPipe visit(VariableAccessNode variableAccess) {
                return null;
            }
        });
    }

    static ErrorPipe check(MJNode node){
        ErrorPipe pipe = definitionChecks(node);
        pipe.addAll(checkForReferencesToInvalidExpressionsAndVariables(node));
        return pipe;
    }

    static void checkAndThrow(MJNode node){
        ErrorPipe pipe = definitionChecks(node);
        pipe.addAll(checkForReferencesToInvalidExpressionsAndVariables(node));
        if (pipe.size() > 0) {
            throw new CheckError(pipe);
        }
    }
}

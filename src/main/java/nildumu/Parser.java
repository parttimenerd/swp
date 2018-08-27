package nildumu;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.Serializable;
import java.util.*;
import java.util.stream.*;

import swp.SWPException;
import swp.lexer.*;
import swp.parser.lr.*;
import swp.util.Utils;

import static nildumu.Lattices.*;
import static nildumu.Parser.LexerTerminal.*;

/**
 * Parser and AST for a basic Java based language that has only integer as a data type.
 * It is basically a while language with basic functions.
 * <p/>
 * The biggest difference to the normal while language is, that the syntax for integers is different:<br/>
 * <ul>
 *     <li>normal literal = a normal fully public integer</li>
 *     <li>normal literal + (l|h) = a fully secret or public integer (that might be pointless, as the attacker
 *         knows the source code…)</li>
 *     <li>(bit sec?)+ = a bit is either "u" (variable), "x" (don't care), "0" or "1" (constant), a security
 *         level (l or h) can be given per bit</li>
 * </ul>
 */
public class Parser implements Serializable {

    /**
     * The terminals with the matching regular expression
     */
    public enum LexerTerminal implements Generator.LexerTerminalEnum {
        EOF(""),
        COMMENT("/\\*([^*\\n]*(\\*[^/\\n])?)*\\*/"),
        WS("[\\s\\t]"),
        LBRK("[\\r\\n]"),
        INPUT("input"),
        OUTPUT("output"),
        INT("int"),
        RETURN("return"),
        IF("if"),
        WHILE("while"),
        ELSE("else"),
        TRUE("true"),
        FALSE("false"),
        VOID("void"),
        USE_SEC("use_sec"),
        TILDE("~"),

        LOWER_EQUALS("<="),
        GREATER_EQUALS(">="),
        MODULO("%"),
        LRBRACKET("\\[ ([\\r\\n\\t\\s]? (/\\*([^*]*(\\*[^/])?)*\\*/)?)* \\]"),

        PLUS("\\+", "+"),
        MINUS("\\-", "-"),
        DIVIDE("/"),
        MULTIPLY("\\*", "*"),
        EQUAL_SIGN("="),
        EQUALS("=="),
        UNEQUALS("!="),
        INVERT("!"),
        LOWER("<"),
        GREATER(">"),
        AND("&&"),
        OR("\\|\\|"),
        BAND("&"),
        BOR("\\|"),
        XOR("\\^"),
        LPAREN("\\("),
        RPAREN("\\)"),
        QUESTION_MARK("\\?"),
        SEMICOLON("(\\;|\\n)+"),
        INTEGER_LITERAL("(([1-9][0-9]*)|0)|(0b[01]+)|(\\-([1-9][0-9]*))"),
        INPUT_LITERAL("(0b[01u]+)"),
        IDENT("[A-Za-z_][A-Za-z0-9_]*"),
        LCURLY("\\{"),
        RCURLY("\\}"),
        COLON(":"),
        COMMA("[,]"),
        DOT("\\.");

        private String description;
        private String representation;

        LexerTerminal(String description){
            this(description, description);
        }

        LexerTerminal(String description, String representation){
            this.description = description;
            this.representation = representation;
        }

        @Override
        public String getTerminalDescription() {
            return description;
        }

        private static LexerTerminal[] terminals = values();

        static LexerTerminal valueOf(int id){
            return terminals[id];
        }

        public String getRepresentation() {
            return representation;
        }
    }

    /**
     * The parser generator that loads a version of the parser and lexer from disc if possible.
     * Change the id, when changing the parser oder replace the id by {@code null} to build the parser and lexer
     * every time (takes long)
     */
    public static Generator generator = Generator.getCachedIfPossible("stuff/blae54r6r55344474i446u7s5f2", LexerTerminal.class, new String[]{"WS", "COMMENT", "LBRK"},
            (builder) -> {
                builder.addRule("program", "use_sec? lines", asts -> {
                            MJNode.resetIdCounter();
                            SecurityLattice<?> secLattice = asts.get(0).children().isEmpty() ? BasicSecLattice.get() : ((ListAST<WrapperNode<SecurityLattice<?>>>)asts.get(0)).get(0).wrapped;
                            ProgramNode node = new ProgramNode(new Context(secLattice));
                            NodeVisitor visitor = new NodeVisitor<Object>(){

                                @Override
                                public Object visit(MJNode node) {
                                    System.out.println("ignore " + node);
                                    return null;
                                }

                                @Override
                                public Object visit(MethodNode method) {
                                    node.addMethod(method);
                                    return null;
                                }

                                @Override
                                public Object visit(StatementNode statement) {
                                    node.addGlobalStatement(statement);
                                    return null;
                                }

                                @Override
                                public Object visit(InputVariableDeclarationNode inputDecl) {
                                    node.context.addInputValue(secLattice.parse(inputDecl.secLevel), ((IntegerLiteralNode)inputDecl.expression).value);
                                    visit((StatementNode)inputDecl);
                                    return null;
                                }
                            };
                            asts.get(1).<WrapperNode<List<MJNode>>>as().wrapped.forEach(n -> n.accept(visitor));
                            return node;
                        })
                        .addRule("use_sec", "USE_SEC IDENT SEMICOLON", asts -> {
                            return new WrapperNode<>(asts.getStartLocation(), SecurityLattice.forName(asts.get(1).getMatchedString()));
                        })
                        .addRule("lines", "line_w_semi lines", asts -> {
                            WrapperNode<List<MJNode>> left = (WrapperNode<List<MJNode>>) asts.get(1);
                            MJNode right = (MJNode)asts.getAs(0);
                            left.wrapped.add(0, right);
                            return left;
                        })
                        .addRule("lines", "line", asts -> {
                            return new WrapperNode<>(((MJNode)asts.get(0)).location, new ArrayList<>(Collections.singleton(asts.get(0))));
                        })
                        .addRule("lines", "", asts -> {
                            return new WrapperNode<>(new Location(0, 0), new ArrayList<>());
                        })
                        .addRule("line_w_semi", "method")
                        .addRule("line_w_semi", "block_statement_w_semi")
                        .addRule("line_w_semi", "output_decl_statement SEMICOLON", asts -> asts.get(0))
                        .addRule("line_w_semi", "input_decl_statement SEMICOLON", asts -> asts.get(0))
                        .addRule("line", "method")
                        .addRule("line", "block_statement")
                        .addRule("line", "output_decl_statement")
                        .addRule("line", "input_decl_statement")
                        .addRule("output_decl_statement", "IDENT OUTPUT INT IDENT EQUAL_SIGN expression", asts -> {
                            return new OutputVariableDeclarationNode(
                                    asts.get(0).getMatchedTokens().get(0).location,
                                    asts.get(3).getMatchedString(),
                                    (ExpressionNode)asts.get(5),
                                    asts.get(0).getMatchedString());
                        })
                        .addRule("input_decl_statement", "IDENT INPUT INT IDENT EQUAL_SIGN input_literal", asts -> {
                            return new InputVariableDeclarationNode(
                                    asts.getStartLocation(),
                                    asts.get(3).getMatchedString(),
                                    (IntegerLiteralNode)asts.get(5),
                                    asts.get(0).getMatchedString());
                        })
                        .addRule("method", "INT IDENT LPAREN parameters RPAREN method_body", asts -> {
                            return new MethodNode(asts.get(0).getMatchedTokens().get(0).location,
                                    asts.get(1).getMatchedString(),
                                    (ParametersNode)asts.get(3), (BlockNode)asts.get(5));
                        })
                        .addRule("parameters", "", asts -> new ParametersNode(new Location(0, 0), new ArrayList<>()))
                        .addRule("parameters", "parameter COMMA parameters", asts -> {
                            ParameterNode param = (ParameterNode)asts.get(0);
                            ParametersNode node = new ParametersNode(param.location, Utils.makeArrayList(param));
                            node.parameterNodes.addAll(((ParametersNode)asts.get(2)).parameterNodes);
                            return node;
                        })
                        .addRule("parameters", "parameter", asts -> {
                            ParameterNode param = (ParameterNode)asts.get(0);
                            return new ParametersNode(param.location, Utils.makeArrayList(param));
                        })
                        .addRule("parameter", "INT IDENT", asts -> {
                            return new ParameterNode(asts.getStartLocation(), asts.get(1).getMatchedString());
                        })
                        .addEitherRule("statement", "block")
                        .addRule("expression_statement", "expression", asts -> {
                            return new ExpressionStatementNode((ExpressionNode)asts.get(0));
                        })
                        .addRule("block", "LCURLY block_statements RCURLY", asts -> new BlockNode(asts.get(0).getMatchedTokens().get(0).location, asts.get(1).<WrapperNode<List<StatementNode>>>as().wrapped))
                        .addRule("method_body", "LCURLY method_block_statements RCURLY", asts -> new BlockNode(asts.get(0).getMatchedTokens().get(0).location, asts.get(1).<WrapperNode<List<StatementNode>>>as().wrapped))
                        .addRule("block_statements", "block_statement_w_semi block_statements", asts -> {
                            WrapperNode<List<StatementNode>> left = (WrapperNode<List<StatementNode>>) asts.get(1);
                            StatementNode right = (StatementNode)asts.getAs(0);
                            left.wrapped.add(0, right);
                            return left;
                        })
                        .addRule("block_statements", "block_statement", asts -> {
                            return new WrapperNode<>(((MJNode)asts.get(0)).location, new ArrayList<>(Collections.singleton(asts.get(0))));
                        })
                        .addRule("block_statements", "", asts -> {
                            return new WrapperNode<>(new Location(0, 0), new ArrayList<>());
                        })
                        .addRule("method_block_statements", "block_statement_w_semi method_block_statements", asts -> {
                            WrapperNode<List<StatementNode>> left = (WrapperNode<List<StatementNode>>) asts.get(1);
                            StatementNode right = (StatementNode)asts.getAs(0);
                            left.wrapped.add(0, right);
                            return left;
                        })
                        .addRule("method_block_statements", "block_statement", asts -> {
                            return new WrapperNode<>(((MJNode)asts.get(0)).location, new ArrayList<>(Collections.singleton(asts.get(0))));
                        })
                        .addRule("method_block_statements", "return_statement SEMICOLON?", asts -> {
                            return new WrapperNode<>(((MJNode)asts.get(0)).location, new ArrayList<>(Collections.singleton(asts.get(0))));
                        })
                        .addRule("method_block_statements", "", asts -> {
                            return new WrapperNode<>(new Location(0, 0), new ArrayList<>());
                        })
                        .addRule("block_statement_w_semi", "statement SEMICOLON", asts -> asts.get(0))
                        .addRule("block_statement_w_semi", "var_decl SEMICOLON", asts -> asts.get(0))
                        .addRule("block_statement_w_semi", "local_variable_assignment_statement SEMICOLON", asts -> asts.get(0))
                        .addRule("block_statement_w_semi", "while_statement")
                        .addRule("block_statement_w_semi", " if_statement")
                        .addRule("block_statement_w_semi", "expression_statement SEMICOLON", asts -> asts.get(0))
                        .addRule("block_statement", "statement", asts -> asts.get(0))
                        .addRule("block_statement", "var_decl", asts -> asts.get(0))
                        .addRule("block_statement", "local_variable_assignment_statement", asts -> asts.get(0))
                        .addRule("block_statement", "while_statement")
                        .addRule("block_statement", "if_statement")
                        .addRule("block_statement", "expression_statement")
                        .addRule("var_decl", "INT IDENT", asts -> {
                            return new VariableDeclarationNode(
                                    asts.getStartLocation(),
                                    asts.get(1).getMatchedString());
                        })
                        .addRule("var_decl", "INT IDENT EQUAL_SIGN expression", asts -> {
                            return new VariableDeclarationNode(
                                    asts.getStartLocation(),
                                    asts.get(1).getMatchedString(),
                                    (ExpressionNode)asts.get(3));
                        })
                        .addRule("local_variable_assignment_statement", "IDENT EQUAL_SIGN expression", asts -> {
                            return new VariableAssignmentNode(
                                    asts.getStartLocation(),
                                    asts.get(0).getMatchedString(),
                                    (ExpressionNode)asts.get(2));
                        })
                        .addRule("while_statement", "WHILE LPAREN expression RPAREN block_statement", asts -> {
                            return new WhileStatementNode(
                                    asts.getStartLocation(),
                                    (ExpressionNode)asts.get(2),
                                    (StatementNode)asts.get(4));
                        })
                        .addRule("if_statement", "IF LPAREN expression RPAREN statement", asts -> {
                            return new IfStatementNode(
                                    asts.getStartLocation(),
                                    (ExpressionNode)asts.get(2),
                                    (StatementNode)asts.get(4));
                        })
                        .addRule("if_statement", "IF LPAREN expression RPAREN statement ELSE statement", asts -> {
                            return new IfStatementNode(asts.getStartLocation(), (ExpressionNode)asts.get(2),
                                    (StatementNode)asts.get(4), (StatementNode)asts.get(6));
                        })
                        .addRule("return_statement", "RETURN", asts -> {
                            return new ReturnStatementNode(asts.getStartLocation());
                        })
                        .addRule("return_statement", "RETURN expression", asts -> {
                            return new ReturnStatementNode((ExpressionNode)asts.get(1));
                        })
                        .addOperators("expression", "postfix_expression", operators -> {
                            operators.defaultBinaryAction((asts, op) -> {
                                        return new BinaryOperatorNode((ExpressionNode)asts.get(0), (ExpressionNode)asts.get(2), LexerTerminal.valueOf(op));
                                    })
                                    .defaultUnaryAction((asts, op) -> {
                                        return new UnaryOperatorNode((ExpressionNode)asts.get(1), LexerTerminal.valueOf(op));
                                    })
                                    .closeLayer()
                                    .binaryLayer(OR)
                                    .binaryLayer(AND)
                                    .binaryLayer(BOR)
                                    .binaryLayer(BAND)
                                    .binaryLayer(XOR)
                                    .binaryLayer(EQUALS, UNEQUALS)
                                    .binaryLayer(LOWER, LOWER_EQUALS, GREATER, GREATER_EQUALS)
                                    .binaryLayer(PLUS, MINUS)
                                    .binaryLayer(MULTIPLY, DIVIDE, MODULO)
                                    .unaryLayerLeft(INVERT, MINUS);
                        })
                        .addRule("postfix_expression", "primary_expression")
                        .addEitherRule("postfix_expression", "method_invocation")
                        .addRule("method_invocation", "IDENT LPAREN arguments RPAREN", asts -> {
                            return new MethodInvocationNode(asts.getStartLocation(), asts.get(0).getMatchedString(),
                                    (ArgumentsNode)asts.get(2));
                        })
                        .addRule("method_invocation", "IDENT LPAREN expression RPAREN", asts -> {
                            ExpressionNode arg = (ExpressionNode)asts.get(2);
                            return new MethodInvocationNode(asts.getStartLocation(), asts.get(0).getMatchedString(),
                                    new ArgumentsNode(arg.location, Utils.makeArrayList(arg)));
                        })
                        .addRule("arguments", "", asts -> new ArgumentsNode(new Location(0, 0), new ArrayList<>()))
                        .addRule("arguments", "expression", asts -> {
                            return new ArgumentsNode(((ExpressionNode)asts.get(0)).location, Utils.makeArrayList((ExpressionNode)asts.get(0)));
                        })
                        .addRule("arguments", "expression COMMA arguments", asts -> {
                            List<ExpressionNode> args = Utils.makeArrayList((ExpressionNode)asts.get(0));
                            ArgumentsNode argsNode = ((ArgumentsNode)asts.get(2));
                            args.addAll(argsNode.arguments);
                            return new ArgumentsNode(argsNode.location, args);
                        })
                        .addRule("primary_expression", "FALSE", asts -> new IntegerLiteralNode(asts.getStartLocation(), ValueLattice.get().parse(0)))
                        .addRule("primary_expression", "TRUE", asts -> new IntegerLiteralNode(asts.getStartLocation(), ValueLattice.get().parse(1)))
                        .addRule("primary_expression", "INTEGER_LITERAL", asts -> {
                            return new IntegerLiteralNode(asts.getStartLocation(), ValueLattice.get().parse(asts.getMatchedString()));
                        })
                        .addRule("primary_expression", "IDENT", asts -> new VariableAccessNode(asts.getStartLocation(), asts.getMatchedString()))
                        .addRule("primary_expression", "LPAREN expression RPAREN", asts -> {
                            return asts.get(1);
                        })
                        .addRule("input_literal", "INPUT_LITERAL|INTEGER_LITERAL", asts -> {
                            /*List<Bit> rev = asts.get(0).<ListAST<?>>as().stream().map(s -> new Bit(B.U.parse(s.getMatchedString().substring(1)))).collect(Collectors.toList());
                            Collections.reverse(rev);
                            return new IntegerLiteralNode(new Value(rev));*/
                            return new IntegerLiteralNode(asts.getStartLocation(), ValueLattice.get().parse(asts.getMatchedString()));
                        });
            }, "program");

    /**
     * Start a simple repl
     */
    public static void main(String[] args) {
        /*for (LexerTerminal terminal : LexerTerminal.values()) {
            System.out.println(terminal.name() + "#" + terminal.representation + "#" + terminal.description);
        }*/
        Generator generator = Parser.generator;
        Utils.repl(s -> generator.createLexer(s));
    }

    public static class LexerAndASTRepl {
        public static void main(String[] args){
            Utils.parserRepl(s -> {
                Lexer lexer = Parser.generator.createLexer(s);
                try {
                    do {
                        System.out.print(lexer.next().toSimpleString() + " ");
                    } while (lexer.cur().type != 0);
                } catch (SWPException ex){
                    System.out.print("Caught error: " + ex.getMessage());
                }
                System.out.println(Parser.generator.parse(s).toPrettyString());
                return null;
            });
        }
    }
    /**
     * Process the passed input.
     * Currently does a name resolution and converts the result into SSA form
     */
    public static ProgramNode process(String input){
        ProgramNode program = (ProgramNode) generator.parse(input);
        new NameResolution(program).resolve();
        new SSAResolution(program).resolve();
        return program;
    }

    /**
     * Parse and do a SSA resolution.
     */
    public static ProgramNode parse(String input){
        ProgramNode program = (ProgramNode) generator.parse(input);
        new NameResolution(program).resolve();
        new SSAResolution(program).resolve();
        return program;
    }

    /**
     * Visitor that delegates each not implemented visit method to the visit method for the parent class.
     */
    public interface NodeVisitor<R> extends StatementVisitor<R>, ExpressionVisitor<R> {

        R visit(MJNode node);

        default R visit(ProgramNode program){
            return visit((MJNode)program);
        }

        default R visit(MethodNode method){
            return visit((MJNode)method);
        }

        default <T> R visit(WrapperNode<T> wrapper){
            return visit((MJNode)wrapper);
        }

        default R visit(MethodPartNode methodPart){
            return visit((MJNode)methodPart);
        }

        default R visit(BlockPartNode blockPart){
            return visit((MethodPartNode)blockPart);
        }

        default R visit(StatementNode statement){
            return visit((BlockPartNode)statement);
        }

        default R visit(ArgumentsNode arguments){
            return visit((BlockPartNode)arguments);
        }

        default R visit(ExpressionNode expression){
            return visit((BlockPartNode) expression);
        }

        default R visit(MethodInvocationNode methodInvocation){
            return visit((ExpressionNode) methodInvocation);
        }

        default R visit(VariableAssignmentNode assignment){
            return visit((StatementNode)assignment);
        }

        default R visit(VariableDeclarationNode variableDeclaration){
            return visit((VariableAssignmentNode) variableDeclaration);
        }

        default R visit(OutputVariableDeclarationNode outputDecl){
            return visit((VariableDeclarationNode) outputDecl);
        }

        default R visit(InputVariableDeclarationNode inputDecl){
            return visit((VariableDeclarationNode) inputDecl);
        }

        default R visit(BlockNode block){
            return visit((StatementNode)block);
        }

        default R visit(ParametersNode parameters){
            return visit((MethodPartNode)parameters);
        }

        default R visit(ParameterNode parameter){
            return visit((MethodPartNode)parameter);
        }

        default R visit(PhiNode phi){
            return visit((ExpressionNode)phi);
        }

        default R visit(IfStatementNode ifStatement){
            return visit((StatementNode)ifStatement);
        }

        default R visit(IfStatementEndNode ifEndStatement){
            return visit((StatementNode)ifEndStatement);
        }

        default R visit(WhileStatementNode whileStatement){
            return visit((StatementNode)whileStatement);
        }

        default R visit(WhileStatementEndNode whileEndStatement){
            return visit((StatementNode)whileEndStatement);
        }

        default R visit(VariableAccessNode variableAccess){
            return visit((PrimaryExpressionNode)variableAccess);
        }

        default R visit(BinaryOperatorNode binaryOperator){
            return visit((ExpressionNode)binaryOperator);
        }

        default R visit(UnaryOperatorNode unaryOperator){
            return visit((ExpressionNode)unaryOperator);
        }

        default R visit(PrimaryExpressionNode primaryExpression){
            return visit((ExpressionNode)primaryExpression);
        }

        default R visit(ExpressionStatementNode expressionStatement){
            return visit((StatementNode)expressionStatement);
        }

        default R visit(ReturnStatementNode returnStatement){
            return visit((ExpressionStatementNode)returnStatement);
        }

        /**
         * Visit all direct children with the visitor and return the results
         */
        default List<R> visitChildren(MJNode node){
            return node.children().stream().map(c -> ((MJNode)c).accept(this)).collect(Collectors.toList());
        }

        /**
         * Visit all direct children with the visitor and discard the results
         */
        default void visitChildrenDiscardReturn(MJNode node) {
            node.children().stream().forEach(c -> ((MJNode) c).accept(this));
        }
    }

    public interface StatementVisitor<R> {

        R visit(StatementNode statement);

        default R visit(VariableAssignmentNode assignment){
            return visit((StatementNode)assignment);
        }

        default R visit(VariableDeclarationNode variableDeclaration){
            return visit((VariableAssignmentNode) variableDeclaration);
        }

        default R visit(OutputVariableDeclarationNode outputDecl){
            return visit((VariableDeclarationNode) outputDecl);
        }

        default R visit(InputVariableDeclarationNode inputDecl){
            return visit((VariableDeclarationNode) inputDecl);
        }

        default R visit(BlockNode block){
            return visit((StatementNode)block);
        }

        default R visit(IfStatementNode ifStatement){
            return visit((StatementNode)ifStatement);
        }

        default R visit(IfStatementEndNode ifEndStatement){
            return visit((StatementNode)ifEndStatement);
        }

        default R visit(WhileStatementNode whileStatement){
            return visit((StatementNode)whileStatement);
        }

        default R visit(WhileStatementEndNode whileEndStatement){
            return visit((StatementNode)whileEndStatement);
        }

        default R visit(ExpressionStatementNode expressionStatement){
            return visit((StatementNode)expressionStatement);
        }

        default R visit(ReturnStatementNode returnStatement){
            return visit((ExpressionStatementNode)returnStatement);
        }


        /**
         * Visit all direct children statements with the visitor and return the results
         */
        default List<R> visitChildren(MJNode node){
            return node.children().stream().filter(c -> c instanceof StatementNode).map(c -> ((StatementNode)c).accept(this)).collect(Collectors.toList());
        }

        /**
         * Visit all direct children statements with the visitor and discard the results
         */
        default void visitChildrenDiscardReturn(MJNode node){
            node.children().stream().filter(c -> c instanceof StatementNode).forEach(c -> ((StatementNode)c).accept(this));
        }
    }

    public interface ExpressionVisitor<R> {


        R visit(ExpressionNode expression);

        default R visit(PhiNode phi){
            return visit((ExpressionNode)phi);
        }


        default R visit(VariableAccessNode variableAccess){
            return visit((PrimaryExpressionNode)variableAccess);
        }

        default R visit(IntegerLiteralNode literal){
            return visit((PrimaryExpressionNode)literal);
        }

        default R visit(BinaryOperatorNode binaryOperator){
            return visit((ExpressionNode)binaryOperator);
        }

        default R visit(UnaryOperatorNode unaryOperator){
            return visit((ExpressionNode)unaryOperator);
        }

        default R visit(PrimaryExpressionNode primaryExpression){
            return visit((ExpressionNode)primaryExpression);
        }

        /**
         * Visit all direct children statements with the visitor and return the results
         */
        default List<R> visitChildren(MJNode node){
            return node.children().stream().filter(c -> c instanceof ExpressionNode).map(c -> ((ExpressionNode)c).accept(this)).collect(Collectors.toList());
        }

        /**
         * Visit all direct children statements with the visitor and discard the results
         */
        default void visitChildrenDiscardReturn(MJNode node){
            node.children().stream().filter(c -> c instanceof ExpressionNode).forEach(c -> ((ExpressionNode)c).accept(this));
        }
    }

    /**
     * Expression visitor that gets passed an argument that is usually related to the children of the node.
     * <p/>
     * Used for evaluation (see {@link FixpointIteration#walkExpression(ExpressionVisitorWArgs, ExpressionNode)}
     *
     * @param <R> type of the result of each visit
     * @param <A> type of the argument for the visit methods
     */
    public interface ExpressionVisitorWArgs<R, A> {


        R visit(ExpressionNode expression, A argument);

        default R visit(PhiNode phi, A argument){
            return visit((ExpressionNode)phi, argument);
        }


        default R visit(VariableAccessNode variableAccess, A argument){
            return visit((PrimaryExpressionNode)variableAccess, argument);
        }

        default R visit(IntegerLiteralNode literal, A argument){
            return visit((PrimaryExpressionNode)literal, argument);
        }

        default R visit(BinaryOperatorNode binaryOperator, A argument){
            return visit((ExpressionNode)binaryOperator, argument);
        }

        default R visit(UnaryOperatorNode unaryOperator, A argument){
            return visit((ExpressionNode)unaryOperator, argument);
        }

        default R visit(PrimaryExpressionNode primaryExpression, A argument){
            return visit((ExpressionNode)primaryExpression, argument);
        }
    }

    /**
     * A basic AST Node for the language
     */
    public static abstract class MJNode extends BaseAST {

        private static int idCounter = 0;

        private final int id;

        public final Location location;

        protected MJNode(Location location) {
            this.location = location;
            this.id = idCounter++;
        }


        @Override
        public List<Token> getMatchedTokens() {
            return null;
        }

        public abstract <R> R accept(NodeVisitor<R> visitor);

        @Override
        public List<BaseAST> children() {
            return new ArrayList<>();
        }

        public String getTextualId(){
            return shortType() + location.toString();
        }

        public static void resetIdCounter(){
            idCounter = 0;
        }

        public Operator getOperator(Context c){
            return getOperator();
        }

        Operator getOperator(){
            throw new NotImplementedException();
        }

        public abstract String shortType();

        @Override
        public String type() {
            return shortType();
        }
    }

    /**
     * A node wrapping literal values.
     */
    public static class WrapperNode<T> extends MJNode {
        public final T wrapped;

        protected WrapperNode(Location location, T wrapped) {
            super(location);
            this.wrapped = wrapped;
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String shortType() {
            return "s";
        }
    }

    /**
     * A program with some methods and a global block
     */
    public static class ProgramNode extends MJNode {

        public final Context context;

        private final Map<Variable, Object> inputVariables = new IdentityHashMap<>();
        private final Map<Variable, Object> outputVariables = new IdentityHashMap<>();
        private final Map<String, MethodNode> methods = new HashMap<>();

        public final BlockNode globalBlock;

        public ProgramNode(Context context) {
            super(new Location(1, 1));
            this.context = context;
            globalBlock = new BlockNode(location, new ArrayList<>());
        }

        public void addMethod(MethodNode methodNode){
            methods.put(methodNode.name, methodNode);
        }

        public List<String> getMethodNames(){
            return new ArrayList<>(methods.keySet());
        }

        public MethodNode getMethod(String name){
            return methods.get(name);
        }

        public boolean hasMethod(String name){
            return methods.containsKey(name);
        }

        public Set<Variable> getInputVariables(){
            return inputVariables.keySet();
        }

        public void addInputVariable(Variable variable){
            assert variable.isInput;
            inputVariables.put(variable, null);
        }

        public Set<Variable> getOutputVariables(){
            return outputVariables.keySet();
        }

        public void addOuputVariable(Variable variable){
            assert variable.isOutput;
            outputVariables.put(variable, null);
        }

        public void addGlobalStatement(StatementNode statement){
            globalBlock.statementNodes.add(statement);
        }

        @Override
        public String toString() {
            return globalBlock.toString() + "\ninput variables: " + getInputVariables().stream().map(v -> v.name).collect(Collectors.joining(";")) +
                    "\noutput variables: " + getOutputVariables().stream().map(Variable::toString).collect(Collectors.joining(";"));// +
                 //   "methods:\n  " + methods.keySet().stream().sorted().map(methods::get).map(MethodNode::toString).collect(Collectors.joining("\n  "));
        }

        @Override
        public String type() {
            return "program"; }

        @Override
        public List<BaseAST> children() {
            return Stream.concat(methods.values().stream(), Stream.of(globalBlock)).collect(Collectors.toList());
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String shortType() {
            return "p";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return globalBlock.toPrettyString(indent, incr);
        }
    }

    /**
     * Node representing a simple method that gets passed some integers and returns an integer
     */
    public static class MethodNode extends MJNode {
        public final String name;
        public final ParametersNode parameters;
        public final BlockNode body;

        public MethodNode(Location location, String name, ParametersNode parameters, BlockNode body) {
            super(location);
            this.name = name;
            this.parameters = parameters;
            this.body = body;
        }

        @Override
        public String toString() {
            return String.format("int %s (%s): %s", name, parameters, body);
        }

        @Override
        public String type() {
            return "method";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(parameters, body);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String shortType() {
            return "m";
        }

        @Override
        public String getTextualId() {
            return "m:" + name;
        }

        public boolean hasReturnValue() {
            return !body.statementNodes.isEmpty() && body.getLastStatementOrNull() instanceof ReturnStatementNode && ((ReturnStatementNode) body.getLastStatementOrNull()).hasReturnExpression();
        }
    }

    /**
     * Node that can be part of a method
     */
    public static abstract class MethodPartNode extends MJNode {
        public MethodNode parentMethod;

        public MethodPartNode(Location location) {
            super(location);
        }

        public abstract void setParentMethod(MethodNode parentMethod);

        public boolean hasParentMethod(){
            return parentMethod != null;
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

    }

    /**
     * A list of parameters (their definitions)
     */
    public static class ParametersNode extends MethodPartNode {

        public final List<ParameterNode> parameterNodes;

        public ParametersNode(Location location, List<ParameterNode> parameterNodes) {
            super(location);
            this.parameterNodes = parameterNodes;
        }

        @Override
        public void setParentMethod(MethodNode parentMethod) {
            this.parentMethod = parentMethod;
            for (ParameterNode parameterNode : parameterNodes) {
                parameterNode.setParentMethod(parentMethod);
            }
        }

        @Override
        public String toString() {
            return Utils.toString(", ", parameterNodes);
        }

        @Override
        public String type() {
            return "parameters";
        }

        @Override
        public List<BaseAST> children() {
            return Arrays.asList(parameterNodes.toArray(new BaseAST[]{}));
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String shortType() {
            return "ps";
        }

        public int size(){
            return parameterNodes.size();
        }

        public ParameterNode get(int i){
            return parameterNodes.get(i);
        }
    }

    /**
     * A parameter defintion
     */
    public static class ParameterNode extends MethodPartNode {

        public Variable definition;
        public final String name;


        public ParameterNode(Location location, String name) {
            super(location);
            assert !name.isEmpty();
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("int %s", name);
        }

        @Override
        public void setParentMethod(MethodNode parentMethod) {
            this.parentMethod = parentMethod;
        }

        @Override
        public String type() {
            return "parameter";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            return new Operator.VariableAccess(definition);
        }

        @Override
        public String shortType() {
            return "p";
        }

        @Override
        public String getTextualId() {
            return "p:" + (definition != null ? definition.name : name);
        }
    }

    /**
     * A node that might be part of a block
     */
    public static abstract class BlockPartNode extends MethodPartNode {
        public BlockNode parentBlock = null;

        public BlockPartNode(Location location) {
            super(location);
        }

        public abstract BlockPartNode[] getBlockParts();

        public void setParentBlock(BlockNode parentBlock) {
            this.parentBlock = parentBlock;
            for (BlockPartNode blockPartNode : getBlockParts()) {
                if (blockPartNode != null) {
                    blockPartNode.setParentBlock(parentBlock);
                }
            }
        }

        @Override
        public void setParentMethod(MethodNode parentMethod) {
            this.parentMethod = parentMethod;
            for (BlockPartNode blockPartNode : getBlockParts()) {
                if (blockPartNode != null) {
                    blockPartNode.setParentMethod(parentMethod);
                }
            }
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    /**
     * A statement
     */
    public static abstract class StatementNode extends BlockPartNode {
        public StatementNode(Location location) {
            super(location);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * A block of {@link BlockPartNode}s
     */
    public static class BlockNode extends StatementNode {
        public final List<StatementNode> statementNodes;

        public BlockNode(Location location, List<StatementNode> statementNodes) {
            super(location);
            this.statementNodes = statementNodes;
            for (BlockPartNode statementNode : statementNodes) {
                statementNode.setParentBlock(this);
            }
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return statementNodes.toArray(new BlockPartNode[]{});
        }

        @Override
        public String toString() {
            return String.format("{\n%s\n}", statementNodes.stream().flatMap(s -> Arrays.stream(s.toString().split("\n")).map(str -> "  "  + str)).collect(Collectors.joining("\n")));
        }

        @Override
        public String type() {
            return "block";
        }

        @Override
        public List<BaseAST> children() {
            return Arrays.asList(statementNodes.toArray(new BaseAST[]{}));
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        public StatementNode getLastStatementOrNull(){
            if (statementNodes.isEmpty()){
                return null;
            }
            return statementNodes.get(statementNodes.size() - 1);
        }

        @Override
        public String shortType() {
            return "b";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return statementNodes.stream().map(s ->s.toPrettyString(indent, incr)).filter(s -> s.trim().length() != 0).collect(Collectors.joining("\n"));
        }
    }

    /**
     * A variable declaration, introduces a variable in a scope
     */
    public static class VariableDeclarationNode extends VariableAssignmentNode {

        public VariableDeclarationNode(Location location, String name, ExpressionNode initExpression) {
            super(location, name, initExpression);
        }

        public VariableDeclarationNode(Location location, String name) {
            this(location, name, null);
        }

        public VariableDeclarationNode(Location location, Variable variable, ExpressionNode initExpression) {
            this(location, variable.name, initExpression);
            definition = variable;
        }

        public boolean hasInitExpression(){
            return expression != null;
        }

        @Override
        public String toString() {
            if (hasInitExpression()){
                return String.format("int %s = %s;", variable, expression);
            } else {
                return String.format("int %s;", variable);
            }
        }

        @Override
        public String type() {
            return "var_decl";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public List<BaseAST> children() {
            if (hasInitExpression()){
                return Utils.makeArrayList(expression);
            }
            return Utils.makeArrayList();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            if (hasInitExpression()) {
                return new Operator.VariableAssignment(definition);
            }
            return new Operator.LiteralOperator(vl.bot());
        }

        @Override
        public String shortType() {
            return "d";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + toString();
        }
    }

    public static class OutputVariableDeclarationNode extends VariableDeclarationNode {
        final String secLevel;

        public OutputVariableDeclarationNode(Location location, String name, ExpressionNode initExpression, String secLevel) {
            super(location, name, initExpression);
            this.secLevel = secLevel;
        }

        @Override
        public String toString() {
            return String.format("%s output %s", secLevel, super.toString());
        }

        @Override
        public String type() {
            return "output_decl";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator(Context c) {
            return new Operator.OutputVariableAssignment(definition, c.sl.parse(secLevel));
        }

        @Override
        public String shortType() {
            return "do";
        }
    }

    public static class InputVariableDeclarationNode extends VariableDeclarationNode {
        public final String secLevel;

        public InputVariableDeclarationNode(Location location, String name, IntegerLiteralNode initExpression, String secLevel) {
            super(location, name, initExpression);
            this.secLevel = secLevel;
        }

        @Override
        public String type() {
            return "input_decl";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "di";
        }

        @Override
        public String toString() {
            return String.format("%s input %s", secLevel, super.toString());
        }
    }


    /**
     * A variable assignment
     */
    public static class VariableAssignmentNode extends StatementNode {
        Variable definition;
        public final String variable;
        public final ExpressionNode expression;

        public VariableAssignmentNode(Location location, String variable, ExpressionNode expression) {
            super(location);
            this.variable = variable;
            this.expression = expression;
        }

        @Override
        public String type() {
            return "local_variable_assignment_statement";
        }

        @Override
        public String toString() {
            return String.format("%s = %s;", definition == null ? variable : definition.name, expression);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public List<BaseAST> children() {
            if (expression != null){
                return Arrays.asList(expression);
            }
            return Collections.emptyList();
        }

        @Override
        public Operator getOperator() {
            return new Operator.VariableAssignment(definition);
        }

        @Override
        public String getTextualId() {
            return shortType() + ":" + (definition != null ? definition.name : variable) + location.toString();
        }

        @Override
        public String shortType() {
            return "a";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + toString();
        }
    }

    /**
     * An empty statement that has no effect
     */
    public static class EmptyStatementNode extends StatementNode {

        public EmptyStatementNode(Location location) {
            super(location);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{};
        }

        @Override
        public String toString() {
            return "[empty statement]";
        }

        @Override
        public String type() {
            return "empty_statement";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "e";
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent;
        }
    }

    /**
     * A while statement
     */
    public static class WhileStatementNode extends StatementNode {
        public ExpressionNode conditionalExpression;
        public final BlockNode body;

        public WhileStatementNode(Location location, ExpressionNode conditionalExpression, StatementNode body) {
            super(location);
            this.conditionalExpression = conditionalExpression;
            this.body = appendWhileEnd(body instanceof BlockNode ? (BlockNode)body : new BlockNode(body.location, new ArrayList<>(Arrays.asList(body))));
        }

        private BlockNode appendWhileEnd(BlockNode blockNode){
            if (!(blockNode.getLastStatementOrNull() instanceof WhileStatementEndNode)){
                List<StatementNode> tmp = new ArrayList<>(blockNode.statementNodes);
                tmp.add(new WhileStatementEndNode(this, location));
                return new BlockNode(blockNode.location, tmp);
            }
            return blockNode;
        }

        @Override
        public String toString() {
            return String.format("while (%s) %s", conditionalExpression, body);
        }

        @Override
        public String type() {
            return "while";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{body};
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(conditionalExpression, body);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "w";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return String.format("%swhile (%s) {\n%s\n%s}", indent, conditionalExpression, body.toPrettyString(indent + incr, incr), indent);
        }
    }

    /**
     * An if statement with two branches
     */
    public static class IfStatementNode extends StatementNode {
        public final ExpressionNode conditionalExpression;
        public final BlockNode ifBlock;
        public final BlockNode elseBlock;

        public IfStatementNode(Location location, ExpressionNode conditionalExpression, StatementNode ifBlock, StatementNode elseBlock) {
            super(location);
            this.conditionalExpression = conditionalExpression;
            this.ifBlock = appendIfEnd(ifBlock instanceof BlockNode ? (BlockNode)ifBlock : new BlockNode(ifBlock.location, new ArrayList<>(Arrays.asList(ifBlock))));
            this.elseBlock = appendIfEnd(elseBlock instanceof BlockNode ? (BlockNode)elseBlock : new BlockNode(elseBlock.location, new ArrayList<>(Arrays.asList(elseBlock))));
        }

        public IfStatementNode(Location location, ExpressionNode conditionalExpression, StatementNode ifBlock) {
            this(location, conditionalExpression, ifBlock, new BlockNode(location, new ArrayList<>()));
        }

        private BlockNode appendIfEnd(BlockNode blockNode){
            if (!(blockNode.getLastStatementOrNull() instanceof IfStatementEndNode)){
                List<StatementNode> tmp = new ArrayList<>(blockNode.statementNodes);
                tmp.add(new IfStatementEndNode(location));
                return new BlockNode(blockNode.location, tmp);
            }
            return blockNode;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{conditionalExpression, ifBlock, elseBlock};
        }

        public boolean hasElseBlock(){
            return elseBlock != null;
        }

        @Override
        public String type() {
            return "if";
        }

        @Override
        public String toString() {
            if (hasElseBlock()){
                return String.format("if (%s) %s \n else %s", conditionalExpression, ifBlock, elseBlock);
            } else {
                return String.format("if (%s) %s", conditionalExpression, ifBlock);
            }
        }

        @Override
        public List<BaseAST> children() {
            if (hasElseBlock()) {
                return Utils.makeArrayList(conditionalExpression, ifBlock, elseBlock);
            }
            return Utils.makeArrayList(conditionalExpression, ifBlock);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "i";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            String thenStr = ifBlock.toPrettyString(indent + incr, incr);
            if (hasElseBlock()) {
                return String.format("%sif (%s) {\n%s\n%s} else {\n%s\n%s}", indent, conditionalExpression, thenStr, indent, elseBlock.toPrettyString(indent + incr, incr), indent);
            } else {
                return String.format("%sif (%s) {\n%s\n%s}", indent, conditionalExpression, thenStr, indent);
            }
        }
    }

    /**
     * Indicates the end of an {@link IfStatementNode}
     */
    public static class IfStatementEndNode extends StatementNode {

        public IfStatementEndNode(Location location) {
            super(location);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[0];
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "ie";
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String type() {
            return "ifEnd";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent;
        }
    }

    /**
     * Indicates the end of an {@link WhileStatementNode}
     */
    public static class WhileStatementEndNode extends StatementNode {

        public final WhileStatementNode whileStatement;

        public WhileStatementEndNode(WhileStatementNode whileStatement, Location location) {
            super(location);
            this.whileStatement = whileStatement;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[0];
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String type() {
            return "whileEnd";
        }

        @Override
        public String shortType() {
            return "we";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent;
        }
    }

    /**
     * An expression statement that essentially consists of an expression
     */
    public static class ExpressionStatementNode extends StatementNode {
        public final ExpressionNode expression;

        public ExpressionStatementNode(ExpressionNode expression) {
            super(expression.location);
            this.expression = expression;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public String toString() {
            return expression.toString() + ";";
        }

        @Override
        public String type() {
            return "expression_statement";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(expression);
        }

        @Override
        public String shortType() {
            return ";" + expression.shortType();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + toString();
        }
    }

    /**
     * A return statement. An empty return statement return {@code 0l}
     */
    public static class ReturnStatementNode extends ExpressionStatementNode {

        public ReturnStatementNode(Location location){
            super(new IntegerLiteralNode(location, ValueLattice.get().bot()));
        }

        public ReturnStatementNode(ExpressionNode expression) {
            super(expression);
        }

        @Override
        public String toString() {
            if (hasReturnExpression()) {
                return String.format("return %s;", expression);
            } else {
                return "return;";
            }
        }

        @Override
        public String type() {
            return "return_statement";
        }

        public boolean hasReturnExpression(){
            return expression != null;
        }

        @Override
        public List<BaseAST> children() {
            if (hasReturnExpression()){
                return Utils.makeArrayList(expression);
            }
            return new ArrayList<>();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "r";
        }
    }

    /**
     * Base node for all expressions
     */
    public static abstract class ExpressionNode extends BlockPartNode {

        public ExpressionNode(Location location) {
            super(location);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        public <R,A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument){
            return visitor.visit(this, argument);
        }

        @Override
        public abstract Operator getOperator();
    }

    /**
     * A binary operator expression
     */
    public static class BinaryOperatorNode extends ExpressionNode {
        public final ExpressionNode left;
        public final ExpressionNode right;
        public final LexerTerminal operator;
        public final Operator op;

        public BinaryOperatorNode(ExpressionNode left, ExpressionNode right, LexerTerminal operator) {
            super(left.location);
            this.left = left;
            this.right = right;
            this.operator = operator;
            op = getOperator(operator);
        }

        static Operator getOperator(LexerTerminal operator){
            switch (operator) {
                case BAND:
                    return Operator.AND;
                case BOR:
                    return Operator.OR;
                case XOR:
                    return Operator.XOR;
                case PLUS:
                    return Operator.ADD;
                case MULTIPLY:
                    return Operator.MULTIPLY;
                case EQUALS:
                    return Operator.EQUALS;
                case UNEQUALS:
                    return Operator.UNEQUALS;
                case LOWER:
                    return Operator.LESS;
                default:
                    return null;
            }
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{left, right};
        }

        @Override
        public String toString() {
            return String.format("(%s %s %s)", left, operator.description.replace("\\", ""), right);
        }

        @Override
        public String type() {
            return "binary_operator";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(left, right);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R,A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument){
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            return op;
        }

        @Override
        public String shortType() {
            return operator.representation;
        }
    }

    /**
     * An unary operator expression
     */
    public static class UnaryOperatorNode extends ExpressionNode {
        public final ExpressionNode expression;
        public final LexerTerminal operator;
        public final Operator op;

        public UnaryOperatorNode(ExpressionNode expression, LexerTerminal operator) {
            super(expression.location);
            this.expression = expression;
            this.operator = operator;
            switch (operator){
                case INVERT:
                case TILDE:
                    op = Operator.NOT;
                default:
                    throw new UnsupportedOperationException(operator.toString());
            }
        }

        @Override
        public String toString() {
            return operator.name() + expression;
        }

        @Override
        public String type() {
            return "unary_operator";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(expression);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R,A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument){
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            return op;
        }

        @Override
        public String shortType() {
            return operator.representation;
        }
    }

    /**
     * Arguments for a method call
     */
    public static class ArgumentsNode extends BlockPartNode {
        public final List<ExpressionNode> arguments;

        public ArgumentsNode(Location location, List<ExpressionNode> arguments) {
            super(location);
            this.arguments = arguments;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return arguments.toArray(new BlockPartNode[]{});
        }

        @Override
        public String type() {
            return "arguments";
        }

        @Override
        public String toString() {
            return Utils.toString(", ", arguments);
        }

        @Override
        public List<BaseAST> children() {
            return Arrays.asList(arguments.toArray(new BaseAST[]{}));
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "args";
        }

        public int size(){
            return arguments.size();
        }

        public ExpressionNode get(int i){
            return arguments.get(i);
        }
    }

    /**
     * Base class for all primary expressions
     */
    public static abstract class PrimaryExpressionNode extends ExpressionNode {
        public PrimaryExpressionNode(Location location) {
            super(location);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{};
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R,A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument){
            return visitor.visit(this, argument);
        }
    }

    /**
     * An integer literal expression
     */
    public static class IntegerLiteralNode extends PrimaryExpressionNode {
        public final Value value;
        public final Operator op;

        public IntegerLiteralNode(Location location, Value value) {
            super(location);
            this.value = value;
            this.op = new Operator.LiteralOperator(value);
        }

        @Override
        public String toString() {
            return value.toLiteralString();
        }

        @Override
        public String type() {
            return "integer_literal";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(new WrapperNode<>(location, value));
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R,A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument){
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            return op;
        }

        @Override
        public String shortType() {
            return "i";
        }
    }

    /**
     * A method invocation
     */
    public static class MethodInvocationNode extends PrimaryExpressionNode {
        public MethodNode definition;
        public final String method;
        public final ArgumentsNode arguments;

        public MethodInvocationNode(Location location, String method, ArgumentsNode arguments) {
            super(location);
            this.method = method;
            this.arguments = arguments;
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", method, arguments);
        }

        @Override
        public String type() {
            return "local_method_invocation";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{arguments};
        }

        @Override
        public List<BaseAST> children() {
            return (List<BaseAST>)(List<?>)arguments.arguments;
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R,A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument){
            return visitor.visit(this, argument);
        }

        @Override
        public String getTextualId() {
            return String.format("%s(c %s)", super.getTextualId(), method);
        }

        @Override
        public Operator getOperator() {
            return new Operator.MethodInvocation(definition);
        }

        @Override
        public String shortType() {
            return "mi";
        }
    }

    /**
     * Access of a variable
     */
    public static class VariableAccessNode extends PrimaryExpressionNode {
        public final String ident;
        Variable definition;

        public VariableAccessNode(Location location, String ident) {
            super(location);
            this.ident = ident;
        }

        @Override
        public String toString() {
            return definition == null ? ident : definition.name;
        }

        @Override
        public String type() {
            return "identifier_literal";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R,A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument){
            return visitor.visit(this, argument);
        }

        @Override
        public String getTextualId() {
            return shortType() + ":" + (definition != null ? definition.name : ident) + location.toString();
        }

        @Override
        public String shortType() {
            return "ac";
        }
        @Override
        public Operator getOperator() {
            return new Operator.VariableAccess(definition);
        }
    }

    /**
     * A phi node to join two variables from different control paths
     */
    public static class PhiNode extends ExpressionNode {
        public final List<ExpressionNode> controlDeps;
        public final List<VariableAccessNode> joinedVariables;

        public PhiNode(Location location, List<ExpressionNode> controlDeps, List<Variable> joinedVariables) {
            super(location);
            this.controlDeps = controlDeps;
            this.joinedVariables = joinedVariables.stream().map(v -> {
                VariableAccessNode n = new VariableAccessNode(location, v.name);
                n.definition = v;
                return n;
            }).collect(Collectors.toList());
        }

        @Override
        public List<BaseAST> children() {
            return (List<BaseAST>)(List<?>)joinedVariables;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[0];
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String type() {
            return "ɸ";
        }

        @Override
        public String toString() {
            return String.format("ɸ[%s](%s)",
                    controlDeps.stream().map(MJNode::toString).collect(Collectors.joining(",")),
                    String.join(", ", joinedVariables.stream().map(v -> v.definition.toString())
                    .collect(Collectors.toList())));
        }

        @Override
        public String getTextualId() {
            return toString();
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R,A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument){
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            if (joinedVariables.size() == 2){
                return Operator.PHI;
            }
            return Operator.PHI_GENERIC;
        }

        /*@Override
        public String getTextualId() {
            return shortType() + "(" + joinedVariables.stream().map(MJNode::getTextualId).collect(Collectors.joining(",")) + ")";
        }*/

        @Override
        public String shortType() {
            return "ϕ";
        }
    }

    /**
     * A basic error message
     */
    public static class MJError extends SWPException {

        public MJError(String message) {
            super(message);
        }
    }
}

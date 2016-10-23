package swp.parser.examples;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import swp.SWPException;
import swp.lexer.Token;
import swp.parser.lr.BaseAST;
import swp.parser.lr.Generator;
import swp.parser.lr.ListAST;
import swp.util.Utils;

import static swp.parser.examples.MiniJava.LexerTerminal.AND;
import static swp.parser.examples.MiniJava.LexerTerminal.DIVIDE;
import static swp.parser.examples.MiniJava.LexerTerminal.EQUALS;
import static swp.parser.examples.MiniJava.LexerTerminal.EQUAL_SIGN;
import static swp.parser.examples.MiniJava.LexerTerminal.GREATER;
import static swp.parser.examples.MiniJava.LexerTerminal.GREATER_EQUALS;
import static swp.parser.examples.MiniJava.LexerTerminal.INVERT;
import static swp.parser.examples.MiniJava.LexerTerminal.LOWER;
import static swp.parser.examples.MiniJava.LexerTerminal.LOWER_EQUALS;
import static swp.parser.examples.MiniJava.LexerTerminal.MINUS;
import static swp.parser.examples.MiniJava.LexerTerminal.MODULO;
import static swp.parser.examples.MiniJava.LexerTerminal.MULTIPLY;
import static swp.parser.examples.MiniJava.LexerTerminal.OR;
import static swp.parser.examples.MiniJava.LexerTerminal.PLUS;
import static swp.parser.examples.MiniJava.LexerTerminal.UNEQUALS;

/**
 * Code for the MiniJava language, see
 * http://pp.info.uni-karlsruhe.de/lehre/WS201617/compprakt/intern/sprachbericht.pdf
 * for a language specification.
 *
 * It translates MiniJava to C++.
 */
public class MiniJava {

    public static enum LexerTerminal implements Generator.LexerTerminalEnum {
        EOF(""),
        COMMENT("/\\*([^*\\n]*(\\*[^/\\n])?)*\\*/"),
        WS("[\\s]"),
        LBRK("[\\r\\n\\t]"),

        SYSTEM_OUT_PRINTLN("System.out.println"),
        BOOLEAN("boolean"),
        INT("int"),
        CLASS("class"),
        NEW("new"),
        RETURN("return"),
        THIS("this"),
        IF("if"),
        WHILE("while"),
        ELSE("else"),
        TRUE("true"),
        FALSE("false"),
        PUBLIC("public"),
        STATIC("static"),
        VOID("void"),
        NULL("null"),
        STRING("String"),

        RESERVED_KEYWORDS(
                "(abstract)|(assert)|(break)|(byte)|(case)|(catch)|(char)|(const)|(" +
                        "continue)|(default)|(double)|(do)|(else)|(enum)|(extends)|(false)|(finally)|(final)|(" +
                        "float)|(for)|(goto)|(if)|(implements)|(import)|(instanceof)|(interface)|(long)|(" +
                        "native)|(null)|(package)|(private)|(protected)|(public)|(short)|(" +
                        "static)|(strictfp)|(super)|(switch)|(synchronized)|(throws)|(throw)|(" +
                        "transient)|(try)|(void)|(volatile)"),

        MULTIPLY_EQUALS("\\*="),
        INCREMENT("\\+\\+"),
        PLUS_EQUALS("\\+="),
        MINUS_EQUALS("\\-="),
        DECREMENT("\\-\\-"),
        DIVIDE_EQUALS("/="),
        LOWER_LOWER_EQUALS("<<="),
        LOWER_LOWER("<<"),
        LOWER_EQUALS("<="),
        GREATER_EQUALS(">="),
        GREATER_GREATER_EQUALS(">>="),
        GREATER_GREATER_GREATER_EQUALS(">>>="),
        GREATER_GREATER_GREATER(">>>"),
        GREATER_GREATER(">>"),
        MODULO_EQUALS("%="),
        MODULO("%"),
        AND_EQUALS("&="),
        BIT_WISE_END("&"),
        LBRACKET("\\["),
        RBRACKET("\\]"),
        LRBRACKET("\\[ ([\\r\\n\\t\\s]? (/\\*([^*]*(\\*[^/])?)*\\*/)?)* \\]"),
        XOR("\\^", "^"),
        BIT_WISE_NOT("~"),
        BIT_WISE_OR("|"),

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
        LPAREN("\\("),
        RPAREN("\\)"),
        QUESTION_MARK("\\?"),
        SEMICOLON("\\;"),
        INTEGER_LITERAL("[1-9][0-9]*|0"),
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

    public Generator generator = Generator.getCachedIfPossible(null, LexerTerminal.class, new String[]{"WS", "COMMENT", "LBRK"},
            (builder) -> {
                builder.addRule("program", "class_declaration*", asts -> {
                            ProgramNode node = new ProgramNode();
                            for (Object ast : asts.getAll("class")) {
                                node.addClass((ClassNode)ast);
                            }
                            return node;
                        })
                        .addRule("class_declaration", "CLASS IDENT LCURLY class_members RCURLY", asts -> {
                            ClassNode node = new ClassNode(asts.get(1).getMatchedString());
                            for (Object ast : asts.getAll("field")) {
                                node.addField((FieldNode)ast);
                            }
                            for (Object method : asts.getAll("method")) {
                                node.addMethod((MethodNode)method);
                            }
                            for (Object mainMethod : asts.getAll("main_method")) {
                                node.setMainMethod((MainMethodNode)mainMethod);
                            }
                            return node;
                        })
                        .addRule("class_members", "class_member*")
                        .addEitherRule("class_member", "main_method", "field", "method")
                        .addRule("field", "PUBLIC type IDENT SEMICOLON", asts -> {
                            return new FieldNode((TypeNode)asts.get(1), asts.get(2).getMatchedString());
                        })
                        .addRule("main_method", "PUBLIC STATIC VOID IDENT LPAREN STRING LRBRACKET IDENT RPAREN block", asts -> {
                            return new MainMethodNode((BlockNode)asts.getLast());
                        })
                        .addRule("method", "PUBLIC type IDENT LPAREN parameters RPAREN block", asts -> {
                            return new MethodNode((TypeNode)asts.get(1), asts.get(2).getMatchedString(),
                                    (ParametersNode)asts.get(4), (BlockNode)asts.get(6));
                        })
                        .addRule("parameters", "", asts -> new ParametersNode(new ArrayList<>()))
                        .addRule("parameters", "parameter COMMA parameters", asts -> {
                            ParametersNode node = new ParametersNode(Utils.makeArrayList((ParameterNode)asts.get(0)));
                            node.parameterNodes.addAll(((ParametersNode)asts.get(2)).parameterNodes);
                            return node;
                        })
                        .addRule("parameters", "parameter", asts -> {
                            return new ParametersNode(Utils.makeArrayList((ParameterNode)asts.get(0)));
                        })
                        .addRule("parameter", "type IDENT", asts -> {
                            return new ParameterNode((TypeNode)asts.get(0), asts.get(1).getMatchedString());
                        })
                        .addRule("type", "type LRBRACKET", asts -> {
                            return new ArrayTypeNode((TypeNode)asts.get(0));
                        })
                        .addRule("type", "basic_type")
                        .addRule("basic_type", "basic_s_type", asts -> {
                            return new BasicTypeNode(asts.getMatchedString());
                        })
                        .addRule("basic_s_type", "INT")
                        .addRule("basic_s_type", "BOOLEAN")
                        .addRule("basic_s_type", "VOID")
                        .addRule("basic_s_type", "IDENT")
                        .addEitherRule("statement", "block", "empty_statement", "if_statement",
                                "expression_statement", "while_statement", "return_statement")

                        .addRule("block", "LCURLY block_statements RCURLY", asts -> asts.get(1))
                        .addRule("block_statements", "block_statement block_statements", asts -> {
                            BlockNode node = new BlockNode(Utils.makeArrayList((BlockPartNode)asts.get(0)));
                            node.statementNodes.addAll(((BlockNode)asts.get(1)).statementNodes);
                            return node;
                        })
                        .addRule("block_statements", "", asts -> {
                            return new BlockNode(new ArrayList<>());
                        })
                        .addRule("block_statement", "statement")
                        .addRule("block_statement", "local_variable_declaration_statement")
                        .addRule("local_variable_declaration_statement", "type IDENT SEMICOLON", asts -> {
                            return new LocalVariableDeclarationStatementNode(
                                    (TypeNode)asts.get(0),
                                    asts.get(1).getMatchedString());
                        })
                        .addRule("local_variable_declaration_statement", "type IDENT EQUAL_SIGN expression SEMICOLON", asts -> {
                            return new LocalVariableDeclarationStatementNode(
                                    (TypeNode)asts.get(0),
                                    asts.get(1).getMatchedString(),
                                    (ExpressionNode)asts.get(3));
                        })
                        .addRule("empty_statement", "SEMICOLON", asts -> {
                            return new EmptyStatementNode();
                        })
                        .addRule("while_statement", "WHILE LPAREN expression RPAREN statement", asts -> {
                            return new WhileStatementNode((ExpressionNode)asts.get(2),
                                    (StatementNode)asts.get(4));
                        })
                        .addRule("if_statement", "IF LPAREN expression RPAREN statement", asts -> {
                            return new IfStatementNode((ExpressionNode)asts.get(2),
                                    (StatementNode)asts.get(4));
                        })
                        .addRule("if_statement", "IF LPAREN expression RPAREN statement ELSE statement", asts -> {
                            return new IfStatementNode((ExpressionNode)asts.get(2),
                                    (StatementNode)asts.get(4), (StatementNode)asts.get(6));
                        })
                        .addRule("expression_statement", "expression SEMICOLON", asts -> {
                            return new ExpressionStatementNode((ExpressionNode)asts.get(0));
                        })
                        .addRule("return_statement", "RETURN SEMICOLON", asts -> {
                            return new ReturnStatementNode();
                        })
                        .addRule("return_statement", "RETURN expression SEMICOLON", asts -> {
                            return new ReturnStatementNode((ExpressionNode)asts.get(1));
                        })
                        .addOperators("expression", "postfix_expression", operators -> {
                            operators.defaultBinaryAction((asts, op) -> {
                                        return new BinaryOperatorNode((ExpressionNode)asts.get(0), (ExpressionNode)asts.get(2), LexerTerminal.valueOf(op));
                                    })
                                    .defaultUnaryAction((asts, op) -> {
                                        return new UnaryExpressionStatement((ExpressionNode)asts.get(1), LexerTerminal.valueOf(op));
                                    })
                                    .binaryRightAssociative(EQUAL_SIGN)
                                    .closeLayer()
                                    .binaryLayer(OR)
                                    .binaryLayer(AND)
                                    .binaryLayer(EQUALS, UNEQUALS)
                                    .binaryLayer(LOWER, LOWER_EQUALS, GREATER, GREATER_EQUALS)
                                    .binaryLayer(PLUS, MINUS)
                                    .binaryLayer(MULTIPLY, DIVIDE, MODULO)
                                    .unaryLayerLeft(INVERT, MINUS);
                        })
                        .addRule("postfix_expression", "array_access")
                        .addRule("postfix_expression", "primary_expression")
                        .addEitherRule("postfix_expression", "method_invocation", "field_access",
                                "system_out_println")


                        .addRule("method_invocation", "postfix_expression DOT IDENT LPAREN arguments RPAREN", asts -> {
                            return new MethodInvocationNode((PostfixExpressionNode)asts.get(0),
                                    asts.get(2).getMatchedString(), (ArgumentsNode)asts.get(4));
                        })
                        .addRule("field_access", "postfix_expression DOT IDENT", asts -> {
                            return new FieldAccessNode((PostfixExpressionNode)asts.get(0), asts.get(2).getMatchedString());
                        })
                        .addRule("array_access", "postfix_expression LBRACKET expression RBRACKET", asts -> {
                            return new ArrayAccessNode((PostfixExpressionNode)asts.get(0), (ExpressionNode)asts.get(2));
                        })
                        .addRule("system_out_println", "SYSTEM_OUT_PRINTLN LPAREN expression RPAREN", asts -> {
                            return new SystemOutPrintLnNode((ExpressionNode)asts.get(2));
                        })
                        .addRule("arguments", "", asts -> new ArgumentsNode(new ArrayList<>()))
                        .addRule("arguments", "expression", asts -> {
                            return new ArgumentsNode(Utils.makeArrayList((ExpressionNode)asts.get(0)));
                        })
                        .addRule("arguments", "expression COMMA expressions", asts -> {
                            List<ExpressionNode> args = Utils.makeArrayList((ExpressionNode)asts.get(0));
                            args.addAll(((ArgumentsNode)asts.get(1)).arguments);
                            return new ArgumentsNode(args);
                        })
                        .addRule("primary_expression", "NULL", asts -> new NullLiteralNode())
                        .addRule("primary_expression", "FALSE", asts -> new BooleanLiteralNode(false))
                        .addRule("primary_expression", "TRUE", asts -> new BooleanLiteralNode(true))
                        .addRule("primary_expression", "INTEGER_LITERAL", asts -> {
                            return new IntegerLiteralNode(Integer.parseInt(asts.getMatchedString()));
                        })
                        .addRule("primary_expression", "IDENT", asts -> new IdentifierLiteralNode(asts.getMatchedString()))
                        .addRule("primary_expression", "IDENT LPAREN arguments RPAREN", asts -> {
                            return new LocalMethodInvocationNode(asts.get(0).getMatchedString(),
                                    (ArgumentsNode)asts.get(2));
                        })
                        .addRule("primary_expression", "THIS", asts -> new IdentifierLiteralNode(asts.getMatchedString()))
                        .addRule("primary_expression", "LPAREN expression RPAREN", asts -> {
                            return asts.get(1);
                        })
                        .addRule("primary_expression", "new_object_expression")
                        .addRule("primary_expression", "new_array_expression")
                        .addRule("new_object_expression", "NEW IDENT LPAREN RPAREN", asts -> {
                            return new NewObjectExpressionNode(asts.get(1).getMatchedString());
                        })
                        .addRule("new_array_expression", "NEW basic_type LBRACKET expression RBRACKET (LRBRACKET)*", asts -> { // type=1, expr=3,
                            int dimension = asts.get(5).<ListAST>as().getAll("leaf").size() + 1;
                            System.out.println(asts);
                            BasicTypeNode typeNode = (BasicTypeNode)asts.get(1);
                            return new NewArrayExpressionNode(typeNode, (ExpressionNode)asts.get(3), dimension);
                        });
            }, "program");

    public static void main(String[] args) {
        Generator generator = new MiniJava().generator;
        //Utils.repl(s -> generator.createLexer(s));
        Utils.parserRepl(s -> {
            MJNode result = (MJNode)generator.parse(s);
            result.cpp(System.out);
            System.out.println(result.compile(true));
            return result.toPrettyString();
        });
    }

    public static abstract class MJNode extends BaseAST {
        public void cpp(PrintStream out){}

        @Override
        public List<Token> getMatchedTokens() {
            return null;
        }

        public boolean compile(boolean run){
            try {
                Path tmpFile = Files.createTempFile("test", ".c");
                PrintStream stream = new PrintStream(Files.newOutputStream(tmpFile));
                cpp(stream);
                stream.close();
                Process process = new ProcessBuilder().inheritIO().command("g++", tmpFile.toAbsolutePath() + "",
                        "-o", tmpFile.toAbsolutePath() + ".out").start();
                process.waitFor();
                if (process.exitValue() > 0){
                    return false;
                }
                if (run) {
                    process = new ProcessBuilder().inheritIO()
                            .command(tmpFile.toAbsolutePath() + ".out").start();
                    process.waitFor();
                    System.out.println();
                }
                return true;
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    public static class ProgramNode extends MJNode {
        public final List<ClassNode> classes = new ArrayList<>();
        private boolean hasMainMethod = false;
        private ClassNode mainMethodClass = null;

        @Override
        public void cpp(PrintStream out) {
            //out.println("#include <bool>");
            out.println("class $$Int {public: int value = 0; $$Int(int v): value(v) {} };");
            out.println("class $$Boolean {public: bool value = false; $$Boolean(bool v): value(v) {} };");
            for (String op : new String[]{"+", "*", "/", "-", "%"}) {
                out.println(String.format("template<typename T> T* operator%s(const T lhs, const T& rhs){\n" +
                        "\treturn new T(lhs.value %s rhs.value);\n" +
                        "}", op, op));
            }
            for (String op : new String[]{ "<", "<=", ">", ">=", "==", "!="}){
                for (String t : new String[]{"$$Int", "$$Boolean"}) {
                    out.println(String.format("bool operator%s(const %s& lhs, const %s&rhs){",
                            op, t, t));
                    out.println(String.format("\treturn lhs.value %s rhs.value;\n}", op));
                }
            }

            out.println("bool $$bool($$Int *o){return o->value != 0;}");
            out.println("bool $$bool($$Boolean *o){return o->value;}");
            out.println("int $$int($$Int *o){return o->value;}");
            out.println("int $$int($$Boolean *o){return o->value ? 0 : 1;}");
            out.println("template<typename T> bool $$bool(T *o){return o != 0;}");
            out.println("#include <iostream>");
            out.println("template<typename T> void $println(T *o){std::cout << o->value << \"\\n\";}");
            out.println("$$Int* operator-(const $$Int& in){ return new $$Int(-in.value); }");
            out.println("bool operator!(const $$Boolean& in){ return !in.value; }");
            //out.println("void $print($$Int *o){printf(\"H\");printf(\"%d\",o->value);}");
            //out.println("void $print($$Boolean *o){std::cout << o->value;}");

            for (ClassNode aClass : classes) {
                out.println(String.format("class %s;", aClass.name));
            }
            out.println();
            for (ClassNode aClass : classes) {
                aClass.cppDeclaration(out);
            }
            out.println();
            for (ClassNode aClass : classes) {
                aClass.cppDefinition(out);
            }
            if (hasMainMethod){
                out.println(getMainMethod().cpp());
            }
        }

        public void addClass(ClassNode classNode){
            if (hasClass(classNode.name)){
                throw new MJError(String.format("A class with name \"%s\" already exists", classNode.name));
            }
            if (Utils.makeArrayList("void", "int", "boolean").contains(classNode.name)){
                throw new MJError(String.format("\"%s\" is an invalid class name", classNode.name));
            }
            if (classNode.hasMainMethod()){
                if (hasMainMethod) {
                    throw new MJError(String.format("Class \"%s\": " +
                                    "There already exists a class with a main method (class \"%s\")",
                            classNode.name, mainMethodClass.name));
                } else {
                    hasMainMethod = true;
                    mainMethodClass = classNode;
                }
            }
            classes.add(classNode);
            classNode.program = this;
        }

        public MainMethodNode getMainMethod(){
            if (hasMainMethod){
                return mainMethodClass.mainMethod;
            }
            return null;
        }

        public List<String> getClassNames(){
            return classes.stream().map(n -> n.name).collect(Collectors.toList());
        }

        public ClassNode getClass(String name){
            for (ClassNode aClass : classes) {
                if (aClass.name.equals(name)){
                    return aClass;
                }
            }
            return null;
        }

        public boolean hasClass(String name){
            return getClass(name) != null;
        }

        public boolean hasSystemOutPrintln(){
            FieldNode outField = getField("System", "out");
            return outField != null && getMethod(outField.type, "println") != null;
        }

        public ClassNode getClass(TypeNode typeNode){
            if (typeNode instanceof BasicTypeNode){
                BasicTypeNode basicTypeNode = (BasicTypeNode)typeNode;
                if (basicTypeNode.isCustom()){
                    return getClass(basicTypeNode.name);
                }
            }
            return null;
        }

        public boolean hasClass(TypeNode typeNode){
            return getClass(typeNode) != null;
        }

        public MethodNode getMethod(TypeNode typeNode, String method){
            ClassNode classNode = getClass(typeNode);
            if (classNode != null && classNode.hasMethod(method)){
                return classNode.getMethod(method);
            }
            return null;
        }

        public FieldNode getField(TypeNode typeNode, String field){
            ClassNode classNode = getClass(typeNode);
            if (classNode != null && classNode.hasField(field)){
                return classNode.getField(field);
            }
            return null;
        }

        public FieldNode getField(String klass, String field){
            ClassNode classNode = getClass(klass);
            if (classNode != null && classNode.hasField(field)){
                return classNode.getField(field);
            }
            return null;
        }

        public boolean hasField(String klass, String field){
            return getField(klass, field) != null;
        }

        @Override
        public String toString() {
            return Utils.toString("\n\n", classes);
        }

        @Override
        public String type() {
            return "program";
        }

        @Override
        public List<BaseAST> children() {
            return Arrays.asList(classes.toArray(new BaseAST[]{}));
        }
    }

    public static class ClassNode extends MJNode {
        public ProgramNode program;
        public String name;
        public List<FieldNode> fields = new ArrayList<>();
        public List<MethodNode> methods = new ArrayList<>();
        public MainMethodNode mainMethod = null;

        public ClassNode(String name){
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("class %s {\nFields: \n" +
                    "%s\n" +
                    "Methods:\n" +
                    "%s\n" +
                    "MainMethod:\n" +
                    "\n}", name, Utils.toString("\n\n", fields),
                    Utils.toString("\n\n", methods), mainMethod);
        }

        public boolean hasMainMethod(){
            return mainMethod != null;
        }

        public void addField(FieldNode field){
            if (hasField(field.name)){
                error(String.format("Field \"%s\" already exists", field.name));
            }
            fields.add(field);
            field.parent = this;
        }

        public void addMethod(MethodNode method){
            if (hasMethod(method.name) || (method.name.equals("main") && hasMainMethod())){
                error(String.format("Method with name \"%s\" already exists", method.name));
            }
            methods.add(method);
            method.parent = this;
            method.body.setParentMethod(method);
        }

        public void setMainMethod(MainMethodNode method){
            if (hasMethod("main") || hasMainMethod()){
                error("Method with name \"main\" already exists");
            }
            mainMethod = method;
            method.parent = this;
            method.body.setParentMethod(method);
        }

        public FieldNode getField(String name){
            for (FieldNode field : fields) {
                if (field.name.equals(name)){
                    return field;
                }
            }
            return null;
        }

        public boolean hasField(String name){
            return getField(name) != null;
        }

        public MethodNode getMethod(String name){
            for (MethodNode method : methods) {
                if (method.name.equals(name)){
                    return method;
                }
            }
            return null;
        }

        public boolean hasMethod(String name){
            return getMethod(name) != null;
        }

        @Override
        public String type() {
            return "class";
        }

        private void error(String msg){
            throw new MJError(String.format("Error in class %s: %s", name, msg));
        }

        @Override
        public List<BaseAST> children() {
            List<BaseAST> children = new ArrayList<>();
            if (hasMainMethod()){
                children.add(mainMethod);
            }
            children.addAll(fields);
            children.addAll(methods);
            return children;
        }

        public void cppDeclaration(PrintStream out) {
            out.println(String.format("class %s {", name));
            out.println("public:");
            out.println();
            for (FieldNode field : fields) {
                out.println("\t" + field.cpp());
            }
            out.println();
            for (MethodNode method : methods) {
                out.println("\t" + method.cppDeclaration());
            }
            out.println("};");
        }

        public void cppDefinition(PrintStream out) {
            for (MethodNode method : methods) {
                out.println(method.cppDefinition());
            }
        }
    }

    public static abstract class ClassMemberNode extends MJNode {

        public ClassNode parent;

    }

    public static class FieldNode extends ClassMemberNode {

        public final TypeNode type;
        public final String name;

        public FieldNode(TypeNode type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("public %s %s;", type, name);
        }

        @Override
        public String type() {
            return "field";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(type, new StringNode(name));
        }

        public String cpp() {
            String initExpr = null;
            switch (type.typeName()){
                case "int":
                    initExpr = "new $$Int(0)";
                    break;
                case "boolean":
                    initExpr = "new $$Boolean(false)";
                    break;
            }
            if (initExpr == null) {
                return String.format("%s %s;", type.cpp(), name);
            }
            return String.format("%s %s = %s;", type.cpp(), name, initExpr);
        }
    }

    public static class MainMethodNode extends MethodNode {

        public MainMethodNode(BlockNode body) {
            super(body);
        }

        @Override
        public String toString() {
            return "public static void main(…): " + body;
        }

        @Override
        public String type() {
            return "main_method";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(body);
        }

        public String cpp(){
            StringBuilder builder = new StringBuilder();
            builder.append("int main()").append(body.cpp(1));
            return builder.toString();
        }
    }

    public static class MethodNode extends ClassMemberNode {
        public final TypeNode returnType;
        public final String name;
        public final ParametersNode parameters;
        public final BlockNode body;

        protected MethodNode(BlockNode body){
            this(null, "main", null, body);
        }

        public MethodNode(TypeNode returnType, String name, ParametersNode parameters, BlockNode body) {
            this.returnType = returnType;
            this.name = name;
            this.parameters = parameters;
            this.body = body;
        }

        @Override
        public String toString() {
            return String.format("%s %s (%s): %s", returnType, name, parameters, body);
        }

        @Override
        public String type() {
            return "method";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(returnType, new StringNode(name), parameters, body);
        }

        public String cppHeader(){
            return String.join(" ", returnType.cpp(), "$" + name, "(", parameters.cpp(), ")");
        }

        public String cppDeclaration() {
            return cppHeader() + ";";
        }

        public String cppDefinition() {
            StringBuilder builder = new StringBuilder();
            builder.append(returnType.cpp()).append(parent.name).append("::").append("$" + name);
            builder.append("(").append(parameters.cpp()).append(")").append(body.cpp());
            return builder.toString();
        }
    }

    public static abstract class MethodPartNode extends MJNode {
        public MethodNode parentMethod;

        public abstract void setParentMethod(MethodNode parentMethod);
    }

    public static class ParametersNode extends MethodPartNode {

        public final List<ParameterNode> parameterNodes;

        public ParametersNode(List<ParameterNode> parameterNodes) {
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

        public String cpp(){
            return parameterNodes.stream().map(ParameterNode::cpp).collect(Collectors.joining(", "));
        }
    }

    public static class ParameterNode extends MethodPartNode {
        public final TypeNode parameterType;
        public final String name;

        public ParameterNode(TypeNode parameterType, String name) {
            this.parameterType = parameterType;
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("%s %s", parameterType, name);
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
            return Utils.makeArrayList(parameterType, new StringNode(name));
        }

        public String cpp(){
            return String.join(" ", parameterType.cpp(), name);
        }
    }

    public static abstract class TypeNode extends MJNode {

        public abstract String typeName();

        public abstract String cpp();

    }

    public static class ArrayTypeNode extends TypeNode {
        public final TypeNode subType;

        public ArrayTypeNode(TypeNode subType) {
            this.subType = subType;
        }

        @Override
        public String toString() {
            return subType + "[]";
        }

        @Override
        public String type() {
            return "array_type";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(subType);
        }

        @Override
        public String cpp() {
            return subType.cpp() + "**";
        }

        @Override
        public String typeName() {
            return "$$array";
        }
    }

    public static class BasicTypeNode extends TypeNode {
        public final String name;

        public BasicTypeNode(String name) {
            this.name = name;
        }

        public boolean isVoid(){
            return name.equals("void");
        }

        public boolean isInt(){
            return name.equals("int");
        }

        public boolean isBoolean(){
            return name.equals("boolean");
        }

        public boolean isCustom(){
            return !isBoolean() && !isInt() && !isVoid();
        }

        @Override
        public String toString() {
            return String.format("[Type %s]", name);
        }

        @Override
        public String type() {
            return "basic_type";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(new StringNode(name));
        }

        @Override
        public String cpp() {
            String type = name;
            switch (name){
                case "int":
                    type = "$$Int";
                    break;
                case "boolean":
                    type = "$$Boolean";
                    break;
            }
            return type + "*";
        }

        @Override
        public String typeName() {
            return name;
        }
    }

    public static abstract class BlockPartNode extends MethodPartNode {
        public BlockNode parentBlock = null;

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

        public String cpp(){
            return cpp(0);
        }

        public abstract String cpp(int identation);
    }

    public static abstract class StatementNode extends BlockPartNode {
        public BlockNode parentBlock = null;
    }

    public static class BlockNode extends StatementNode {
        public final List<BlockPartNode> statementNodes;

        public BlockNode(List<BlockPartNode> statementNodes) {
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
            return String.format("{\n%s\n}", Utils.toString("\n", statementNodes));
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
        public String cpp(int identation) {
            StringBuilder builder = new StringBuilder();
            builder.append(ws(identation)).append("{\n");
            for (BlockPartNode statementNode : statementNodes) {
                if (statementNode instanceof BlockNode){
                    builder.append(statementNode.cpp(identation + 1));
                } else {
                    builder.append(ws(identation + 1)).append(statementNode.cpp());
                }
            }
            builder.append("\n").append(ws(identation)).append("}\n");
            return builder.toString();
        }
    }

    public static class LocalVariableDeclarationStatementNode extends BlockPartNode {
        public final TypeNode type;
        public final String name;
        public final ExpressionNode initExpression;

        public LocalVariableDeclarationStatementNode(TypeNode type, String name, ExpressionNode initExpression) {
            this.type = type;
            this.name = name;
            this.initExpression = initExpression;
        }

        public LocalVariableDeclarationStatementNode(TypeNode type, String name) {
            this(type, name, null);
        }

        public boolean hasInitExpression(){
            return initExpression != null;
        }

        @Override
        public String toString() {
            if (hasInitExpression()){
                return String.format("%s %s = %s;", type, name, initExpression);
            } else {
                return String.format("%s %s;", type, name);
            }
        }

        @Override
        public String type() {
            return "local_variable_declaration_statement";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{initExpression};
        }

        @Override
        public List<BaseAST> children() {
            if (hasInitExpression()){
                return Utils.makeArrayList(type, new StringNode(name), initExpression);
            }
            return Utils.makeArrayList(type, new StringNode(name));
        }

        @Override
        public String cpp(int identation) {
            StringBuilder builder = new StringBuilder();
            builder.append(ws(identation)).append(type.cpp()).append(" ").append(name);
            String initExpr = null;
            if (hasInitExpression()){
                initExpr = initExpression.cpp();
            } /*else {
                switch (type.typeName()){
                    case "int":
                        initExpr = "new $$Int(0)";
                        break;
                    case "boolean":
                        initExpr = "new $$Boolean(false)";
                        break;
                }
            }*/
            if (initExpr != null){
                builder.append(" = ").append(initExpr);
            }
            builder.append(";\n");
            return builder.toString();
        }

    }

    public static class EmptyStatementNode extends StatementNode {

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
        public String cpp(int identation) {
            return "";
        }
    }

    public static class WhileStatementNode extends StatementNode {
        public final ExpressionNode conditionalExpression;
        public final StatementNode body;

        public WhileStatementNode(ExpressionNode conditionalExpression, StatementNode body) {
            this.conditionalExpression = conditionalExpression;
            this.body = body;
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
        public String cpp(int identation) {
            StringBuilder builder = new StringBuilder();
            builder.append(ws(identation)).append("while (")
                    .append("$$bool(").append(conditionalExpression.cpp()).append(")")
                    .append(")\n").append(body.cpp(identation));
            return builder.toString();
        }
    }

    public static class IfStatementNode extends StatementNode {
        public final ExpressionNode conditionalExpression;
        public final StatementNode ifBlock;
        public final StatementNode elseBlock;

        public IfStatementNode(ExpressionNode conditionalExpression, StatementNode ifBlock, StatementNode elseBlock) {
            this.conditionalExpression = conditionalExpression;
            this.ifBlock = ifBlock;
            this.elseBlock = elseBlock;
        }

        public IfStatementNode(ExpressionNode conditionalExpression, StatementNode ifBlock) {
            this(conditionalExpression, ifBlock, null);
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
        public String cpp(int identation) {
            StringBuilder builder = new StringBuilder();
            builder.append(ws(identation))
                    .append("if ($$bool(").append(conditionalExpression.cpp()).append(")) ")
                    .append(ifBlock.cpp(identation + 1));
            if (hasElseBlock()){
                builder.append(" else ").append(elseBlock.cpp(identation + 1));
            }
            return builder.toString();
        }
    }

    public static class ExpressionStatementNode extends StatementNode {
        public final ExpressionNode expression;

        public ExpressionStatementNode(ExpressionNode expression) {
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
        public String cpp(int identation) {
            return expression.cpp(identation) + ";\n";
        }
    }

    public static class ReturnStatementNode extends ExpressionStatementNode {

        public ReturnStatementNode(){
            super(null);
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
        public String cpp(int identation) {
            if (hasReturnExpression()) {
                return String.format("return %s;", expression.cpp());
            } else {
                return "return;";
            }
        }
    }

    public static abstract class ExpressionNode extends BlockPartNode {

    }

    public static class BinaryOperatorNode extends ExpressionNode {
        public final ExpressionNode left;
        public final ExpressionNode right;
        public final LexerTerminal operator;

        public BinaryOperatorNode(ExpressionNode left, ExpressionNode right, LexerTerminal operator) {
            this.left = left;
            this.right = right;
            this.operator = operator;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{left, right};
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", left, operator.name(), right);
        }

        @Override
        public String type() {
            return "binary_operator";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(new StringNode(operator.name()), left, right);
        }

        @Override
        public String cpp(int identation) {
            if (Arrays.asList(new String[]{"+", "-", "/", "*", "%"}).contains(operator.representation)){
                return String.format("(*(%s) %s *(%s))", left.cpp(), operator.representation, right.cpp());
            }
            if (operator.representation == "||" || operator.representation == "&&"){
                return String.format("(new $$Boolean($$bool(%s) %s $$bool(%s)))",
                        left.cpp(), operator.representation, right.cpp());
            }
            if (Arrays.asList(new String[]{"<", "<=", ">", ">=", "==", "!="}).contains(operator.representation)){
                return String.format("(new $$Boolean(*(%s) %s *(%s)))",
                        left.cpp(), operator.representation, right.cpp());
            }
            return String.join(" ", left.cpp(), operator.representation, right.cpp());
        }
    }

    public static class UnaryExpressionStatement extends ExpressionNode {
        public final ExpressionNode expression;
        public final LexerTerminal operator;

        public UnaryExpressionStatement(ExpressionNode expression, LexerTerminal operator) {
            this.expression = expression;
            this.operator = operator;
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
            return Utils.makeArrayList(expression, new StringNode(operator.name()));
        }

        @Override
        public String cpp(int identation) {
            switch (operator.representation){
                case "-":
                    return String.format("(-(*(%s)))", expression.cpp());
                case "!":
                    return String.format("(new $$Boolean(!$$bool(%s)))", expression.cpp());
            }
            return operator.representation + " " + expression.cpp();
        }
    }

    public static abstract class PostfixExpressionNode extends ExpressionNode {

    }

    public static class MethodInvocationNode extends PostfixExpressionNode {
        public final PostfixExpressionNode expression;
        public final String method;
        public final ArgumentsNode arguments;

        public MethodInvocationNode(PostfixExpressionNode expression, String method, ArgumentsNode arguments) {
            this.expression = expression;
            this.method = method;
            this.arguments = arguments;
        }

        @Override
        public String toString() {
            return String.format("%s.%s(%s)", expression, method, arguments);
        }

        @Override
        public String type() {
            return "method_invocation";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression, arguments};
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(expression, new StringNode(method), arguments);
        }

        @Override
        public String cpp(int identation) {
            StringBuilder builder = new StringBuilder();
            builder.append(expression.cpp()).append("->").append("$" + method)
                    .append("(").append(arguments.cpp()).append(")");
            return builder.toString();
        }
    }

    public static class ArgumentsNode extends BlockPartNode {
        public final List<ExpressionNode> arguments;

        public ArgumentsNode(List<ExpressionNode> arguments) {
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
        public String cpp(int identation) {
            return arguments.stream().map(ExpressionNode::cpp).collect(Collectors.joining(", "));
        }
    }

    public static class FieldAccessNode extends PostfixExpressionNode {
        public final PostfixExpressionNode expression;
        public final String field;

        public FieldAccessNode(PostfixExpressionNode expression, String field) {
            this.expression = expression;
            this.field = field;
        }

        @Override
        public String toString() {
            return "." + field;
        }

        @Override
        public String type() {
            return "field_access";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(expression, new StringNode(field));
        }

        @Override
        public String cpp(int identation) {
            return String.join("->", expression.cpp(), field);
        }
    }

    public static class ArrayAccessNode extends PostfixExpressionNode {
        public final PostfixExpressionNode expression;
        public final ExpressionNode indexExpression;

        public ArrayAccessNode(PostfixExpressionNode expression, ExpressionNode indexExpression) {
            this.expression = expression;
            this.indexExpression = indexExpression;
        }

        @Override
        public String toString() {
            return String.format("%s[%s]", expression, indexExpression);
        }

        @Override
        public String type() {
            return "array_access";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression, indexExpression};
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(expression, indexExpression);
        }

        @Override
        public String cpp(int identation) {
            return String.format("(*%s)[$$int(%s)]", expression.cpp(), indexExpression.cpp());
        }
    }

    public static abstract class PrimaryExpressionNode extends PostfixExpressionNode {
        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{};
        }
    }

    public static class NullLiteralNode extends PrimaryExpressionNode {

        @Override
        public String toString() {
            return "null";
        }

        @Override
        public String type() {
            return "null_literal";
        }

        @Override
        public List<BaseAST> children() {
            return new ArrayList<>();
        }

        @Override
        public String cpp(int identation) {
            return "0";
        }
    }

    public static class BooleanLiteralNode extends PrimaryExpressionNode {
        public final boolean value;

        public BooleanLiteralNode(boolean value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "" + value;
        }

        @Override
        public String type() {
            return "boolean_literal";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(new StringNode(value));
        }

        @Override
        public String cpp(int identation) {
            return String.format("(new $$Boolean(%s))", value);
        }
    }

    public static class IntegerLiteralNode extends PrimaryExpressionNode {
        public final int value;

        public IntegerLiteralNode(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "" + value;
        }

        @Override
        public String type() {
            return "integer_literal";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(new StringNode(value));
        }

        @Override
        public String cpp(int identation) {
            return String.format("(new $$Int(%d))", value);
        }
    }

    public static class LocalMethodInvocationNode extends PrimaryExpressionNode {
        public final String method;
        public final ArgumentsNode arguments;

        public LocalMethodInvocationNode(String method, ArgumentsNode arguments) {
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
            return Utils.makeArrayList(new StringNode(method), arguments);
        }

        @Override
        public String cpp(int identation) {
            return String.format("$%s(%s)", method, arguments.cpp());
        }
    }

    public static class IdentifierLiteralNode extends PrimaryExpressionNode {
        public final String ident;

        public IdentifierLiteralNode(String ident) {
            this.ident = ident;
        }

        @Override
        public String toString() {
            return ident;
        }

        @Override
        public String type() {
            return "identifier_literal";
        }

        public boolean isThisLiteral(){
            return ident.equals("this");
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(new StringNode(ident));
        }

        @Override
        public String cpp(int identation) {
            return ident;
        }
    }

    public static class NewObjectExpressionNode extends PrimaryExpressionNode {

        public final String type;

        public NewObjectExpressionNode(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return String.format("new %s()", type);
        }

        @Override
        public String type() {
            return "new_object_expression";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(new StringNode(type));
        }

        @Override
        public String cpp(int identation) {
            return String.format("(new %s())", type);
        }
    }

    public static class NewArrayExpressionNode extends PrimaryExpressionNode {
        public final BasicTypeNode type;
        public final ExpressionNode firstIndexExpression;
        public final int dimension;

        public NewArrayExpressionNode(BasicTypeNode type, ExpressionNode firstIndexExpression, int dimension) {
            this.type = type;
            this.firstIndexExpression = firstIndexExpression;
            this.dimension = dimension;
        }

        @Override
        public String toString() {
            return String.format("new %s[%s]: %d", type, firstIndexExpression, dimension);
        }

        @Override
        public String type() {
            return "new_array_expression";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{firstIndexExpression};
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(type, firstIndexExpression, new StringNode(dimension));
        }

        @Override
        public String cpp(int identation) {
            String builder = String.format("[&](){ int $$b = $$int(%s); %s $$a = new %s[$$b]; " +
                            "for (int $$i = 0; $$i < $$b; $$i++) $$a[$$i] = new %s[1]; return $$a; }()",
                    firstIndexExpression.cpp(),
                    starredType(dimension * 2),
                    starredType(dimension * 2 - 1),
                    starredType(dimension * 2 - 2));
            return builder;
        }

        private String starredType(int dimension){
            StringBuilder builder = new StringBuilder();
            builder.append(type.cpp());
            for (int i = 0; i < dimension; i++) {
                builder.append("*");
            }
            return builder.toString();
        }
    }

    public static class SystemOutPrintLnNode extends PrimaryExpressionNode {

        public final ExpressionNode expression;

        public SystemOutPrintLnNode(ExpressionNode expression) {
            this.expression = expression;
        }


        @Override
        public String cpp(int identation) {
            System.out.println(parentMethod);
            if (parentMethod.parent.program.hasClass("String")){
                return new MethodInvocationNode(
                        new FieldAccessNode(new IdentifierLiteralNode("String"), "out"), "println",
                        new ArgumentsNode(Utils.makeArrayList(expression))).cpp();
            } else {
                return String.format("$println(%s)", expression.cpp());
            }
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(expression);
        }

        @Override
        public String type() {
            return "system_out_println";
        }
    }

    public static class StringNode extends BaseAST {

        public final String value;

        public StringNode(String value) {
            this.value = Utils.toPrintableRepresentation(value);
        }

        public StringNode(int value){
            this.value = "" + value;
        }

        public StringNode(boolean value){
            this.value = "" + value;
        }

        @Override
        public List<Token> getMatchedTokens() {
            return null;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public String type() {
            return "string";
        }

        @Override
        protected String toPrettyString(int ident, int total) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < total; i++){
                builder.append("\t");
            }
            builder.append(this);
            return builder.toString();
        }
    }

    public static class MJError extends SWPException {

        public MJError(String message) {
            super(message);
        }
    }

    public static String ws(int identation){
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < identation; i++) {
            builder.append("\t");
        }
        return builder.toString();
    }
}

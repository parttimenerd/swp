package swp.parser.examples;

import swp.SWPException;
import swp.lexer.Token;
import swp.parser.lr.BaseAST;
import swp.parser.lr.Generator;
import swp.parser.lr.ListAST;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Different ways to implement a simple calculator.
 */
public class SimpleCalculator3 implements Serializable {

	private Generator generator;

	private Context context = new Context();

	private static enum LexerTerminal implements Generator.LexerTerminalEnum {
		EOF(""),
		PLUS("\\+"),
		MINUS("\\-"),
		DIVIDE("/"),
		MULTIPLY("\\*"),
		POW("\\*\\*"),
		EQUAL_SIGN("="),
		EQUALS("=="),
		UNEQUALS("!="),
		INVERT("!"),
		LOWER("<"),
		GREATER(">"),
		AND("&&"),
		OR("||"),
		LPAREN("\\("),
		RPAREN("\\)"),
		QUESTION_MARK("\\?"),
		COLON("\\;"),
		NUMBER("\\d+"),
		STRING("\"[^\"]*\""),
		ID("[A-Za-z_][A-Za-z0-9_]*"),
		WS("[\\s]"),
		COMMENT("\\#([^\\n]*)");

		private String description;

		LexerTerminal(String description){
			this.description = description;
		}

		@Override
		public String getTerminalDescription() {
			return description;
		}

		private static LexerTerminal[] terminals = values();

		static LexerTerminal valueOf(int id){
			return terminals[id];
		}
	}

	public SimpleCalculator3() {
		generator = Generator.getCachedIfPossible("",
				LexerTerminal.class,
				new String[]{"WS", "COMMENT"}, builder -> {
					builder.addRule("expr", "p15")
							.addRule("p15", "p14 QUESTION_MARK p14 COLON p14", asts ->
									((NumberNode)asts.get(0)).toBoolean() ? asts.get(2) : asts.get(4))
							.addRule("p15", "ID EQUAL_SIGN p15", asts -> {
								context.put(asts.get(0).getMatchedString(), (Node)asts.get(2));
								return asts.get(2);
							})
							.addRule("p15", "p14")

							.addRule("p14", "p14 OR p13", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.OR, asts.get(2)))
							.addRule("p14", "p13")

							.addRule("p13", "p13 AND p7", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.AND, asts.get(2)))
							.addRule("p13", "p7")

							.addRule("p7", "p7 EQUALS p6", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.EQUALS, asts.get(2)))
							.addRule("p7", "p7 UNEQUALS p6", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.UNEQUALS, asts.get(2)))
							.addRule("p7", "p6")

							.addRule("p6", "p6 LOWER p4", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.LOWER, asts.get(2)))
							.addRule("p6", "p6 GREATER p4", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.GREATER, asts.get(2)))

							.addRule("p6", "p4")

							.addRule("p4", "p4 PLUS t", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.PLUS, asts.get(2)))
							.addRule("p4", "p4 MINUS t", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.MINUS, asts.get(2)))
							.addRule("p4", "t")

							.addRule("t", "t MULTIPLY neg", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.MULTIPLY, asts.get(2)))
							.addRule("t", "t DIVIDE neg", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.DIVIDE, asts.get(2)))
							.addRule("t", "neg POW t", asts ->
									((Node)asts.get(0)).binaryOperation(BinaryOperation.POW, asts.get(2)))
							.addRule("t", "neg")

							.addRule("neg", "f NEGATE", asts ->
									((Node)asts.get(0)).unaryOperation(UnaryOperation.INVERT))
							.addRule("neg", "f")

							.addRule("f", "LPAREN expr RPAREN", asts -> asts.get(1))
							.addRule("f", "MINUS LPAREN expr RPAREN", asts ->
									((Node)asts.get(1)).unaryOperation(UnaryOperation.NEGATE))
							.addRule("f", "(PLUS | MINUS)? NUMBER", asts -> {
								return new NumberNode(new BigInteger(asts.getMatchedString()));
							})
							.addRule("f", "STRING", asts -> {
								String str = asts.getMatchedString();
								return new StringNode(str.substring(1, str.length() - 1));
							})
							.addRule("f", "ID", asts ->
									context.get(asts.getMatchedString()));
				}, "expr");

	}

	private static BigInteger number(ListAST asts, int pos){
		return ((NumberNode) asts.get(pos)).num;
	}

	private static long integer(ListAST asts, int pos){
		return number(asts, pos).intValue();
	}

	public Object eval(String input) {
		context = new Context();
		return generator.parse(input);
	}

	private enum Type {
		INTEGER, STRING
	}

	private static class UnsupportedUnaryOperation extends SWPException {
		public UnsupportedUnaryOperation(UnaryOperation operation, Node node) {
			super(String.format("Unary operation %s isn't supported on %s", operation, node));
		}
	}

	private static class UnsupportedBinaryOperation extends SWPException {
		public UnsupportedBinaryOperation(BinaryOperation operation, Node left, Node right) {
			super(String.format("Binary operation %s isn't supported for %s and %s", operation, left, right));
		}
	}

	private static class TypeMismatch extends SWPException {
		public TypeMismatch(Type expected, Type actual, Node node){
			super(String.format("Node %s has type %s but type %s was expected", node, actual, expected));
		}
	}

	private static enum UnaryOperation {
		NEGATE, INVERT
	}

	private static enum BinaryOperation {
		PLUS, MINUS, POW, DIVIDE, MULTIPLY,
		GREATER, LOWER, EQUALS, UNEQUALS,
		AND, OR
	}

	private static abstract class Node extends BaseAST implements Serializable {

		@Override
		public List<Token> getMatchedTokens() {
			return new ArrayList<>();
		}

		public Node unaryOperation(UnaryOperation op){
			throw new UnsupportedUnaryOperation(op, this);
		}

		public Node binaryOperation(BinaryOperation op, BaseAST otherNode){
			return binaryOperation(op, (NumberNode)otherNode);
		}

		public abstract Node binaryOperation(BinaryOperation op, Node otherNode);

		public boolean toBoolean(){
			return false;
		}

		public abstract Type getType();

		public boolean hasType(Type type){
			return getType() == type;
		}

		public boolean isInteger(){
			return hasType(Type.INTEGER);
		}

		public Node expectType(Type type){
			if (!hasType(type)){
				throw new TypeMismatch(type, getType(), this);
			}
			return this;
		}
	}

	private static class NumberNode extends Node {

		public final BigInteger num;

		public NumberNode(boolean num) {
			this.num = BigInteger.valueOf(num ? 1 : 0);
		}

		public NumberNode(long num) {
			this.num = BigInteger.valueOf(num);
		}

		public NumberNode(BigInteger num) {
			this.num = num;
		}

		@Override
		public List<Token> getMatchedTokens() {
			return new ArrayList<>();
		}

		@Override
		public Node unaryOperation(UnaryOperation op) {
			switch (op){
				case INVERT:
					return new NumberNode(num.equals(BigInteger.ZERO));
				case NEGATE:
					return new NumberNode(num.negate());
				default:
					throw new UnsupportedUnaryOperation(op, this);
			}
		}

		@Override
		public Node binaryOperation(BinaryOperation op, Node otherNode) {
			switch (op){
				case AND:
					return new NumberNode(toBoolean() && otherNode.toBoolean());
				case OR:
					return new NumberNode(toBoolean() || otherNode.toBoolean());
			}
			otherNode.expectType(Type.INTEGER);
			BigInteger otherNum = ((NumberNode)otherNode).num;
			switch (op){
				case PLUS:
					return new NumberNode(num.add(otherNum));
				case MINUS:
					return new NumberNode(num.subtract(otherNum));
				case POW:
					return new NumberNode(num.pow(otherNum.intValue()));
				case DIVIDE:
					return new NumberNode(num.divide(otherNum));
				case MULTIPLY:
					return new NumberNode(num.multiply(otherNum));
				case GREATER:
					return new NumberNode(num.compareTo(otherNum) == 1);
				case LOWER:
					return new NumberNode(num.compareTo(otherNum) == -1);
				case EQUALS:
					return new NumberNode(num.compareTo(otherNum) == 0);
				case UNEQUALS:
					return new NumberNode(num.compareTo(otherNum) != 0);
				default:
					throw new UnsupportedBinaryOperation(op, this, otherNode);
			}
		}

		@Override
		public boolean toBoolean() {
			return !num.equals(BigInteger.ZERO);
		}

		@Override
		public Type getType() {
			return Type.INTEGER;
		}

		@Override
		public String toString() {
			return "" + num;
		}
	}

	private static class StringNode extends Node {

		public final String value;

		public StringNode(boolean value) {
			this.value = value ? "true" : "false";
		}

		public StringNode(BigInteger num) {
			this.value = num.toString();
		}

		public StringNode(String value) {
			this.value = value;
		}

		@Override
		public List<Token> getMatchedTokens() {
			return new ArrayList<>();
		}

		@Override
		public Node binaryOperation(BinaryOperation op, Node otherNode) {
			String otherValue = otherNode.toString();
			switch (op){
				case PLUS:
					return new StringNode(value + otherValue);
				case MINUS:
					return new StringNode(value.replace(otherValue, ""));
				case AND:
					return new NumberNode(toBoolean() && otherNode.toBoolean());
				case OR:
					return new NumberNode(toBoolean() || otherNode.toBoolean());
				default:
					throw new UnsupportedBinaryOperation(op, this, otherNode);
			}
		}

		@Override
		public boolean toBoolean() {
			return !value.isEmpty();
		}

		@Override
		public Type getType() {
			return Type.STRING;
		}

		@Override
		public String toString() {
			return value;
		}
	}

	private static class NoSuchVariable extends SWPException {
		public NoSuchVariable(String name){
			super(String.format("There's no such variable %s", name));
		}
	}
	public class Context implements Serializable {
		private Map<String, Node> variables = new HashMap<>();

		public Context put(String name, Node value){
			variables.put(name, value);
			return this;
		}

		public Node get(String name){
			if (!variables.containsKey(name)){
				throw new NoSuchVariable(name);
			}
			return variables.get(name);
		}
	}

}

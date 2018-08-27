package swp.grammar;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;

import swp.SWPException;
import swp.lexer.*;
import swp.lexer.automata.*;
import swp.parser.lr.*;
import swp.util.*;

/**
 * Allows parsing of grammar right hand sides (written in a variant of EBNF):
 *
 * <pre>
 * TOKEN_ID    = [A-Z][A-Z0-9_]*
 * ID          = [a-z][a-z0-9_]*
 * WS          = [\s]+       # ignored
 * COMMENT     = \#[^\\n]*   # ignored
 *
 * statement   = expression | EOF
 * expression  = expr*
 * expr        = term \* | term \? | term \+ | term
 * term        = ID | TOKEN_ID | \( expression \)
 * </pre>
 */
public class ExtGrammarBuilder extends GrammarBuilder implements Serializable {

	private LRParserTable basicParserTable = null;
	private static Table lexerTable;

	public ExtGrammarBuilder(TerminalSet alphabet) {
		super(alphabet);
		initParser();
	}

	private void initParser(){
		if (lexerTable == null) {
			LexerDescriptionParser lexerBuilder = new LexerDescriptionParser();
			String lexerGrammar = "TOKEN_ID = [A-Z]([A-Z0-9_]*)\n" +
					"ID = [a-z]([a-z0-9_]*)\n" +
					"L_BRACE = \\(\n R_BRACE = \\)\n" +
					"OR = \\|; PLUS = \\+; STAR = \\*; WS = [\\s]+; COMMENT = \\#([^\\n]*); MAYBE = \\?";
			lexerTable = lexerBuilder.eval(lexerGrammar);
		}
		GrammarBuilder b = new GrammarBuilder(lexerTable.terminalSet);
		b.add("statement",  b.orWithActions(
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine("expression", "EOF"),
						list -> list.get(0)
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine(""),
						list -> new ASTNode()
				)
		));

		b.add("expression", b.orWithActions(
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine("expr", "OR", "expression"),
						list -> new OrNode((ASTNode) list.get(0), (ASTNode) list.get(2))
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine("expr"),
						list -> list.get(0)
				)
		));

		b.add("expr", b.orWithActions(
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(new Object[]{"expr", "expression"},
						list -> new CombineNode((ASTNode) list.get(0), (ASTNode) list.get(1))
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine("term", "STAR"),
						list -> new RangeNode((ASTNode)list.get(0), 0, 0, true)
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine("term", "MAYBE"),
						list -> new RangeNode((ASTNode)list.get(0), 0, 1, false)
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine("term", "PLUS"),
						list -> new RangeNode((ASTNode)list.get(0), 1, 0, true)
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine("term"),
						list -> list.get(0)
				)
		));

		b.add("term", b.orWithActions(
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine("ID"),
						list -> new IDNode(list.getMatchedString())
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(b.combine("TOKEN_ID"),
						list -> {
							String tokenName = list.getMatchedString();
							if (!alphabet.isValidTypeName(tokenName)){
								throw new SWPException(String.format("No such token %s in grammar description",
										tokenName));
							}
							return new TokenNode(alphabet.stringToType(tokenName));
						}
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(
						b.combine("L_BRACE", "expression", "R_BRACE"),
						list -> list.get(1)
				)
		));
		Graph.isLALR = true;
		Grammar grammar = b.toGrammar("statement");
		basicParserTable = Graph.createFromGrammar(grammar).toParserTable();
		//Utils.repl(input -> createLexer(input));
		//new DiffGraph(grammar, "/tmp/test_").createMP4(1);
		//Utils.parserRepl(s -> addRule("A", s));
	}

	/**
	 * Add a rule
	 *
	 * @param nonTerminal left hand side of the rule
	 * @param rule right hand side of the rule (@see ExtGrammarBuilder class comment for a format description)
	 * @return self
	 */
	public ExtGrammarBuilder addRule(String nonTerminal, String rule){
		//System.out.println(nonTerminal + " → " + rule);
		Lexer lex = createLexer(rule);
		//Utils.repl(this::createLexer);
		try {
			LRParser parser = new LRParser(basicParserTable.grammar, lex, basicParserTable);
			Object[] rhs = ((ASTNode) parser.parse()).toObjectArr(this);
			add(nonTerminal, rhs);
		} catch (Exception ex){
			System.err.println(String.format("Error at rule %s → %s", nonTerminal, rule));
			throw ex;
		}
		return this;
	}

	public ExtGrammarBuilder addEitherRule(String nonTerminal, String... subNonTerminals){
		//System.out.println(nonTerminal + " → " + rule);
		for (String rule : subNonTerminals){
			addRule(nonTerminal, rule).action(asts -> asts.get(0));
		}
		return this;
	}


	/**
	 * Add a rule with an action
	 *
	 * @param nonTerminal left hand side of the rule
	 * @param rule right hand side of the rule
	 * @see ExtGrammarBuilder format description
	 * @param action action to execute for this rule
	 * @return self
	 */
	public ExtGrammarBuilder addRule(String nonTerminal, String rule, SerializableFunction<ListAST, BaseAST> action){
		addRule(nonTerminal, rule);
		action(action);
		return this;
	}

	/**
	 * Add precedence ordered operators
	 *
	 * @param nonTerminal left hand side of the top most rule
	 * @param endNonTerminal non terminal that is used in the lowest rule
	 * @param builder uses an Operators object to build up the operator precendence layers
	 * @return self
	 */
	public ExtGrammarBuilder addOperators(String nonTerminal, String endNonTerminal, Consumer<Operators> builder){
		Operators operators = new Operators(nonTerminal, endNonTerminal, alphabet);
		builder.accept(operators);
		operators.accept(this);
		return this;
	}

	public static enum LEFT_OR_RIGHT {
		LEFT, RIGHT
	}

	/**
	 * Simplifies the operator precedence grammar construction.
	 * The first declared layer has the lowest precedence
	 */
	public static class Operators implements Serializable {

		private class Layer implements Serializable {
			List<OperatorInfo> ops = new ArrayList<>();
			SerializableBiFunction<ListAST, Integer, BaseAST> binaryAction;
			SerializableBiFunction<ListAST, Integer, BaseAST> unaryAction;
			boolean closed = false;

			Layer(SerializableBiFunction<ListAST, Integer, BaseAST> binaryAction,
			             SerializableBiFunction<ListAST, Integer, BaseAST> unaryAction) {
				this.ops = ops;
				this.binaryAction = binaryAction;
				this.unaryAction = unaryAction;
			}
		}

		private abstract class OperatorInfo implements Serializable {

		}

		private class StandardOperatorInfo extends OperatorInfo implements Serializable {

			final int terminal;
			final boolean isBinary;
			final LEFT_OR_RIGHT associativity;

			public StandardOperatorInfo(int terminal, boolean isBinary, LEFT_OR_RIGHT associativity) {
				this.terminal = terminal;
				this.isBinary = isBinary;
				this.associativity = associativity;
			}
			public String toString(){
				String term = terminalSet == null ? "" + terminal : terminalSet.typeToString(terminal);
				if (isBinary){
					return String.format("Binary operator %s (%s associative)", term,
							associativity == LEFT_OR_RIGHT.LEFT ? "left" : "right");
				} else {
					return String.format("Unary operator %s (%s hand side)", term,
							associativity == LEFT_OR_RIGHT.LEFT ? "left" : "right");
				}
			}
		}

		private class CustomOperatorInfo extends OperatorInfo implements Serializable {

			final String rule;
			final SerializableFunction<ListAST, BaseAST> action;

			public CustomOperatorInfo(String rule, SerializableFunction<ListAST, BaseAST> action) {
				this.rule = rule;
				this.action = action;
			}

			@Override
			public String toString() {
				return "Custom operator with rule " + rule;
			}
		}

		private String startNonTerminal;
		private String endNonTerminal;
		private TerminalSet terminalSet;
		private Stack<Layer> layers = new Stack<>();
		private SerializableBiFunction<ListAST, Integer, BaseAST> defaultBinaryAction = null;
		private SerializableBiFunction<ListAST, Integer, BaseAST> defaultUnaryAction = null;

		public Operators(String startNonTerminal, String endNonTerminal, TerminalSet terminalSet){
			this.startNonTerminal = startNonTerminal;
			this.endNonTerminal = endNonTerminal;
			this.terminalSet = terminalSet;
			addNewLayer();
		}

		private Operators binary(int terminal, LEFT_OR_RIGHT associativity){
			addOperator(new StandardOperatorInfo(terminal, true, associativity));
			return this;
		}

		/**
		 * Add a binary operator
		 * @return self
		 */
		public Operators binary(int terminal){
			return binary(terminal, LEFT_OR_RIGHT.LEFT);
		}

		/**
		 * Add binary left associative operators (allows enum elements as input)
		 *
		 * @return self
		 */
		public <E extends Enum<E>> Operators binary(E... terminals){
			for (E e : terminals) {
				binary(e.ordinal());
			}
			return this;
		}

		/**
		 * Add binary left associative operators (allows enum elements as input) and close the precedence layer.
		 *
		 * @return self
		 */
		public <E extends Enum<E>> Operators binaryLayer(E... terminals){
			closeLayer();
			binary(terminals);
			closeLayer();
			return this;
		}

		/**
		 * Add binary right associative operator
		 *
		 * @return self
		 */
		public Operators binaryRightAssociative(int terminal){
			return binary(terminal, LEFT_OR_RIGHT.RIGHT);
		}

		/**
		 * Add binary right associative operators (allows enum elements as input)
		 *
		 * @return self
		 */
		public <E extends Enum<E>> Operators binaryRightAssociative(E... terminals){
			for (E terminal : terminals) {
				binaryRightAssociative(terminal.ordinal());
			}
			return this;
		}

		/**
		 * Add binary right associative operators (allows enum elements as input) and close the precedence layer.
		 *
		 * @return self
		 */
		public <E extends Enum<E>> Operators binaryRightAssociativeLayer(E... terminals){
			closeLayer();
			binaryRightAssociative(terminals);
			return closeLayer();
		}

		/**
		 * Add unary operator
		 *
		 * @param position does the operator stay in front of (left) or after (right) the inner non terminal
		 * @return self
		 */
		public Operators unary(int terminal, LEFT_OR_RIGHT position){
			addOperator(new StandardOperatorInfo(terminal, false, position));
			return this;
		}

		/**
		 * Add unary operator (allows enum elements as input)
		 *
		 * @param position does the operator stay in front of (left) or after (right) the inner non terminal
		 * @return self
		 */
		public <E extends Enum<E>> Operators unary(E terminal, LEFT_OR_RIGHT position){
			return unary(terminal.ordinal(), position);
		}

		/**
		 * Add unary operator (allows enum elements as input)
		 *
		 * @param position does the operator stay in front of (left) or after (right) the inner non terminal
		 * @return self
		 */
		public <E extends Enum<E>> Operators unaryLayer(LEFT_OR_RIGHT position, E... terminals){
			closeLayer();
			for (E terminal : terminals) {
				unary(terminal.ordinal(), position);
			}
			closeLayer();
			return this;
		}

		/**
		 * Add unary operator (allows enum elements as input)
		 *
		 * @return self
		 */
		public <E extends Enum<E>> Operators unaryLayerLeft(E... terminals){
			closeLayer();
			for (E terminal : terminals) {
				unary(terminal.ordinal(), LEFT_OR_RIGHT.LEFT);
			}
			closeLayer();
			return this;
		}

		/**
		 * Add unary operator (allows enum elements as input)
		 *
		 * @return self
		 */
		public <E extends Enum<E>> Operators unaryLayerRight(E... terminals){
			closeLayer();
			for (E terminal : terminals) {
				unary(terminal.ordinal(), LEFT_OR_RIGHT.RIGHT);
			}
			closeLayer();
			return this;
		}

		/**
		 * Add a custom rule.
		 *
		 * In the rule string "$current" is replaced with the current layer's non terminal
		 * and "$next" with the one of the next layer (with higher precedence).
		 *
		 * @param rule custom rule string ()
		 * @param action action executed for the rule
		 * @see ExtGrammarBuilder format description
		 * @return self
		 */
		public Operators custom(String rule, SerializableFunction<ListAST, BaseAST> action){
			addOperator(new CustomOperatorInfo(rule, action));
			return this;
		}

		/**
		 * Add a custom rule.
		 *
		 * In the rule string "$current" is replaced with the current layer's non terminal
		 * and "$next" with the one of the next layer (with higher precedence).
		 *
		 * @param rule custom rule string
		 * @see ExtGrammarBuilder format description
		 * @return self
		 */
		public Operators custom(String rule){
			addOperator(new CustomOperatorInfo(rule, (x) -> x));
			return this;
		}

		/**
		 * Close a precedence layer
		 *
		 * @return self
		 */
		public Operators closeLayer(){
			return closeLayer(null, null);
		}


		/**
		 * Close a precedence layer
		 *
		 * @param binaryAction default action for binary operators in the current precedence layer
		 * @return self
		 */
		public Operators closeLayer(SerializableBiFunction<ListAST, Integer, BaseAST> binaryAction) {
			return closeLayer(binaryAction, null);
		}

		/**
		 * Close a precedence layer
		 *
		 * @param binaryAction default action for binary operators in the current precedence layer
		 * @param unaryAction default action for unary operators in the current precedence layer
		 * @return self
		 */
		public Operators closeLayer(SerializableBiFunction<ListAST, Integer, BaseAST> binaryAction,
		                            SerializableBiFunction<ListAST, Integer, BaseAST> unaryAction){
			if (layers.peek().closed){
				return this;
			}
			layers.peek().binaryAction = binaryAction;
			layers.peek().unaryAction = unaryAction;
			layers.peek().closed = true;
			return this;
		}

		/**
		 * Set the default action for binary operators
		 *
		 * @param binaryAction action
		 * @return self
		 */
		public Operators defaultBinaryAction(SerializableBiFunction<ListAST, Integer, BaseAST> binaryAction){
			this.defaultBinaryAction = binaryAction;
			return this;
		}

		/**
		 * Set the default action for unary operators
		 *
		 * @param unaryAction action
		 * @return self
		 */
		public Operators defaultUnaryAction(SerializableBiFunction<ListAST, Integer, BaseAST> unaryAction){
			this.defaultUnaryAction = unaryAction;
			return this;
		}

		private void addNewLayer(){
			layers.add(new Layer(null, null));
		}

		private void addOperator(OperatorInfo op){
			if (layers.peek().closed){
				addNewLayer();
			}
			layers.peek().ops.add(op);
		}

		private void accept(ExtGrammarBuilder builder) {
			builder.addRule(startNonTerminal, nonTerminalForLayer(0));
			for (int layerId = 0; layerId < layers.size(); layerId++) {
				String layerNonTerminal = nonTerminalForLayer(layerId);
				String nextLayerNonTerminal = layerId == layers.size() - 1 ? endNonTerminal : nonTerminalForLayer(layerId + 1);
				Layer layer = layers.get(layerId);
				for (OperatorInfo operatorInfo : layer.ops) {
					if (operatorInfo instanceof StandardOperatorInfo){
						accept(builder, (StandardOperatorInfo)operatorInfo, layerNonTerminal, nextLayerNonTerminal, layer);
					} else {
						accept(builder, (CustomOperatorInfo)operatorInfo, layerNonTerminal, nextLayerNonTerminal, layer);
					}

				}
				builder.addRule(layerNonTerminal, nextLayerNonTerminal);
			}
		}

		private void accept(final ExtGrammarBuilder builder, final StandardOperatorInfo op,
		                    String layerNonTerminal, String nextLayerNonTerminal,
		                    Layer layer){
			final SerializableBiFunction<ListAST, Integer, BaseAST> binaryAction =
					layer.binaryAction == null ? defaultBinaryAction : layer.binaryAction;
			final SerializableBiFunction<ListAST, Integer, BaseAST> unaryAction =
					layer.unaryAction == null ? defaultUnaryAction : layer.unaryAction;
			final SerializableFunction<ListAST, BaseAST> normBinaryAction = (asts) -> {
				return binaryAction.apply(asts, op.terminal);
			};
			final SerializableFunction<ListAST, BaseAST> normUnaryAction = (asts) -> {
				return unaryAction.apply(asts, op.terminal);
			};
			String opName = builder.getTerminalSet().typeToString(op.terminal);
			if (op.isBinary){
				if (binaryAction == null){
					throw new SWPException("No binary action given");
				}
				switch (op.associativity){
					case LEFT:
						builder.addRule(layerNonTerminal,
								String.format("%s %s %s", layerNonTerminal, opName, nextLayerNonTerminal),
								normBinaryAction);
						break;
					case RIGHT:
						builder.addRule(layerNonTerminal,
								String.format("%s %s %s", nextLayerNonTerminal, opName, layerNonTerminal),
								normBinaryAction);
				}
			} else {
				if (unaryAction == null){
					throw new SWPException("No unary action given");
				}
				switch (op.associativity){
					case LEFT:
						builder.addRule(layerNonTerminal, opName + " " + layerNonTerminal, normUnaryAction);
						break;
					case RIGHT:
						builder.addRule(layerNonTerminal, layerNonTerminal + " " + opName, normUnaryAction);
				}
			}
		}

		private void accept(final ExtGrammarBuilder builder, final CustomOperatorInfo op,
		                    String layerNonTerminal, String nextLayerNonTerminal,
		                    Layer layer){
			builder.addRule(layerNonTerminal,
					op.rule.replace("$current", layerNonTerminal).replace("$next", nextLayerNonTerminal),
					op.action);
		}

		private String nonTerminalForLayer(int layerId){
			return startNonTerminal + "__p" + precedenceForLayer(layerId);
		}

		private int precedenceForLayer(int layerId){
			return layers.size() - layerId;
		}

		public String toPrettyString() {
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < layers.size(); i++) {
				Layer layer = layers.get(i);
				builder.append(String.format("Layer %d:", precedenceForLayer(i)));
				for (OperatorInfo operatorInfo : layer.ops) {
					builder.append("\n\t");
					builder.append(operatorInfo.toString());
				}
			}
			return builder.toString();
		}
	}

	private Lexer createLexer(String input){
		return new AutomatonLexer(lexerTable, input, new int[]{}, new String[]{"COMMENT", "WS"});
	}

	private RulesForNonTerminal create(String nonTerminal){
		return new RulesForNonTerminal(nonTerminal);
	}

	public TerminalSet getTerminalSet(){
		return alphabet;
	}

	private class RulesForNonTerminal {
		private final String nonTerminal;

		public RulesForNonTerminal(String nonTerminal) {
			this.nonTerminal = nonTerminal;
		}

		public void rule(String rule){
			addRule(nonTerminal, rule);
		}

		public void rule(String rule, SerializableFunction<ListAST, BaseAST> action){
			addRule(nonTerminal, rule);
			action(action);
		}
	}

	public static class ASTNode extends BaseAST {

		public Object[] toObjectArr(GrammarBuilder builder){
			return new Object[]{""};
		}

		@Override
		public List<Token> getMatchedTokens() {
			return null;
		}
	}

	public static class BinaryASTNode extends ASTNode {
		public final ASTNode left;
		public final ASTNode right;

		public BinaryASTNode(ASTNode left, ASTNode right){
			this.left = left;
			this.right = right;
		}
	}

	public static class OrNode extends BinaryASTNode {

		public OrNode(ASTNode left, ASTNode right) {
			super(left, right);
		}

		@Override
		public Object[] toObjectArr(GrammarBuilder builder) {
			return builder.or(left.toObjectArr(builder), right.toObjectArr(builder));
		}
	}

	public static class CombineNode extends BinaryASTNode {

		public CombineNode(ASTNode left, ASTNode right) {
			super(left, right);
		}

		@Override
		public Object[] toObjectArr(GrammarBuilder builder) {
			return builder.combine(left.toObjectArr(builder), right.toObjectArr(builder));
		}
	}

	public static class RangeNode extends ASTNode {
		public final ASTNode child;
		public final int start;
		public final int end;
		public final boolean infEnd;

		public RangeNode(ASTNode child, int start, int end, boolean infEnd) {
			this.child = child;
			this.start = start;
			this.end = end;
			this.infEnd = infEnd;
		}

		@Override
		public Object[] toObjectArr(GrammarBuilder builder) {
			if (infEnd){
				return builder.minimal(start, child.toObjectArr(builder));
			} else {
				if (start == 0 && end == 1){
					return builder.maybe(child.toObjectArr(builder));
				}
				throw new NotImplementedException();
			}
		}
	}

	public static class IDNode extends ASTNode {
		public final String id;

		public IDNode(String id) {
			this.id = id;
		}

		@Override
		public Object[] toObjectArr(GrammarBuilder builder) {
			return builder.combine(id);
		}
	}

	public static class TokenNode extends ASTNode {
		public final int tokenID;

		public TokenNode(int tokenID){
			this.tokenID = tokenID;
		}

		@Override
		public Object[] toObjectArr(GrammarBuilder builder) {
			return builder.combine(tokenID);
		}
	}
}

package swp.parser.examples;

import swp.grammar.ExtGrammarBuilder;
import swp.parser.lr.Generator;
import swp.util.Utils;

/**
 * Example of an implementation of a parser for a simple language.
 */
public class Fui {

	private Generator generator;

	private static enum LexerTerminal implements Generator.LexerTerminalEnum {
		EOF(""),
		FUN("fun"),
		LET("let"),
		RET("ret"),
		RETURN("return"),
		IF("if"),
		WHILE("while"),
		ELSE("else"),
		TRUE("true"),
		FALSE("false"),
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
		OR("\\|\\|"),
		LPAREN("\\("),
		RPAREN("\\)"),
		QUESTION_MARK("\\?"),
		LRBK("\\;|\\n"),
		NUMBER("\\d+"),
		STRING("\"[^\"]*\""),
		ID("$?[A-Za-z_][A-Za-z0-9_]*"),
		WS("[\\s]"),
		COMMENT("\\#([^\\n]*)"),
		LCURLY("\\{"),
		RCURLY("\\}"),
		COLON(":"),
		COMMA("[,]");

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

	public static void main(String[] args) {
		Generator generator = Generator.getCachedIfPossible("fui", LexerTerminal.class, new String[]{"WS", "COMMENT"},
				(builder) -> {
			builder.addRule("program", "block")
					.addRule("program", "")
					.addRule("expr", "LCURLY block RCURLY")
					.addRule("expr", "LCURLY RCURLY")
					.addRule("block", "expr (LRBK expr)*");
					builder.addOperators("expr", "f", operators -> {
						operators
								.custom("$current QUESTION_MARK $next COLON $next")
								.custom("lexpr EQUAL_SIGN $current")
								.defaultBinaryAction((ast, integer) -> ast)
								.defaultUnaryAction((ast, integer) -> ast)
								.binaryLayer(LexerTerminal.OR)
								.binaryLayer(LexerTerminal.AND)
								.binaryLayer(LexerTerminal.EQUALS, LexerTerminal.UNEQUALS)
								.binaryLayer(LexerTerminal.LOWER, LexerTerminal.GREATER)
								.binaryLayer(LexerTerminal.PLUS, LexerTerminal.MINUS)
								.binaryLayer(LexerTerminal.MULTIPLY, LexerTerminal.DIVIDE)
								.binaryRightAssociativeLayer(LexerTerminal.POW)
								.unaryLayer(ExtGrammarBuilder.LEFT_OR_RIGHT.RIGHT, LexerTerminal.INVERT);
					})
							.addRule("lexpr", "LET ID")
							.addRule("lexpr", "ID")
							.addRule("f", "LPAREN expr RPAREN", asts -> asts.get(1))
							.addRule("f", "MINUS LPAREN expr RPAREN")
							.addRule("f", "literal")
							.addRule("f", "condition")
							.addRule("f", "loop")
							.addRule("f", "function")
							.addRule("f", "return")
							.addRule("f", "funccall")

							.addRule("literal", "(PLUS | MINUS)? NUMBER")
							.addRule("literal", "STRING")
							.addRule("literal", "ID")
							.addRule("literal", "boolean")
							.addRule("boolean", "TRUE")
							.addRule("boolean", "FALSE")

							.addRule("condition", "IF LPAREN expr RPAREN expr elsepart?")
							.addRule("elsepart", "ELSE expr")

							.addRule("loop", "WHILE LPAREN expr RPAREN expr")

							.addRule("function", "FUN ID? LPAREN paramlist RPAREN expr")
							.addRule("paramlist", "ID (COMMA ID)*")
							.addRule("paramlist", "")

							.addRule("funccall", "expr LPAREN arglist RPAREN")
							.addRule("arglist", "expr (COMMA expr)*")
							.addRule("arglist", "")

							.addRule("return", "RET returnresult")
							.addRule("return", "RETURN returnresult")
							.addRule("returnresult", "expr")
							.addRule("returnresult", "");


		}, "program");
		//Utils.repl(s -> generator.createLexer(s));
		Utils.parserRepl(s -> generator.parse(s).toPrettyString());
	}

}

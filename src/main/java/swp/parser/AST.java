package swp.parser;

import java.util.List;

import swp.grammar.NonTerminal;
import swp.lexer.Token;

/**
 * Created by parttimenerd on 15.05.16.
 */
public class AST {

	public static class NonTerminalNode extends AST {

		public final NonTerminal nonTerminal;
		public final List<AST> children;

		public NonTerminalNode(NonTerminal nonTerminal, List<AST> children) {
			this.nonTerminal = nonTerminal;
			this.children = children;
		}
	}

	public static class TerminalNode extends AST {
		public final Token token;


		public TerminalNode(Token token) {
			this.token = token;
		}
	}
}

package swp.lexer;

import swp.grammar.Terminal;

public class Token {

	public final int id;
	private static int idCounter = 0;

	/**
	 * Type of the token.
	 */
	public final int type;

	/**
	 * Set of terminals the type of this token belongs too.
	 */
	public final TerminalSet terminalSet;

	/**
	 * Matched text.
	 */
	public final String value;

	public final Location location;

	public Token(int type, TerminalSet terminalSet, String value, Location location){
		this.type = type;
		this.terminalSet = terminalSet;
		this.value = value;
		this.location = location;
		this.id = idCounter++;
	}

	@Override
	public String toString() {
		return terminalSet.typeToString(type) + location.toString() + "(" + value + ")";
	}

	public String toSimpleString(){
		return terminalSet.typeToString(type);
	}

	public boolean isTerminal(Terminal terminal){
		return type == terminal.id;
	}
}

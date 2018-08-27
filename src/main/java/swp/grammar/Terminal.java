package swp.grammar;

import java.io.Serializable;

import swp.lexer.TerminalSet;

/**
 * A terminal symbol
 */
public class Terminal extends TerminalOrEpsilon implements Comparable<Symbol>, Serializable {

	/**
	 * Id of this terminal
	 */
	public final int id;

	/**
	 * Set of terminal symbols this terminal belongs to (like the used alphabet)
	 */
	public final TerminalSet terminalSet;


	public Terminal(int id, TerminalSet terminalSet) {
		this.id = id;
		this.terminalSet = terminalSet;
	}

	@Override
	public String toString() {
		return "<" + terminalSet.typeToString(id) + ">";
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public int compareTo(Symbol o) {
		if (!(o instanceof Terminal)){
			return super.compareTo(o);
		}
		return Integer.compare(id, ((Terminal)o).id);
	}
}

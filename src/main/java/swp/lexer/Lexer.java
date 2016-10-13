package swp.lexer;


/**
 * A simple interface for a pull lexer.
 */
public interface Lexer {

	/**
	 * Get the current token (calls next() if no token has been read before).
	 */
	Token cur();

	/**
	 * Read another token and return it.
	 */
	Token next();

	/**
	 * Ignores tokens of the passed type in subsequent readings.
	 */
	void ignore(int tokenType);

	/**
	 * Returns the used terminal set, the end of input token (EOF) can't be ignored.
	 */
	TerminalSet getTerminalSet();
}

package swp.lexer.automata;

import swp.lexer.LexerError;

/**
 * Created by parttimenerd on 03.08.16.
 */
public class AutomatonConstructionError extends LexerError {
	public AutomatonConstructionError(String message) {
		super(null, message);
	}
}

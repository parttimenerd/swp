package swp.util;

import swp.LocatedSWPException;
import swp.lexer.Token;

/**
 * An error thrown after encountering a syntax error
 */
public class ParserError extends LocatedSWPException {

    public ParserError(Token errorToken, String message) {
        super(errorToken, String.format("Error at %s: %s", errorToken.location, message));
    }
}

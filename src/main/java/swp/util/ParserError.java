package swp.util;

import swp.SWPException;
import swp.lexer.Location;
import swp.lexer.Token;

/**
 * An error thrown after encountering a syntax error
 */
public class ParserError extends SWPException {

    public final Location errorLocation;
    public final Token errorToken;

    public ParserError(Token errorToken, String message) {
        super(String.format("Error at %s: %s", errorToken.location, message));
        this.errorToken = errorToken;
        this.errorLocation = errorToken.location;
    }
}

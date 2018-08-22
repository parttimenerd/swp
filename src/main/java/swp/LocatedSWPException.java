package swp;

import swp.lexer.Location;
import swp.lexer.Token;

public class LocatedSWPException extends SWPException {

	public final Token errorToken;
	public final Location errorLocation;

	public LocatedSWPException(Token errorToken, String message) {
		super(message);
		this.errorToken = errorToken;
		this.errorLocation = errorToken.location;
	}
}

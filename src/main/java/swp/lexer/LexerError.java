package swp.lexer;

import java.util.*;

import swp.LocatedSWPException;
import swp.util.Utils;

/**
 * Created by parttimenerd on 03.08.16.
 */
public class LexerError extends LocatedSWPException {

	public LexerError(Token errorToken, String message) {
		super(errorToken, message);
	}

	public static LexerError create(Token errorToken, Collection<Integer> expectedTokens){
		StringBuilder builder = new StringBuilder();
		List<Integer> list = new ArrayList<>();
		list.addAll(expectedTokens);
		builder.append(errorToken.terminalSet.typesToString(list));
		String errorTokenStr = "";
		if (errorToken.type >= Utils.MIN_CHAR && errorToken.type <= Utils.MAX_CHAR) {
			errorTokenStr = errorToken.terminalSet.typeToString(errorToken.type);
		} else {
			errorTokenStr = "<unsupported character " + Character.toString((char)(errorToken.type + Utils.MIN_CHAR)) + ">";
		}
		return new LexerError(errorToken, String.format("Expected one of %s but got %s at %s", builder.toString(), errorTokenStr, errorToken.location));
	}
}

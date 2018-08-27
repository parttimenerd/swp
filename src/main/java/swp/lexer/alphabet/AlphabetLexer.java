package swp.lexer.alphabet;

import java.io.*;

import swp.lexer.*;

/**
 * Simple lexer the recognizes ASCII letters.
 *
 * It pre processes the whole input to simplify debugging at the expense of memory.
 */
public class AlphabetLexer extends BaseLexer {

	private int line = 1;
	private int column = 0;
	private int cur;

	public AlphabetLexer(String input, int[] ignoredTokenTypes){
		super(new AlphabetTerminals(), input, ignoredTokenTypes);
	}

	public AlphabetLexer(InputStream input, int[] ignoredTokenTypes) {
		super(new AlphabetTerminals(), input, ignoredTokenTypes);
	}

	@Override
	protected Token parseNextToken() {
		try {
			cur = inputStream.read();
		} catch (IOException e) {
			cur = -1;
		}
		if (cur == -1){
			return new Token(0, terminalSet, "", new Location(line, column));
		}
		Token newToken = new Token(cur, terminalSet, Character.toString((char) cur), new Location(line, column));
		if (cur == '\n') {
			line++;
			column = 0;
		} else {
			column++;
		}
		return newToken;
	}
}

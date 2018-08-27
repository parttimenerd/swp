package swp.lexer;

import java.io.*;
import java.util.*;

public abstract class BaseLexer implements Lexer {

	private Token curToken = null;
	protected TerminalSet terminalSet;
	private Set<Integer> ignoredTypes = new HashSet<>();
	protected InputStream inputStream;

	public BaseLexer(TerminalSet terminalSet, String input, int[] ignoredTokenTypes){
		this(terminalSet, new ByteArrayInputStream(input.getBytes()), ignoredTokenTypes);
	}

	public BaseLexer(TerminalSet terminalSet, InputStream input, int[] ignoredTokenTypes) {
		this.terminalSet = terminalSet;
		inputStream = input;
		for (int type : ignoredTokenTypes){
			ignore(type);
		}
	}

	public BaseLexer(TerminalSet terminalSet, int[] ignoredTokenTypes){
		this.terminalSet = terminalSet;
		for (int type : ignoredTokenTypes){
			ignore(type);
		}
	}

	@Override
	public Token cur() {
		if (curToken == null){
			return next();
		}
		return curToken;
	}

	protected abstract Token parseNextToken();

	@Override
	public Token next() {
		if (curToken != null && curToken.type == 0){
			return curToken;
		}
		while (ignoredTypes.contains(((curToken = parseNextToken()).type)));
		return curToken;
	}

	@Override
	public void ignore(int tokenType) {
		assert terminalSet.isValidType(tokenType) && tokenType != 0;
		ignoredTypes.add(tokenType);
	}

	@Override
	public TerminalSet getTerminalSet(){
		return terminalSet;
	}

}

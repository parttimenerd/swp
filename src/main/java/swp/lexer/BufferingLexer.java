package swp.lexer;

import java.io.*;
import java.util.*;

public abstract class BufferingLexer implements Lexer {

	private List<Token> tokens = new ArrayList<>();
	private int index = 0;
	private Token curToken = null;
	protected TerminalSet terminalSet;
	private Set<Integer> ignoredTypes = new HashSet<>();
	protected InputStream inputStream;

	public BufferingLexer(TerminalSet terminalSet, String input, int[] ignoredTokenTypes){
		this(terminalSet, new ByteArrayInputStream(input.getBytes()), ignoredTokenTypes);
	}

	public BufferingLexer(TerminalSet terminalSet, InputStream input, int[] ignoredTokenTypes) {
		this.terminalSet = terminalSet;
		inputStream = input;
		for (int type : ignoredTokenTypes){
			ignore(type);
		}
		initTokens();
	}

	public BufferingLexer(TerminalSet terminalSet, int[] ignoredTokenTypes){
		this.terminalSet = terminalSet;
		for (int type : ignoredTokenTypes){
			ignore(type);
		}
	}

	protected abstract void initTokens();

	protected void addTokenIfNotIgnored(Token token){
		if (!ignoredTypes.contains(token.type)){
			tokens.add(token);
		}
	}

	@Override
	public Token cur() {
		if (curToken == null){
			return next();
		}
		return curToken;
	}

	@Override
	public Token next() {
		if (index < tokens.size()){
			curToken = tokens.get(index++);
		}
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

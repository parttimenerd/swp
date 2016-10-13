package swp.lexer.automata;

import swp.util.Utils;
import swp.lexer.*;
import swp.lexer.alphabet.AlphabetLexer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by parttimenerd on 03.08.16.
 */
public class AutomatonLexer implements Lexer {

	private final AlphabetLexer alphabetLexer;
	private final Table table;
	private Token current;
	private List<Token> lookaheadFromLast = new ArrayList<>();
	private boolean[] ignoredTokens;
	private boolean usesCompressedTable;

	public AutomatonLexer(Table table, AlphabetLexer alphabetLexer) {
		this.alphabetLexer = alphabetLexer;
		this.table = table;
		this.ignoredTokens = new boolean[table.terminalSet.getValidTypes().size()];
		this.usesCompressedTable = table instanceof CompressedTable;
	}

	public AutomatonLexer(Table table, String input, int[] ignoredTokenTypes){
		this(table, new ByteArrayInputStream(input.getBytes()), ignoredTokenTypes);
	}

	public AutomatonLexer(Table table, String input, int[] ignoredTokenTypes, int[] ignoredResultingTokenTypes){
		this(table, new ByteArrayInputStream(input.getBytes()), ignoredTokenTypes);
		for (int i : ignoredResultingTokenTypes){
			ignore(i);
		}
	}

	public AutomatonLexer(Table table, String input, int[] ignoredTokenTypes, String[] ignoredResultingTokenTypes){
		this(table, new ByteArrayInputStream(input.getBytes()), ignoredTokenTypes);
		for (String i : ignoredResultingTokenTypes){
			ignore(table.terminalSet.stringToType(i));
		}
	}

	public AutomatonLexer(Table table, InputStream input, int[] ignoredTokenTypes) {
		this(table, new AlphabetLexer(input, ignoredTokenTypes));
	}

	private Token parseNextToken(){
		CompressedTable compressedTable = null;
		if (usesCompressedTable){
			compressedTable = (CompressedTable)table;
		}
		int currentState = table.initialState;
		StringBuilder builder = new StringBuilder();
		Location location = alphabetLexer.cur().location;
		if (!lookaheadFromLast.isEmpty()){
			location = lookaheadFromLast.get(0).location;
		}
		List<Token> readTokens = new ArrayList<>();
		int rtPosition = 0;
		int lastRtPosition = 0;
		readTokens.addAll(lookaheadFromLast);
		Token lastToken = null;
		while (true){
			int cur;
			if (rtPosition >= readTokens.size()){
				readTokens.add(alphabetLexer.cur());
				alphabetLexer.next();
			}
			cur = readTokens.get(rtPosition).type;
			int prevState = currentState;
			if (cur >= Utils.MIN_CHAR && cur <= Utils.MAX_CHAR) {
				int index = cur;
				if (usesCompressedTable){
					index = compressedTable.tokenTypeTranslations[cur];
				}
				currentState = table.transitions[currentState][index];
			} else {
				currentState = -1;
			}
			builder.appendCodePoint(cur);
			rtPosition++;
			if (currentState == -1){
				lookaheadFromLast = readTokens.subList(lastRtPosition, readTokens.size());
				if (lastToken != null) {
					return lastToken;
				}
				StringBuilder errorBuilder = new StringBuilder();
				int[] row = table.transitions[prevState];
				List<Integer> expected = new ArrayList<>();
				for (int i = 0; i < row.length; i++){
					if (row[i] != -1){
						if (usesCompressedTable){
							for (int c : compressedTable.reverseTranslations.get(i)){
								expected.add(c);
							}
						} else {
							expected.add(i);
						}
					}
				}
				throw LexerError.create(readTokens.get(rtPosition - 1), expected);
			}
			if (table.finalTypes[currentState] != -1){
				lastRtPosition = rtPosition;
				lastToken = new Token(table.finalTypes[currentState], table.terminalSet, builder.toString(), location);
			}
		}
	}

	@Override
	public Token cur() {
		if (current == null){
			next();
		}
		return current;
	}

	@Override
	public Token next() {
		if (current != null && current.id == 0){
			return current;
		}
		do {
			current = parseNextToken();
		} while (current != null && ignoredTokens[current.type]);
		return current;
	}

	@Override
	public void ignore(int tokenType) {
		ignoredTokens[tokenType] = true;
	}

	@Override
	public TerminalSet getTerminalSet() {
		return table.terminalSet;
	}
}

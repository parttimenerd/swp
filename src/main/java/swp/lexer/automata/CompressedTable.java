package swp.lexer.automata;

import swp.lexer.TerminalSet;
import swp.lexer.alphabet.AlphabetTerminals;

import java.util.ArrayList;

/**
 * Clusters classes of terminals together
 */
public class CompressedTable extends Table {

	/**
	 * real token type => token type used to index the table
	 */
	public final int[] tokenTypeTranslations;
	public final ArrayList<ArrayList<Integer>> reverseTranslations;

	public CompressedTable(TerminalSet terminalSet, int[][] transitions, int[] finalTypes, int initialState,
	                       int[] tokenTypeTranslations, ArrayList<ArrayList<Integer>> reverseTranslations) {
		super(terminalSet, transitions, finalTypes, initialState);
		this.tokenTypeTranslations = tokenTypeTranslations;
		this.reverseTranslations = reverseTranslations;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		AlphabetTerminals alphabetTerminals = new AlphabetTerminals();
		for (int state = 0; state < transitions.length; state++){
			if (state != 0){
				builder.append("\n");
			}
			builder.append(String.format("State %3d = %3d {", state, finalTypes[state]));
			int[] row = transitions[state];
			for (int col = 0; col < row.length; col++){
				if (row[col] != -1){
					builder.append(alphabetTerminals.typesToString(reverseTranslations.get(col)));
					builder.append(" => ").append(String.format("%3d  ", row[col]));
				}
			}
			builder.append(" }");
		}
		return builder.toString();
	}

	@Override
	public CompressedTable compress() {
		return this;
	}
}

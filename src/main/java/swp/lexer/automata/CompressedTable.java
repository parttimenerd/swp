package swp.lexer.automata;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import swp.lexer.TerminalSet;
import swp.lexer.alphabet.AlphabetTerminals;

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

	public String toTableClass(List<EnumTableGenerator.TerminalDescription> descriptions,
							   String enumClassName,
							   Path templateFile, String packageName, String className) throws IOException {
		List<String> finalTypesStrings = new ArrayList<>();
		for (int i = 0; i < finalTypes.length; i++) {
			int finalType = finalTypes[i];
			if (finalType == -1){
				finalTypesStrings.add("            null");
			} else {
				finalTypesStrings.add(String.format("            %s.%s", enumClassName,
						descriptions.get(finalType == 0 ? 0 : finalType - 1).name));
			}
		}
		String finalTypesString = String.join(",\n", finalTypesStrings) + "\n";
		String template = new String(Files.readAllBytes(templateFile));
		List<String> tokenTypeTranslationStrs = new ArrayList<>();
		for (int tokenTypeTranslation : tokenTypeTranslations) {
			tokenTypeTranslationStrs.add(tokenTypeTranslation + "");
		}
		String transitionsString = twoDimIntArrayToCode(transitions);
		int[][] reverseTranslationsArr = new int[reverseTranslations.size()][];
		for (int i = 0; i < reverseTranslations.size(); i++) {
			List<Integer> row = reverseTranslations.get(i);
			reverseTranslationsArr[i] = new int[row.size()];
			for (int j = 0; j < row.size(); j++) {
				reverseTranslationsArr[i][j] = row.get(j);
			}
		}
		String reverseTranslationsString = twoDimIntArrayToCode(reverseTranslationsArr);
		template = template.replaceAll("package[^;]+;", String.format("package %s;", packageName))
				.replaceFirst("class [^{\\s]+", String.format("class %s", className))
				.replace("initialState = 1", String.format("initialState = %d", initialState))
				.replaceFirst("null\n", finalTypesString)
				.replaceAll("EnumTemplate", enumClassName)
				.replace("new int[]{}", transitionsString)
				.replace("int[] tokenTypeTranslations = new int[0];",
						String.format("int[] tokenTypeTranslations = new int[]{%s};",
								String.join(", ", tokenTypeTranslationStrs)))
				.replace("new int[]{1}", reverseTranslationsString);
		return template.replace("\t", "    ");
	}

	private String twoDimIntArrayToCode(int[][] twoDimArray){
		List<String> colStrings = new ArrayList<>();
		for (int i = 0; i < twoDimArray.length; i++){
			int[] col = twoDimArray[i];
			List<String> rowStrings = new ArrayList<>();
			for (int j = 0; j < col.length; j++) {
				rowStrings.add(String.format("%d", col[j]));
			}
			String rowString = String.join(", ", rowStrings) + "\n";
			String templ = "            new int[]{\n" +
					"                    " + rowString +
					"            }";
			colStrings.add(templ);
		}
		return String.join(",\n", colStrings) + "\n";
	}
}

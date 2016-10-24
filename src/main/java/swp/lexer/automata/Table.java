package swp.lexer.automata;

import swp.lexer.TerminalSet;
import swp.util.Utils;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A lexer state transition table
 */
public class Table implements Serializable {

	public final TerminalSet terminalSet;
	/**
	 * [current state][character] => next state, -1 for error state
	 */
	public final int[][] transitions;
	/**
	 * [state] => -1: non final state, else terminal id
	 */
	public final int[] finalTypes;

	public final int initialState;

	public Table(TerminalSet terminalSet, int[][] transitions, int[] finalTypes, int initialState) {
		this.terminalSet = terminalSet;
		this.transitions = transitions;
		this.finalTypes = finalTypes;
		this.initialState = initialState;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (int state = 0; state < transitions.length; state++){
			if (state != 0){
				builder.append("\n");
			}
			builder.append(String.format("State %3d = %3d {", state, finalTypes[state]));
			int[] row = transitions[state];
			for (int col = 0; col < row.length; col++){
				if (row[col] != -1){
					builder.append(String.format(" %3s => %3d", Utils.toPrintableRepresentation(Character.toString((char) (col + Utils.MIN_CHAR))), row[col]));
				}
			}
			builder.append(" }");
		}
		return builder.toString();
	}

	/**
	 * Groups all terminals that lead to the same state transitions together.
	 *
	 * Use the tokenTypeTranslations table to convert a "normal" terminal id to it's group terminal id
	 * @return
	 */
	public CompressedTable compress(){
		int newTerminalCounter = 0;
		int[] translations = new int[Utils.MAX_CHAR - Utils.MIN_CHAR + 1];
		ArrayList<ArrayList<Integer>> reverseTranslation = new ArrayList<>();
		TerminalAttribute[] attributes = new TerminalAttribute[translations.length];
		for (int i = 0; i < translations.length; i++){
			attributes[i] = new TerminalAttribute(i);
			translations[i] = -1;
		}
		for (int i = 0; i < translations.length; i++){
			if (translations[i] == -1){
				int newState = newTerminalCounter;
				newTerminalCounter++;
				translations[i] = newState;
				ArrayList<Integer> arr = Utils.makeArrayList(i);
				TerminalAttribute attr = attributes[i];
				for (int j = i + 1; j < translations.length; j++){
					TerminalAttribute otherAttr = attributes[j];
					if (translations[j] == -1 && attr.equals(otherAttr)){
						translations[j] = newState;
						arr.add(j);
					}
				}
				reverseTranslation.add(arr);
			}
		}
		int[][] newTransition = new int[transitions.length][reverseTranslation.size()];
		for (int state = 0; state < transitions.length; state++){
			for (int i = 0; i < reverseTranslation.size(); i++){
				int oldTerminal = reverseTranslation.get(i).get(0);
				newTransition[state][i] = transitions[state][oldTerminal];
			}
		}
		return new CompressedTable(terminalSet, newTransition, finalTypes, initialState, translations, reverseTranslation);
	}

	private class TerminalAttribute {
		public final int terminalRowIndex;
		private int activeStates = 0;
		private int startState = -1;
		private int endState = -1;
		private long product = 1;
		private long sum = 0;

		public TerminalAttribute(int terminalRowIndex) {
			this.terminalRowIndex = terminalRowIndex;
			for (int state = 0; state < transitions.length; state++){
				int nextState = transitions[state][terminalRowIndex];
				if (nextState == -1){
					if (startState != -1 && endState == -1){
						endState = state;
					}
					continue;
				}
				product *= state * nextState;
				sum += state * nextState;
				activeStates++;
				if (startState == -1){
					startState = state;
				}
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof TerminalAttribute)){
				return false;
			}
			TerminalAttribute attr = (TerminalAttribute)obj;
			if (attr.activeStates == activeStates && attr.startState == startState && attr.endState == endState && attr.product == product && attr.sum == sum){
				for (int state = 0; state < transitions.length; state++){
					if (transitions[state][terminalRowIndex] != transitions[state][attr.terminalRowIndex]){
						return false;
					}
				}
				return true;
			}
			return false;
		}

		@Override
		public String toString() {
			return String.format("[id=%3d %3d %3d %3d %3d %3d]", terminalRowIndex, activeStates, startState, endState, product, sum);
		}
	}

	public String toTableClass(Path templateFile, String packageName, String className) throws IOException {
		List<String> finalTypesStrings = new ArrayList<>();
		for (int finalType : finalTypes) {
			finalTypesStrings.add(String.format("            %d", finalType));
		}
		String finalTypesString = String.join(",\n", finalTypesStrings) + "\n";
		List<String> colStrings = new ArrayList<>();
		for (int i = 0; i < transitions.length; i++){
			int[] col = transitions[i];
			List<String> rowStrings = new ArrayList<>();
			for (int j = 0; j < col.length; j++) {
				rowStrings.add(String.format("                    %d", col[j]));
			}
			String rowString = String.join(",\n", rowStrings) + "\n";
			String templ = "            new int[]{\n" +
					rowString +
					"            }";
			colStrings.add(templ);
		}
		String transitionsString = String.join(",\n", colStrings) + "\n";
		String template = new String(Files.readAllBytes(templateFile));
		template = template.replace("package[^;];", String.format("package %s;", packageName))
				.replace("class [^{]{", String.format("class %s {", className))
				.replace("initialState = 1", String.format("initialState = %d", initialState))
				.replace("1\n", finalTypesString)
				.replace("new int[]{}", transitionsString);
		return template.replace("\t", "    ");
	}
}

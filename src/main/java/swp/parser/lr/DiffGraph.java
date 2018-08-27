package swp.parser.lr;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

import swp.grammar.*;
import swp.util.Utils;

/**
 * Created by parttimenerd on 15.07.16.
 */
public class DiffGraph {

	public static enum Mode {
		STATE_LEVEL,
		SITUATION_LEVEL
	}

	public static Mode mode = Mode.SITUATION_LEVEL;

	private Grammar grammar;
	private String imageFilenamePrefix;
	private List<String> pngs = null;

	private Graph finishedGraph;
	private int imageCounter = 0;

	public DiffGraph(Grammar grammar, String imageFilenamePrefix) {
		this.grammar = grammar;
		this.imageFilenamePrefix = imageFilenamePrefix;
	}

	public List<DiffState> loop(){
		State.stateCounter = 0;
		DiffState.stateCounter = 0;
		DiffHistory.currentTime = 0;
		List<DiffState> states = new ArrayList<>();
		grammar.insertStartNonTerminal();
		DiffState startState = new DiffState(grammar);
		Production startProduction = grammar.getProductionOfNonTerminal(grammar.getStart()).get(0);
		startState.add(new Situation(startProduction, new Context(Utils.makeArrayList(grammar.eof))));
		DiffHistory.ItemList list = new DiffHistory.ItemList();
		startState.closure(list);
		list.storeAllItems();
		states.add(startState);
		startState.storeInHistory();
		boolean somethingChanged = true;
		while (somethingChanged){
			somethingChanged = false;
			for (int i = 0; i < states.size(); i++){
				DiffState currentState = (DiffState)states.get(i);
				if (currentState.hasShiftableSituations()){
					Map<Symbol, State> createdStates = currentState.shift();
					List<Symbol> symbols = new ArrayList<>();
					symbols.addAll(createdStates.keySet());
					Collections.sort(symbols);
					for (Symbol shiftSymbol : symbols){
						DiffHistory.ItemList toBeAdded = new DiffHistory.ItemList();
						DiffState createdState = (DiffState)createdStates.get(shiftSymbol);
						DiffHistory.Item blub = createdState.diffHistory.createItemWOTimestamp();
						//currentState.closure(toBeAdded);
						//createdState.closure(toBeAdded);
						boolean somethingChangedInThisRound = false;
						boolean merged = false;
						for (DiffState oldState : states){
							if (oldState.canMerge(createdState, toBeAdded)){
								boolean somethingChangedWhileMerging = oldState.merge(createdState, toBeAdded, currentState, shiftSymbol);
								DiffHistory.Item mergedHistItem = null;
								if (somethingChangedWhileMerging) {
									somethingChanged = true;
									mergedHistItem = oldState.diffHistory.createItemWOTimestamp();
								}
								merged = true;
								boolean store = !currentState.adjacentStates.containsKey(shiftSymbol);
								currentState.adjacentStates.put(shiftSymbol, oldState);
								if(store){
									toBeAdded.add(0, currentState.diffHistory.createItemWOTimestamp());
								}
								if (somethingChangedWhileMerging && mode == Mode.STATE_LEVEL){
									toBeAdded.add(0, mergedHistItem);
								}
								somethingChangedInThisRound = somethingChangedWhileMerging;
								break;
							}
						}
						if (!merged){
							createdState.diffHistory.push(blub);
							if (!currentState.adjacentStates.containsKey(shiftSymbol)) {
								currentState.adjacentStates.put(shiftSymbol, createdState);
								DiffHistory.currentTime--;
								currentState.storeInHistory();
								currentState.diffHistory.last().addUsedSituations(currentState.getUsedSituation(currentState, shiftSymbol));
							}
							states.add(createdState);
							somethingChanged = true;

							//currentState.storeAsUsedInHistory(shiftSymbol);
							states.set(states.size() - 1, createdState);
							somethingChangedInThisRound = true;
						}
						if (somethingChangedInThisRound) {
							toBeAdded.storeAllItems();
						}
					}
				}
			}
		}
		return states;
		/*List<DiffState> newStates = new ArrayList<>();
		int idCounter = 0;
		for (DiffState state : states) {
			newStates.add(state);
			state.id = idCounter++;
			state.diffHistory.improveSituations();
		}
		return newStates;*/
	}

	public List<String> createImages(String imageFormat){
		List<DiffState> states = loop();
		List<String> stateStrings = new ArrayList<>(states.size());
		for (DiffState state : states) {
			stateStrings.add(state.toGraphvizString(0));
		}
		ArrayList<String> filenames = new ArrayList<>();
		filenames.add(toImage(stateStrings, 0, imageFormat));

		for (int time = 1; time < DiffHistory.currentTime + 1; time++){
			boolean atLeastOnStateIsExact = time == DiffHistory.currentTime;
			for (DiffState state : states) {
				if (state.diffHistory.hasExact(time)){
					atLeastOnStateIsExact = true;
					break;
				}
			}
			if (!atLeastOnStateIsExact){
				continue;
			}
			boolean stringsChanged = false;
			for (int j = 0; j < states.size(); j++) {
				DiffState state = states.get(j);
				if (state.diffHistory.storedAt(time) || state.diffHistory.storedAt(time - 1) ||
						state.diffHistory.usedAt(time) || state.diffHistory.usedAt(time -1)){
					String str = state.toGraphvizString(time);
					if (!str.equals(stateStrings.get(j))) {
						stateStrings.set(j, str);
						stringsChanged = true;
					}
				}
			}
			if (stringsChanged) {
				filenames.add(toImage(stateStrings, time, imageFormat));
			}
		}
		return filenames;
	}

	public DiffGraph createPNGs(){
		if (pngs == null){
			pngs = createImages("png");
		}
		return this;
	}

	public DiffGraph createGIF(int delaySeconds){
		return createGIF(delaySeconds, false);
	}

	public DiffGraph createGIF(int delaySeconds, boolean removePNGs) {
		return createGIF(imageFilenamePrefix + ".gif", delaySeconds, removePNGs);
	}

	public DiffGraph createGIF(String filename, int delaySeconds, boolean removePNGs) {
		createPNGs();
		try {
			for (int i = 0; i < pngs.size(); i += 10){
				String tmpFilename = String.format("%s__%05d.gif", imageFilenamePrefix, Math.floorDiv(i, 10));
				execCommand(String.format("convert -delay %d/1 %s0000%d*.png %s", delaySeconds, imageFilenamePrefix,
						Math.floorDiv(i, 10), tmpFilename));
			}
			execCommand(String.format("convert %s__*.gif %s", imageFilenamePrefix, filename));
			if (removePNGs){
				execCommand(String.format("rm %s*.png", imageFilenamePrefix));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return this;
	}

	public DiffGraph createMP4(int delaySeconds){
		return createMP4(delaySeconds, false);
	}

	public DiffGraph createMP4(int delaySeconds, boolean removePNGs) {
		return createMP4(imageFilenamePrefix + ".mp4", delaySeconds, removePNGs);
	}

	public DiffGraph createMP4(String filename, int delaySeconds, boolean removePNGs) {
		createPNGs();
		try {
			if (Files.deleteIfExists(Paths.get(filename)));
			execCommand(String.format("cat %s*.png | ffmpeg -framerate 1/%d -i - -c:v libx264 -r 30 %s",
					imageFilenamePrefix, delaySeconds, filename));
			if (removePNGs) {
				execCommand(String.format("rm %s*.png", imageFilenamePrefix));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return this;
	}

	public DiffGraph removePNGs(){
		try {
			execCommand(String.format("rm %s*.png", imageFilenamePrefix));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return this;
	}

	private String toImage(List<String> stateStrings, int time, String imageFormat) {
		String filename = imageFilenamePrefix + String.format("%06d", time);
		toGraphvizFile(String.join("\n", stateStrings), filename + ".dot");
		try {
			String cmd = "dot -Gmodel=subset -Goverlap=true -Gsize=10,15 -T" + imageFormat + " " + filename + ".dot > " +
					filename + "." + imageFormat;
			System.out.println(cmd);
			Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", cmd});
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String s;
			while ((s = stdInput.readLine()) != null) {
				System.out.println(s);
			}
			Files.delete(Paths.get(filename + ".dot"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return filename + "." + imageFormat;
	}

	private void execCommand(String command) throws IOException {
		System.out.println(command);
		Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		String s;
		while ((s = stdInput.readLine()) != null) {
			System.out.println(s);
		}
	}

	private void toGraphvizFile(String middlePart, String filename){
		Path file = Paths.get(filename);
		try {
			Files.write(file, Utils.makeArrayList(toGraphvizString(middlePart)), Charset.forName("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String toGraphvizString(String middlePart){
		StringBuilder builder = new StringBuilder();
		builder.append("digraph g {\n")
				.append("graph [fontsize=30 labelloc=\"t\" label=\"\" " +
						"splines=true overlap=false rankdir = \"LR\" dpi=\"" +
						Utils.GRAPHVIZ_IMAGE_DPI + "\"]; node [shape=box]\n");
		builder.append(middlePart);
		builder.append("}");
		return builder.toString();
	}
}

package swp.parser.lr;

import swp.grammar.*;
import swp.util.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by parttimenerd on 15.07.16.
 */
public class Graph {

	public static boolean isLALR = true;

	public Grammar grammar;
	public List<State> states;
	public State startState;

	public Graph(Grammar grammar, List<State> states, State startState) {
		this.grammar = grammar;
		this.states = states;
		this.startState = startState;
	}

	public static Graph createFromGrammar(Grammar grammar){
		//Graph.isLALR = isLALR;
		List<State> states = new ArrayList<>();
		grammar.insertStartNonTerminal();
		State startState = new State(grammar);
		Production startProduction = grammar.getProductionOfNonTerminal(grammar.getStart()).get(0);
		startState.add(new Situation(startProduction, new Context(Utils.makeArrayList(grammar.eof))));
		startState.closure();
		states.add(startState);
		boolean somethingChanged = true;
		while (somethingChanged){
			somethingChanged = false;
			for (int i = 0; i < states.size(); i++){
				State currentState = states.get(i);
				if (currentState.hasShiftableSituations()){
					Map<Symbol, State> createdStates = currentState.shift();
					List<Symbol> symbols = new ArrayList<>();
					symbols.addAll(createdStates.keySet());
					try {
					//	Collections.sort(symbols);
					} catch (IllegalArgumentException ex){
						ex.printStackTrace();
						System.err.println(symbols);
						throw ex;
					}
					for (Symbol shiftSymbol : symbols){
						State createdState = createdStates.get(shiftSymbol);
						boolean merged = false;
						for (State oldState : states){
							if (oldState.canMerge(createdState)){
								if (oldState.merge(createdState)) {
									somethingChanged = true;
								}
								merged = true;
								currentState.adjacentStates.put(shiftSymbol, oldState);
								break;
							}
						}
						if (!merged){
							currentState.adjacentStates.put(shiftSymbol, createdState);
							states.add(createdState);
							somethingChanged = true;
						}
					}
				}
			}
		}
		List<State> newStates = new ArrayList<>();
		int idCounter = 0;
		for (State state : states) {
			boolean isUsed = state == startState;
			//for (State state2 : states){
			//	if (state2.adjacentStates.containsValue(state)){
			newStates.add(state);
			state.id = idCounter++;
			//		break;
			//	}
			//}
		}
		return new Graph(grammar, newStates, startState);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (State state : states){
			if (state != startState){
				builder.append("\n–––––––\n");
			}
			builder.append(state.toString());
		}
		return builder.toString();
	}

	public String toGraphvizString(){
		StringBuilder builder = new StringBuilder();
		builder.append("digraph g {\n")
				.append("graph [fontsize=30 labelloc=\"t\" label=\"\" " +
						"splines=true overlap=false rankdir = \"LR\" dpi=\"" +
						Utils.GRAPHVIZ_IMAGE_DPI + "\"]; node [shape=box]\n");
		for (State state : states){
			builder.append(state.toGraphvizString()).append("\n");
		}
		builder.append("}");
		return builder.toString();
	}

	public void toGraphvizFile(String filename){
		Path file = Paths.get(filename);
		try {
			Files.write(file, Utils.makeArrayList(toGraphvizString()), Charset.forName("UTF-8"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void toImage(String filename, String format) {
		toGraphvizFile(filename + ".dot");
		try {
			System.out.println("bash -c ' dot  -T" + format + " " + filename + ".dot > " + filename + "." + format + "'");
			String cmd = "dot -Gsize=10,15 -T" + format + " " + filename + ".dot > " + filename + "." + format;
			Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", cmd});
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String s;
			while ((s = stdInput.readLine()) != null) {
				System.out.println(s);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public LRParserTable toParserTable(){
		LRParserTable table = new LRParserTable(grammar);
		for (State state : states){
			for (Symbol symbol : state.adjacentStates.keySet()){
				State nextState = state.adjacentStates.get(symbol);
				if (symbol instanceof Terminal){
					Terminal terminal = (Terminal)symbol;
					if (terminal.id == grammar.eof.id){
						table.addAccept(state, grammar.eof);
					} else {
						table.addShift(state, terminal, nextState);
					}
				} else {
					NonTerminal nonTerminal = (NonTerminal)symbol;
					table.addGoto(state, nonTerminal, nextState);
				}
			}
			for (Situation situation : state){
				if (!situation.canAdvance() || situation.right.isEmpty()){
					if (situation.left != grammar.getStart()){
						for (Terminal terminal : situation.context){
							table.addReduce(state, terminal, situation);
						}
					}
				}
			}
		}
		return table;
	}

	public ExtLRParserTable toExtParserTable(){
		ExtLRParserTable table = new ExtLRParserTable(toParserTable());
		for (State state : states){
			table.addExtStateInformation(state);
		}
		return table;
	}
}

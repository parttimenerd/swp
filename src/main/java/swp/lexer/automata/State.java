package swp.lexer.automata;

import swp.util.Utils;
import swp.lexer.alphabet.AlphabetTerminals;

import java.util.*;

/**
 * An automaton state.
 */
public class State implements Comparable<State> {

	public static final int EPS = -1;

	public final Automaton automaton;

	public final int id;

	/**
	 * Maps character to neighbor.
	 */
	public Map<Integer, State> neighbors = new HashMap<>();

	public Set<State> epsilonNeighbors = new HashSet<>();

	private boolean isFinal;

	/**
	 * If final state: id of the matched terminal.
	 */
	private int terminalId;

	public State(Automaton automaton, int id) {
		this.automaton = automaton;
		this.id = id;
		this.isFinal = false;
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof State && ((State)obj).id == id;
	}

	@Override
	public String toString() {
		if (true) {
			return "" + id;
		}
		StringBuilder builder = new StringBuilder();
		builder.append("[").append(id);
		if (isFinal){
			builder.append(",").append("final");
		}
		List<Integer> transitionChars = new ArrayList<>(neighbors.keySet());
		Collections.sort(transitionChars);
		for (int c : transitionChars){
			builder.append(",").appendCodePoint(c).append("→").append(neighbors.get(c).id);
		}
		if (!epsilonNeighbors.isEmpty()){
			builder.append(",ε={");
			List<State> ngbs = new ArrayList<>(epsilonNeighbors);
			Collections.sort(ngbs);
			for (int i = 0; i < epsilonNeighbors.size(); i++){
				if (i != 0){
					builder.append(",");
				}
				builder.append(ngbs.get(i));
			}
			builder.append("}");
		}
		builder.append("]");
		return builder.toString();
	}

	public static boolean isEpsilonTransition(int transitionChar){
		return transitionChar == EPS;
	}

	public void addEpsilonNeighbor(State newNeighbor){
		if (automaton.isDeterministic){
			throw new Error("Epsilon transitions aren't allowed in deterministic automata");
		}
		epsilonNeighbors.add(newNeighbor);
	}

	public void addNeighbor(int transitionChar, State newNeighbor){
		if (!neighbors.containsKey(transitionChar)){
			neighbors.put(transitionChar, newNeighbor);
		} else {
			if (neighbors.get(transitionChar).id == id){
				return;
			}
			if (automaton.isDeterministic){
				throw new Error("Duplicate transitions aren't allowed in deterministic automata");
			}
			State otherState = neighbors.get(transitionChar);
			State newState = automaton.createNonFinalState();
			neighbors.put(transitionChar, newState);
			newState.addEpsilonNeighbor(otherState);
			newState.addEpsilonNeighbor(newNeighbor);
		}
	}

	public String toGraphvizString(){
		StringBuilder builder = new StringBuilder();
		builder.append("\"L").append(id).append("\" [shape=");
		if (isFinal){
			builder.append("doublecircle");
		} else {
			builder.append("circle");
		}
		builder.append(",fixedsize=true,width=0.9,label=<<b>").append(id).append("</b>");
		if (this == automaton.initialState){
			builder.append("<br/><i>initial </i>");
		}
		if (isFinal){
			builder.append("<br/><u>").append(Utils.toPrintableRepresentation(automaton.terminalSet
					.typeToString(terminalId))).append("</u>");
		}
		builder.append(">]").append(";\n");
		for (State neighbor : epsilonNeighbors){
			builder.append("L").append(id).append(" ->").append("L").append(neighbor.id).append("[label=\"ε\"];\n");
		}
		Map<State, List<Integer>> transitionLabels = new HashMap<>();
		for (int c : neighbors.keySet()) {
			State neighbor = neighbors.get(c);
			if (!transitionLabels.containsKey(neighbor)) {
				transitionLabels.put(neighbor, new ArrayList<>());
			}
			transitionLabels.get(neighbor).add(c);
		}
		for (State neighbor : transitionLabels.keySet()){
			String label = new AlphabetTerminals().typesToString(transitionLabels.get(neighbor));
			label = label.replace("&", "and");
			label = label.replace(">", "&gt;");
			label = label.replace("<", "&lt;");
			//System.out.println(label);
			builder.append("L").append(id).append(" -> ").append("L").append(neighbor.id).append("[label=<")
					.append(label).append(">];\n");
		}
		return builder.toString();
	}

	@Override
	public int compareTo(State o) {
		return Integer.compare(id, o.id);
	}

	public boolean isFinal(){
		return isFinal;
	}

	public int correspondingTerminalId(){
		return terminalId;
	}

	public void makeFinal(int correspondingTerminal){
		isFinal = true;
		terminalId = correspondingTerminal;
		automaton.finalNodes.put(id, correspondingTerminal);
	}

	public void makeFinal(String correspondingTerminal){
		makeFinal(automaton.terminalSet.stringToType(correspondingTerminal));
	}

	public Map<Integer, Set<State>> transitions(){
		Map<Integer, Set<State>> ret = new HashMap<>();
		Set<State> epsilonReachableStates = epsilonReachableStates();
		epsilonReachableStates.add(this);
		for (State state : epsilonReachableStates){
			for (int s : state.neighbors.keySet()){
				if (!ret.containsKey(s)){
					ret.put(s, new HashSet<>());
				}
				ret.get(s).add(state.neighbors.get(s));
			}
		}
		return ret;
	}

	public Set<State> epsilonReachableStates(){
		Set<State> alreadyVisited = new HashSet<>();
		Stack<State> stack = new Stack<>();
		stack.addAll(epsilonNeighbors);
		alreadyVisited.addAll(epsilonNeighbors);
		while (!stack.isEmpty()){
			State top = stack.pop();
			for (State reachable : top.epsilonNeighbors){
				if (!alreadyVisited.contains(reachable)){
					alreadyVisited.add(reachable);
					stack.push(reachable);
				}
			}
		}
		return alreadyVisited;
	}
}

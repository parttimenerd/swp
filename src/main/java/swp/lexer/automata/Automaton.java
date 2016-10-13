package swp.lexer.automata;

import swp.util.Utils;
import swp.lexer.lr.StringTerminals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static swp.util.Utils.Triple;

public class Automaton {

	/**
	 * Resolve ambiguities by choosing the first possible terminal.
	 */
	public static final boolean RESOLVE_AMBIGUITIES = true;

	private int nodeCounter = 0;

	/**
	 * Is this automaton deterministic?
	 */
	public final boolean isDeterministic;

	public StringTerminals terminalSet = new StringTerminals(new ArrayList<>());

	public State initialState;
	public MetaState initialMetaState;
	public final List<State> states = new ArrayList<>();

	/**
	 * Macro meta nodes can be copied and put at several places in the automaton.
	 */
	private final Map<String, MetaState> macros = new HashMap<>();

	/**
	 * Maps final node id to corresponding terminal id.
	 */
	public Map<Integer, Integer> finalNodes = new HashMap<>();

	public Automaton(){
		this(false);
	}

	/**
	 * Clear all state.
	 */
	public void clear(){
		nodeCounter = 0;
		terminalSet = new StringTerminals(new ArrayList<>());
		states.clear();
		finalNodes.clear();
		initialState = createNonFinalState();
		initialMetaState = new MetaState(initialState);
	}

	public Automaton(boolean isDeterministic){
		this.isDeterministic = isDeterministic;
		initialState = createNonFinalState();
		initialMetaState = new MetaState(initialState);
	}

	public Automaton addTerminal(String name, Function<MetaState, MetaState> builder){
		terminalSet.addTerminal(name);
		MetaState node = new MetaState(createNonFinalState());
		node = builder.apply(node);
		node.getExit().makeFinal(name);
		initialMetaState.getEntry().addEpsilonNeighbor(node.getEntry());
		return this;
	}

	public Automaton addTerminal(String name, MetaState metaState){
		terminalSet.addTerminal(name);
		MetaState node = new MetaState(createNonFinalState());
		node = metaState;
		node.getExit().makeFinal(name);
		initialMetaState.getEntry().addEpsilonNeighbor(node.getEntry());
		return this;
	}

	public Automaton addTerminal(String name, String value){
		return addTerminal(name, mn -> mn.append(value));
	}

	public Automaton addTerminal(String name){
		return addTerminal(name, name);
	}

	public State createNonFinalState(){
		State tmp = new State(this, nodeCounter++);
		states.add(tmp);
		return tmp;
	}

	public State createFinalState(int correspondingTokenId){
		State tmp = new State(this, nodeCounter++);
		tmp.makeFinal(correspondingTokenId);
		states.add(tmp);
		finalNodes.put(tmp.id, correspondingTokenId);
		return tmp;
	}

	public String toGraphvizString(){
		StringBuilder builder = new StringBuilder();
		builder.append("digraph g {\n")
				.append("graph [fontsize=30 labelloc=\"t\" label=\"\" splines=true overlap=false rankdir = \"LR\"];\n");
		builder.append(initialState.toGraphvizString()).append("\n");
		for (State state : states){
			if (state != initialState) {
				builder.append(state.toGraphvizString()).append("\n");
			}
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

	public void toImage(String filename, String format){
		toGraphvizFile(filename + ".dot");
		try {
			System.out.println("bash -c ' dot  -T" + format + " " + filename + ".dot > " + filename + "." + format + "'");
			String cmd = "dot -Gsize=100,150 -T" + format + " " + filename + ".dot > " + filename + "." + format;
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

	public MetaState createMetaNode(){
		return new MetaState(createNonFinalState());
	}

	/**
	 * Makes the powerset construction if the automaton isn't already deterministic.
	 *
	 * @url https://de.wikipedia.org/wiki/Potenzmengenkonstruktion
	 * @url https://binarysculpting.com/2012/02/11/regular-expressions-how-do-they-really-work-automata-theory-for-programmers-part-1/
	 */
	public Automaton toDeterministicVersion(){
		if (isDeterministic){
			return this;
		}
		Set<Set<State>> Q_ = new HashSet<>();
		Set<Set<State>> F_ = new HashSet<>();

		List<Map<Integer, Set<State>>> transitions = new ArrayList<>();
		for (State state : states){
			//System.out.println(state.id + " " + state.transitions());
			transitions.add(state.transitions());
		}
		
		List<Set<State>> epsilonReachable = new ArrayList<>();
		for (State state : states){
			Set<State> set = new HashSet<State>();
			for (State reachableState : state.epsilonReachableStates()) {
				set.add(reachableState);
			}
			epsilonReachable.add(set);
		}

		//System.out.println("transitions = " + transitions);
		//System.out.println("epsilonReachable = " + epsilonReachable);

		Map<Set<State>, Map<Integer, Set<State>>> transitions_ = new HashMap<>();

		Function<Triple<Set<State>, Integer, Set<State>>, Boolean> addTransition = triple -> {
			if (!transitions_.containsKey(triple.first)){
				transitions_.put(triple.first, new HashMap<>());
			}
			Map<Integer, Set<State>> tmp = transitions_.get(triple.first);
			boolean somethingChanged = !tmp.containsKey(triple.second);
			tmp.put(triple.second, triple.third);
			return somethingChanged;
		};

		Function<Set<State>, Set<Integer>> getTransitionChars = powerSet -> {
			Set<Integer> ret = new HashSet<>();
			for (State state : powerSet){
				ret.addAll(transitions.get(state.id).keySet());
			}
			return ret;
		};

		Function<Set<State>, Set<State>> closure = powerSet -> {
			Set<State> ret = new HashSet<>();
			ret.addAll(powerSet);
			for (State state : powerSet){
				ret.addAll(epsilonReachable.get(state.id));
			}
			return ret;
		};

		BiFunction<Set<State>, Integer, Set<State>> gotoFn = (powerSet, character) -> {
			Set<State> ret = new HashSet<State>();
			for (State state : powerSet){
				if (state.neighbors.containsKey(character)) {
					ret.add(state.neighbors.get(character));
				}
			}
			return closure.apply(ret);
		};

		boolean somethingChanged = true;

		Set<State> initialSet = new HashSet<>();
		initialSet.add(initialState);
		initialSet = closure.apply(initialSet);
		Q_.add(initialSet);
		//Set<Set<State>> toLoop = new HashSet<>();
		//toLoop.add(initialSet);

		while (somethingChanged) {
			somethingChanged = false;
			Set<Set<State>> addToQ_ = new HashSet<>();
			for (Set<State> set : Q_){
				for (int s : getTransitionChars.apply(set)){
					Set<State> nextSet = gotoFn.apply(set, s);
					somethingChanged = addTransition.apply(new Triple<>(set, s, nextSet)) || somethingChanged;
					if (!Q_.contains(nextSet) && !addToQ_.contains(nextSet)){
						addToQ_.add(nextSet);
						somethingChanged = true;
					}
				}
			}
			Q_.addAll(addToQ_);
			//System.out.println("Q_ = " + Q_);
			//System.out.println("transitions_ = " + transitions_);
		}
		Map<Set<State>, Integer> setToId = new HashMap<>();
		Automaton automaton = new Automaton(true);
		automaton.states.clear();
		automaton.nodeCounter = 0;
		for (Set<State> q_ : Q_){
			State state = automaton.createNonFinalState();
			setToId.put(q_, state.id);
		}
		for (Set<State> q_ : Q_){
			int correspondingTerminal = -1;
			boolean isFinal = false;
			State state = automaton.states.get(setToId.get(q_));
			for (State part : q_){
				if (finalNodes.containsKey(part.id)){
					if (part.isFinal() && correspondingTerminal != -1 && correspondingTerminal != finalNodes.get(part.id)){
						if (RESOLVE_AMBIGUITIES) {
							isFinal = true;
							if (correspondingTerminal > finalNodes.get(part.id)) {
								correspondingTerminal = finalNodes.get(part.id);
							}
						} else {
							throw new AutomatonConstructionError(
									String.format("Ambiguity at state %s: at least two possible token types: " +
													"%s and %s", state, terminalSet.typeToString(correspondingTerminal),
											terminalSet.typeToString(finalNodes.get(part.id))));
						}
					} else {
						isFinal = true;
						correspondingTerminal = finalNodes.get(part.id);
					}
				}
			}
			if (isFinal){
				state.makeFinal(correspondingTerminal);
			}
		}
		//System.out.println("Q_ = " + Q_);
		//System.out.println("transitions_ = " + transitions_);
		automaton.initialState = automaton.states.get(setToId.get(initialSet));
		automaton.initialMetaState = new MetaState(automaton.initialState);
		automaton.terminalSet = terminalSet;
		for (Set<State> q_ : Q_){
			State state = automaton.states.get(setToId.get(q_));
			//System.out.println(q_);
			if (!transitions_.containsKey(q_)){
				continue;
			}
			Map<Integer, Set<State>> tmp = transitions_.get(q_);
			for (int s : tmp.keySet()){
				State nextState = automaton.states.get(setToId.get(tmp.get(s)));
				state.addNeighbor(s, nextState);
			}
		}
		return automaton;
	}

	public Table toTable(){
		int[][] transitions = new int[states.size()][Utils.MAX_CHAR - Utils.MIN_CHAR + 1];
		int[] finalTypes = new int[states.size()];
		for (State state : states){
			int id = state.id;
			int[] row = transitions[id];
			Arrays.fill(row, -1);
			for (int s : state.neighbors.keySet()){
				row[s - Utils.MIN_CHAR] = state.neighbors.get(s).id;
			}
		}
		Arrays.fill(finalTypes, -1);
		for (int id : finalNodes.keySet()){
			finalTypes[id] = finalNodes.get(id);
		}
		return new Table(terminalSet, transitions, finalTypes, initialState.id);
	}

	/**
	 * Doesn't work properly.
	 *
	 * @url http://www.informatikseite.de/theorie/node57.php
	 */
	public Automaton toMinimalDeterministicVersion() {
		if (!isDeterministic){
			return toDeterministicVersion().toMinimalDeterministicVersion();
		}
		List<Set<Integer>> transitionChars = new ArrayList<>();
		for (State state : states){
			transitionChars.add(state.neighbors.keySet());
		}

		Set<Set<State>> Q_ = new HashSet<>();
		boolean[][] table = new boolean[states.size() - 1][states.size() - 1];
		for (int row = 0; row < states.size() - 1; row++){
			boolean leftFinal = finalNodes.containsKey(row);
			for (int col = 1; col < states.size() - row; col++){
				boolean rightFinal = finalNodes.containsKey(col);
				table[row][col - 1] = leftFinal || rightFinal;
			}
		}
		boolean somethingChanged = true;
		while (somethingChanged){
			somethingChanged = false;
			for (int row = 0; row < states.size() - 1; row++){
				State left = states.get(row);
				for (int col = 1; col < states.size() - row; col++){
					if (table[row][col - 1]){
						continue;
					}
					State right = states.get(col);
					boolean toFinal = false;
					Set<Integer> trans = transitionChars.get(row);
					trans.retainAll(right.neighbors.keySet());
					for (int s : trans){
						State newLeft = left.neighbors.get(s);
						State newRight = right.neighbors.get(s);
						if (newLeft == newRight){
							continue;
						}
						if (!(newLeft.id < states.size() - 1 && newRight.id < states.size() - 1 && newRight.id >= 0)){
							State tmp = newLeft;
							newLeft = newRight;
							newRight = tmp;
						}
						//System.out.println(newLeft.id + "  " + newRight.id);
						if (table[newLeft.id][newRight.id - 1]){
							toFinal = true;
							break;
						}
					}
					if (toFinal){
						somethingChanged = true;
						table[row][col - 1] = true;
					}

				}
			}
		}
		Set<Set<State>> equStates = new HashSet<>();
		Set<State> initialSet = new HashSet<>();
		initialSet.add(initialState);
		equStates.add(initialSet);
		List<Set<State>> equStatesForState = new ArrayList<>(states.size());
		for (int i = 0; i < states.size(); i++){
			Set<State> equ = new HashSet<>();
			equ.add(states.get(i));
			for (int col = 1; col < states.size() - i; col++){
				if (!table[i][col - 1]){
					equ.add(states.get(col));
				}
			}
			for (int row = 0; row < i; row++){
				if (!table[row][i - 1]){
					equ.add(states.get(row));
				}
			}
			equStatesForState.add(equ);
			equStates.add(equ);
		}
		System.out.println("finalNodes = " + finalNodes);
		Map<Set<State>, Integer> setToId = new HashMap<>();
		Automaton automaton = new Automaton(true);
		automaton.states.clear();
		automaton.nodeCounter = 0;
		for (Set<State> q_ : equStates){
			boolean isFinal = false;
			int correspondingTerminal = -1;
			for (State part : q_){
				if (finalNodes.containsKey(part.id)){
					if (isFinal && correspondingTerminal != finalNodes.get(part)){
						throw new Error("Ambiguity");
					}
					isFinal = true;
					correspondingTerminal = finalNodes.get(part.id);
					break;
				}
			}
			State state = automaton.createNonFinalState();
			setToId.put(q_, state.id);
			if (isFinal){
				System.out.println("correspondingTerminal = " + correspondingTerminal);
				state.makeFinal(correspondingTerminal);
			}
		}
		automaton.initialState = automaton.states.get(setToId.get(initialSet));
		automaton.initialMetaState = new MetaState(automaton.initialState);
		automaton.terminalSet = terminalSet;
		//System.out.println("equStates = " + equStates);
		//System.out.println("setToId = " + setToId);
		for (int s : initialState.neighbors.keySet()){
			automaton.initialState.addNeighbor(s, automaton.states.get(setToId.get(equStatesForState.get(initialState.neighbors.get(s).id))));
		}
		for (int i = 0; i < states.size(); i++){
			State state = states.get(i);
			Set<State> equState = equStatesForState.get(i);
			int id = setToId.get(equState);
			Map<Integer, Set<State>> transition = new HashMap<>();
			for (int s : state.neighbors.keySet()){
				transition.put(s, equStatesForState.get(state.neighbors.get(s).id));
			}
			//System.out.println("transition = " + transition);
			State equStateInNAuto = automaton.states.get(id);
			for (int s : transition.keySet()){
				State other = automaton.states.get(setToId.get(transition.get(s)));
				if (!equStateInNAuto.neighbors.containsKey(s)) {
					equStateInNAuto.addNeighbor(s, other);
				}
			}
		}
		//System.out.println(automaton.states);
		return automaton;
	}

	public Automaton addMacro(String name, MetaState metaState){
		macros.put(name, metaState);
		return this;
	}

	public Automaton addMacro(String name, Function<MetaState, MetaState> builder){
		macros.put(name, builder.apply(createMetaNode()));
		return this;
	}

	public MetaState instantiateMacro(String name){
		if (!macros.containsKey(name)){
			throw new AutomatonConstructionError("Unknown macro " + name);
		}
		return macros.get(name).copy();
	}
}

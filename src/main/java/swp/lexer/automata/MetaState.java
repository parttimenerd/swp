package swp.lexer.automata;

import java.util.*;

import swp.util.*;

/**
 * A meta node consists of several nodes but has only one entry and one exit node.
 */
public class MetaState {

	private final Automaton automaton;

	private State entry;
	private State exit;

	public MetaState(State entry, State exit) {
		this.entry = entry;
		this.exit = exit;
		this.automaton = entry.automaton;
	}

	public MetaState(State state){
		this(state, state);
	}

	public MetaState star(){
		State newEntry = automaton.createNonFinalState();
		State newExit = automaton.createNonFinalState();
		newEntry.addEpsilonNeighbor(newExit);
		newEntry.addEpsilonNeighbor(entry);
		newExit.addEpsilonNeighbor(newEntry);
		exit.addEpsilonNeighbor(newExit);
		MetaState ret = new MetaState(newEntry, newExit);
		if (this == automaton.initialMetaState){
			automaton.initialMetaState = ret;
			automaton.initialState = newEntry;
		}
		return ret;
	}

	public MetaState plus(){
		State newEntry = automaton.createNonFinalState();
		newEntry.addEpsilonNeighbor(entry);
		exit.addEpsilonNeighbor(newEntry);
		MetaState ret = new MetaState(newEntry, exit);
		if (this == automaton.initialMetaState){
			automaton.initialMetaState = ret;
			automaton.initialState = newEntry;
		}
		return ret;
	}

	public MetaState range(int min, int max){
		State newEntry = automaton.createNonFinalState();
		State newExit = automaton.createNonFinalState();
		State current = newEntry;

		for (int i = 0; i < min; i++){
			MetaState copy = copy();
			current.addEpsilonNeighbor(copy.entry);
			current = copy.exit;
		}
		current.addEpsilonNeighbor(newExit);
		for (int i = min; i < max; i++){
			MetaState copy = copy();
			current.addEpsilonNeighbor(copy.entry);
			current = copy.exit;
			current.addEpsilonNeighbor(newExit);
		}
		return new MetaState(newEntry, newExit);
	}

	public MetaState range(int min){
		return range(min, min);
	}

	public MetaState rangeOpenEnd(int min){
		return range(min, min).append(copy().star());
	}

	public MetaState append(MetaState otherNode){
		exit.addEpsilonNeighbor(otherNode.entry);
		MetaState ret = new MetaState(entry, otherNode.exit);
		if (this == automaton.initialMetaState){
			automaton.initialMetaState = ret;
		}
		return ret;
	}

	public MetaState append(int character){
		State newState = automaton.createNonFinalState();
		exit.addNeighbor(character, newState);
		MetaState ret = new MetaState(entry, newState);
		if (this == automaton.initialMetaState){
			automaton.initialMetaState = ret;
		}
		return ret;
	}

	public MetaState append(String str){
		MetaState node = this;
		for (int c : str.codePoints().toArray()){
			node = node.append(c);
		}
		return node;
	}

	public MetaState append(int rangeStart, int rangeEnd){
		State newExit = automaton.createNonFinalState();
		for (int i = rangeStart; i <= rangeEnd; i++) {
			exit.addNeighbor(i, newExit);
		}
		MetaState ret = new MetaState(entry, newExit);
		if (this == automaton.initialMetaState){
			automaton.initialMetaState = ret;
		}
		return ret;
	}

	public MetaState appendExcluding(Integer... exclude){
		State newExit = automaton.createNonFinalState();
		Set<Integer> excludedSet = new HashSet<>(Utils.makeArrayList(exclude));
		for (int i = Utils.MIN_CHAR; i < Utils.MAX_CHAR; i++){
			if (!excludedSet.contains(i)) {
				exit.addNeighbor(i, newExit);
			}
		}
		MetaState ret = new MetaState(entry, newExit);
		if (this == automaton.initialMetaState){
			automaton.initialMetaState = ret;
		}
		return ret;
	}

	public MetaState appendExcluding(Character... exclude){
		List<Integer> ints = new ArrayList<>();
		for (char c : exclude){
			ints.add((int)c);
		}
		return appendExcluding(ints.toArray(new Integer[]{}));
	}

	public MetaState appendExcluding(Pair<Integer, Integer>... excludedRanges){
		Map<Integer, Boolean> isExcluded = new HashMap<>(Utils.MAX_CHAR - Utils.MAX_CHAR);
		for (int i = Utils.MIN_CHAR; i < Utils.MAX_CHAR; i++){
			isExcluded.put(i, false);
		}
		for (Pair<Integer, Integer> range : excludedRanges){
			for (int i = range.first; i <= range.second; i++){
				if (i >= Utils.MIN_CHAR && i <= Utils.MAX_CHAR) {
					isExcluded.put(i, true);
				}
			}
		}
		State newExit = automaton.createNonFinalState();
		for (int i = Utils.MIN_CHAR; i < Utils.MAX_CHAR; i++){
			if (!isExcluded.get(i)) {
				exit.addNeighbor(i, newExit);
			}
		}
		MetaState ret = new MetaState(entry, newExit);
		if (this == automaton.initialMetaState){
			automaton.initialMetaState = ret;
		}
		return ret;
	}

	public MetaState appendAllChars(){
		return appendExcluding(new Pair<Integer, Integer>(-1, -1));
	}

	public MetaState appendAllCharsWoEOF(){
		return appendExcluding(new Pair<Integer, Integer>(0, 0));
	}

	public MetaState or(MetaState... otherNodes){
		State newEntry = automaton.createNonFinalState();
		State newExit = automaton.createNonFinalState();
		newEntry.addEpsilonNeighbor(entry);
		for (MetaState otherNode : otherNodes) {
			newEntry.addEpsilonNeighbor(otherNode.entry);
			otherNode.exit.addEpsilonNeighbor(newExit);
		}
		exit.addEpsilonNeighbor(newExit);
		MetaState ret = new MetaState(newEntry, newExit);
		if (this == automaton.initialMetaState){
			automaton.initialMetaState = ret;
			automaton.initialState = newEntry;
		}
		return ret;
	}

	public MetaState or(Character... possibleChars){
		MetaState ret = this;
		for (int c : possibleChars){
			ret = ret.or(create(c));
		}
		return ret;
	}

	public MetaState or(Integer... possibleChars){
		State newEntry = automaton.createNonFinalState();
		State newExit = automaton.createNonFinalState();
		for (int c : possibleChars){
			newEntry.addNeighbor(c, newExit);
		}
		return new MetaState(newEntry, newExit);
	}

	public MetaState maybe(){
		entry.addEpsilonNeighbor(exit);
		return this;
	}

	public MetaState create(){
		return new MetaState(automaton.createNonFinalState());
	}

	public MetaState create(int rangeStart, int rangeEnd){
		return create().append(rangeStart, rangeEnd);
	}

	public MetaState create(String str){
		return create().append(str);
	}

	public MetaState create(int character){
		return create().append(character);
	}

	public State getEntry() {
		return entry;
	}

	public State getExit() {
		return exit;
	}

	public Set<State> getAllStates(){
		Set<State> alreadyVisited = new HashSet<>();
		Stack<State> toVisit = new Stack<>();
		toVisit.push(entry);
		while (!toVisit.isEmpty()){
			State top = toVisit.pop();
			alreadyVisited.add(top);
			toVisit.addAll(top.neighbors.values());
			toVisit.addAll(top.epsilonNeighbors);
			toVisit.removeAll(alreadyVisited);
		}
		return alreadyVisited;
	}

	/**
	 * Copies everything that is "between" entry and exit (and them too).
	 * @return new copy
	 */
	public MetaState copy(){
		Set<State> states = getAllStates();
		Map<State, State> translation = new HashMap<>(); // old state -> new state
		for (State state : states){
			translation.put(state, automaton.createNonFinalState());
			if (state.isFinal()){
				translation.get(state).makeFinal(state.correspondingTerminalId());
			}
		}
		for (State state : states){
			State newVersion = translation.get(state);
			for (int s : state.neighbors.keySet()){
				newVersion.addNeighbor(s, translation.get(state.neighbors.get(s)));
			}
			for (State neighbor : state.epsilonNeighbors) {
				newVersion.addEpsilonNeighbor(translation.get(neighbor));
			}
		}
		return new MetaState(translation.get(entry), translation.get(exit));
	}

	/**
	 * Replaces the current meta node with the copied macro.
	 * @param macro
	 * @return
	 */
	public MetaState use(String macro){
		return automaton.instantiateMacro(macro);
	}
}

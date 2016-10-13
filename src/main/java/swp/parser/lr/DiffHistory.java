package swp.parser.lr;

import swp.grammar.Symbol;
import swp.util.Utils;

import java.util.*;

/**
 * History for a diff state
 */
public class DiffHistory {

	private static final int CACHE_SIZE = 5;

	public static int currentTime = 0;

	private DiffState state;

	public List<Item> items = new ArrayList<>();

	public Set<Integer> changedTimes = new TreeSet<>();

	private Set<Integer> usedTimes = new TreeSet<>();
	public HashMap<Integer, Item> usedAtItems = new HashMap<>();

	private Map<Integer, Item> cache = new TreeMap<>();
	private Deque<Integer> cacheItemInsertionOrder = new ArrayDeque<>();

	public Item lastItem = null;

	public Item firstItem = null;

	public DiffHistory(DiffState state){
		this.state = state;
	}

	public Item createItemWOTimestamp(Situation... usedSituations){
		Item item = createItemWOTimestamp(false);
		item.usedSituations.addAll(Arrays.asList(usedSituations));
		return item;
	}

	public Item createUsedItemWOTimestamp(){
		return createItemWOTimestamp(true);
	}

	public Item createUsedItemWOTimestamp(Symbol... usedTransferSymbols){
		Item item = createItemWOTimestamp(true);
		for (Symbol usedTransferSymbol : usedTransferSymbols) {
			item.addUsedTransferSymbol(usedTransferSymbol);
		}
		return item;
	}

	public Item createItemWOTimestamp(boolean onlyUsedAtThisTime){
		if (lastItem != null && !lastItem.didSomethingChange() && !onlyUsedAtThisTime){
			return new Item(this, -2, onlyUsedAtThisTime, new ArrayList<>(), new ArrayList<>(), new HashMap<>());
		}
		Map<Symbol, State> adjStates = new HashMap<>();
		adjStates.putAll(state.adjacentStates);
		Collections.sort(state.nonClosureItems);
		return new Item(this, -1, onlyUsedAtThisTime, cloneSituationList(state),
				cloneSituationList(state.nonClosureItems), adjStates);
	}

	public void push(Item item){
		int ts = item.timestamp;
		if (ts == -2){
			return;
		}
		if (item.onlyUsedAtThisTime){
			item.timestamp = currentTime - 1;
			usedTimes.add(item.timestamp);
			usedAtItems.put(item.timestamp, item);
			item.mergeSituations(lastItem);
			item.mergeAdjacentStates(lastItem);
		} else {
			item.timestamp = currentTime++;
			item.mergeSituations(lastItem);
			item.mergeAdjacentStates(lastItem);
			lastItem = item;
			if (firstItem == null) {
				firstItem = lastItem;
			}
			changedTimes.add(lastItem.timestamp);
			items.add(lastItem);
			cache.clear();
		}
	}

	public void improveSituations(){
		for (int i = 1; i < items.size(); i++) {
			Item item = items.get(i);
			item.mergeSituations(items.get(i - 1));
			for (Item usedItem : usedAtItems.values()) {
				if (usedItem.timestamp <= item.timestamp){
					item.mergeSituations(usedItem);
				}
			}
		}
	}

	public void push(){
		push(createItemWOTimestamp());
	}

	public Item first(){
		return firstItem;
	}

	public Item last(){
		return lastItem;
	}

	public Item atTime(int time){
		if (items.isEmpty()){
			return null;
		}
		if (firstItem.timestamp > time){
			return null;
		}
		if (!cache.containsKey(time)) {
			Item retItem = lastItem;
			for (int i = 0; i < items.size(); i++) {
				Item item = items.get(i);
				if (item.onlyUsedAtThisTime){
					continue;
				}
				if (item.timestamp == time) {
					retItem = item;
					break;
				} else if (item.timestamp > time) {
					retItem = items.get(i - 1);
					break;
				}
			}
			if (cache.size() >= CACHE_SIZE){
				cache.remove(cacheItemInsertionOrder.pollFirst());
			}
			cacheItemInsertionOrder.addLast(time);
			cache.put(time, retItem);
		}
		return cache.get(time);
	}

	public boolean isVisible(int time){
		return atTime(time) != null;
	}

	public boolean hasExact(int time){
		Item at = atTime(time);
		return at != null && at.timestamp == time;
	}

	public Item justBefore(int time){
		return atTime(time - 1);
	}

	public boolean wasEdgeJustAdded(int time, Symbol transitionSymbol){
		Item current = atTime(time);
		Item justBefore = justBefore(time);
		return current != null && current.adjacentStates.containsKey(transitionSymbol)
				&& justBefore != null && !justBefore.adjacentStates.containsKey(transitionSymbol);
	}

	public boolean isEdgeInvisible(int time, Symbol transitionSymbol){
		Item current = atTime(time);
		return current == null || !current.adjacentStates.containsKey(transitionSymbol);
	}

	public boolean storedAt(int time){
		return changedTimes.contains(time);
	}

	public boolean isSituationInvisible(int time, Situation situation){
		Item current = atTime(time);
		return current == null || !current.containsSituation(situation);
	}

	public boolean wasSituationJustAdded(int time, Situation situation){
		Item current = atTime(time);
		Item justBefore = justBefore(time);
		return current != null && current.containsSituation(situation)
				&& (justBefore == null || !justBefore.containsSituation(situation));
	}

	public boolean isClosureSituation(Situation situation){
		return !last().containsNonClosureSituation(situation);
	}

	public boolean isNonClosureSituation(Situation situation){
		return !isClosureSituation(situation);
	}

	public boolean usedAt(int time){
		return usedTimes.contains(time);
	}

	public Item usedAtItem(int time){
		return usedAtItems.getOrDefault(time, null);
	}

	public static class Item implements Comparable<Integer> {
		public final boolean onlyUsedAtThisTime;
		public final Set<Symbol> usedTransferSymbols = new HashSet<>();
		public final Set<Situation> usedSituations = new HashSet<>();
		public int timestamp;
		public final List<Situation> situations;
		public final List<Situation> nonClosureSituations;
		public final List<Situation> closureSituations = new ArrayList<>();
		public final Map<Symbol, State> adjacentStates;
		public final DiffHistory history;

		public Item(DiffHistory history, int timestamp, boolean onlyUsedAtThisTime, List<Situation> situations, List<Situation> nonClosureSituations,
		            Map<Symbol, State> adjacentStates) {
			this.history = history;
			this.timestamp = timestamp;
			this.onlyUsedAtThisTime = onlyUsedAtThisTime;
			this.situations = situations;
			this.nonClosureSituations = nonClosureSituations;
			for (Situation situation : situations) {
				if (!containsNonClosureSituation(situation)){
					closureSituations.add(situation);
				}
			}
			this.adjacentStates = adjacentStates;
		}

		private boolean containsSituation(Iterable<Situation> situations, Situation situation){
			for (Situation sit : situations) {
				if (sit.canMergeDisregardingContext(situation)){
					return true;
				}
			}
			return false;
		}

		public boolean containsSituation(Situation situation){
			return containsSituation(situations, situation);
		}

		public boolean containsNonClosureSituation(Situation situation){
			return containsSituation(nonClosureSituations, situation);
		}

		public Situation getSituation(Situation situation){
			for (Situation sit : situations) {
				if (sit.canMergeDisregardingContext(situation)){
					return sit;
				}
			}
			return null;
		}

		@Override
		public int compareTo(Integer time) {
			return Integer.compare(timestamp, time);
		}

		public boolean didSomethingChange(){
			return didAdjacentStatesChange() || didSituationsChange();
		}

		public boolean didAdjacentStatesChange(){
			return !history.state.adjacentStates.equals(adjacentStates);
		}

		public boolean didSituationsChange(){
			return !new HashSet<Situation>(history.state).equals(new HashSet<>(situations));
		}

		public void store(){
			history.push(this);
		}

		public Item addUsedTransferSymbol(Symbol symbol){
			usedTransferSymbols.add(symbol);
			return this;
		}

		public Item addUsedSituations(Situation... situations){
			usedSituations.addAll(Arrays.asList(situations));
			return this;
		}

		public Item addUsedSituations(List<Situation> situations){
			usedSituations.addAll(situations);
			return this;
		}
		
		public void mergeSituations(Item otherItem){
			if (otherItem == null){
				return;
			}
			for (Situation otherSituation : otherItem.situations) {
				for (Situation situation : situations) {
					if (situation.canMergeDisregardingContext(otherSituation)){
						situation.merge(otherSituation);
					}
				}
			}
			for (Situation otherSituation : usedSituations) {
				for (Situation situation : situations) {
					if (situation.canMergeDisregardingContext(otherSituation)){
						situation.merge(otherSituation);
					}
				}
			}
		}

		public void mergeAdjacentStates(Item otherItem){
			if (otherItem == null){
				return;
			}
			for (Symbol symbol : otherItem.adjacentStates.keySet()) {
				if (!adjacentStates.containsKey(symbol)){
					adjacentStates.put(symbol, otherItem.adjacentStates.get(symbol));
				}
			}
		}

		@Override
		public String toString() {
			return history.state.id + ": " + Utils.join(situations, "\n");
		}
	}

	protected List<Situation> cloneSituationList(List<Situation> situations){
		List<Situation> newSituationList = new ArrayList<>();
		for (Situation situation : situations) {
			newSituationList.add(situation.clone());
		}
		return newSituationList;
	}

	public boolean didSituationsChange(){
		return lastItem == null || lastItem.didSituationsChange();
	}

	public boolean didSomethingChange(){
		return lastItem == null || lastItem.didSomethingChange();
	}

	public static class ItemList extends ArrayList<Item> {

		public boolean containsAsUsed(int stateId){
			for (Item item : this) {
				if (item.history.state.id == stateId && item.onlyUsedAtThisTime){
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean add(Item item) {
			return super.add(item);
		}

		public void storeAllItems(){
			for (Item item : this) {
				item.store();
			}
		}
	}
}

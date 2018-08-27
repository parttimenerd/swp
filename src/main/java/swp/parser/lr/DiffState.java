package swp.parser.lr;

import java.util.*;

import swp.grammar.*;
import swp.util.Utils;

/**
 * Created by parttimenerd on 15.07.16.
 */
public class DiffState extends State {

	public DiffHistory diffHistory;

	public DiffState(Grammar grammar){
		super(grammar);
		this.diffHistory = new DiffHistory(this);
	}

	public String toGraphvizString(int time){
		StringBuilder builder = new StringBuilder();
		DiffHistory.Item currentItem = diffHistory.atTime(time);
		DiffHistory.Item finalItem = diffHistory.last();
		boolean hidden = !diffHistory.isVisible(time);
		boolean exact = diffHistory.hasExact(time);
		boolean stateJustAdded = diffHistory.atTime(time - 1) == null && !hidden;
		boolean used = diffHistory.usedAt(time);
		if (used){
			currentItem = diffHistory.usedAtItem(time);
		}
		final String black;
		final String white;
		final String color;
		if (used){
			black = Utils.USED_DIFF_HTML_COLOR;
			white = Utils.USED_DIFF_HTML_COLOR2;
			color = Utils.USED_DIFF_HTML_COLOR;
		} else if (hidden){
			black = Utils.HIDDEN_DIFF_HTML_COLOR;
			color = Utils.HIDDEN_DIFF_HTML_COLOR;
			white = Utils.HIDDEN_DIFF_HTML_COLOR2;
		} else if (exact){
			black = Utils.EXACT_DIFF_HTML_COLOR;
			white = Utils.EXACT_DIFF_HTML_COLOR2;
			color = Utils.EXACT_DIFF_HTML_COLOR;
		} else {
			black = "black";
			white = "white";
			color = "";
		}

		builder.append("\"state").append(id)
				.append("\" [style = \"filled, bold\" penwidth = 5 color=\"")
				.append(color).append("\" ").append("fillcolor = \"white\"")
				.append(" label=<<table border=\"0\" cellborder=\"0\" cellpadding=\"3\" " +
						"bgcolor=\"white\"><tr><td bgcolor=\"").append(black).append("\" align=\"center\" ")
				.append("colspan=\"2\">").append("<font color=\"").append(white).append("\">State ").append(id)
				.append("</font></td></tr>");

		List<Situation> finalSituations = new ArrayList<>();
		finalSituations.addAll(finalItem.nonClosureSituations);
		finalSituations.addAll(finalItem.closureSituations);
		List<Situation> currentSituations = hidden ? new ArrayList<>() : currentItem.situations ;
		Set<Situation> currentUsedSituations = hidden ? new HashSet<>() : currentItem.usedSituations;
		boolean firstClosureItem = true;
		for (Situation situation : finalSituations){
			boolean isClosureItem = diffHistory.isClosureSituation(situation);
			boolean justAdded = diffHistory.wasSituationJustAdded(time, situation);
			boolean invisible = diffHistory.isSituationInvisible(time, situation);
			boolean isUsed = currentUsedSituations.contains(situation) && (exact || used);

			builder.append("<tr><td align=\"left\" port=\"r0\">");

			Utils.ColorPair defaultColor = Utils.diffHTMLColorPair(justAdded, invisible);

			if (!isClosureItem) {
				builder.append("<b>");
			}
			DiffHistory.Item priorItem = diffHistory.atTime(time - 1);
			Situation priorSituation = priorItem == null ? null : priorItem.getSituation(situation);
			Situation currentSituation = currentItem == null ? null : currentItem.getSituation(situation);
			builder.append(formatSituation(time, (stateJustAdded && isClosureItem),
					isUsed,
					priorSituation, currentSituation, situation));

			if (!isClosureItem) {
				builder.append("</b>");
			}
			builder.append("</td></tr>");

			if (isClosureItem){
				firstClosureItem = false;
			}
		}

		builder.append("</table>> ];\n");
		List<Symbol> finalSymbols = new ArrayList<>();
		finalSymbols.addAll(finalItem.adjacentStates.keySet());
		Collections.sort(finalSymbols);
		Map<Symbol, State> currentAdjStates = hidden ? new HashMap<>() : currentItem.adjacentStates;
		for (Symbol shiftSymbol : finalSymbols){
			String edgeColor = "black";
			if (used){
				if (currentItem.usedTransferSymbols.contains(shiftSymbol)) {
					edgeColor = Utils.USED_DIFF_HTML_COLOR;
				} else {
					edgeColor = "black";
				}
			} else if (diffHistory.isEdgeInvisible(time, shiftSymbol)){
				edgeColor = Utils.HIDDEN_DIFF_COLOR;
			} else if (diffHistory.wasEdgeJustAdded(time, shiftSymbol)){
				edgeColor = Utils.EXACT_DIFF_HTML_COLOR;
			}
			State state = finalItem.adjacentStates.get(shiftSymbol);
			builder.append("state").append(id).append(" -> ").append("state").append(state.id);
			builder.append("[ penwidth = 5 fontsize = 28 fontcolor = \"").append(edgeColor).append("\" color = \"")
					.append(edgeColor).append("\" label = \"");
			builder.append(Utils.escapeHtml(shiftSymbol.toString())).append("\"];\n");
		}
		return builder.toString();
	}

	public Situation mergeableNotEqualSituation(List<Situation> currentSituations, Situation otherSituation){
		for (Situation situation : currentSituations){
			if (!situation.equals(otherSituation) && situation.canMergeRegardingContext(situation)){
				return situation;
			}
		}
		return null;
	}

	@Override
	public Map<Symbol, State> shift(){
		Map<Symbol, State> adjacentStates = new HashMap<>();
		Collections.sort(this);
		for (Situation situation : this){
			if (situation.inFrontOfTerminal() || situation.inFrontOfNonTerminal()) {
				Symbol symbol = situation.nextSymbol();
				//if (situation.inFrontOfTerminal(grammar.eof)){
				//	continue;
				//}
				if (!adjacentStates.containsKey(symbol)){
					//if (DiffGraph.mode == DiffGraph.Mode.SITUATION_LEVEL) {
						storeInHistory();
					//}
					DiffState state = new DiffState(grammar);
					adjacentStates.put(symbol, state);
				}
				adjacentStates.get(symbol).add(situation.advance());
			}
		}
		return adjacentStates;
	}

	public void storeInHistory(){
		diffHistory.push();
	}

	public void storeAsUsedInHistory(Symbol... transferSymbols){
		diffHistory.push(diffHistory.createUsedItemWOTimestamp(transferSymbols));
	}

	private String formatSituation(int time, boolean forceHidden, boolean isUsed,
	                               Situation priorSituation,
	                               Situation currentSituation,
	                               Situation finalSituation){
		boolean situationHidden = currentSituation == null || forceHidden;
		boolean situationJustAdded = priorSituation == null;

		Utils.ColorPair ovColor = Utils.diffHTMLColorPair(situationJustAdded, situationHidden, isUsed);

		StringBuilder builder = new StringBuilder();
		builder.append("<font color=\"" + ovColor.color + "\">");
		builder.append(finalSituation.toHTMLStringWoContext());
		builder.append("; &#32;");
		builder.append("</font>");

		Set<Integer> priorContext = priorSituation == null ? new HashSet<>() : priorSituation.context.terminalIds;
		Set<Integer> currentContext = currentSituation == null ? new HashSet<>() : currentSituation.context.terminalIds;
		List<Terminal> finalContext = finalSituation.context;
		for (int i = 0; i < finalContext.size(); i++) {
			Terminal terminal = finalContext.get(i);
			boolean hidden = !currentContext.contains(terminal.id) || forceHidden;
			boolean justAdded = !priorContext.contains(terminal.id) && !hidden;
			Utils.ColorPair color = Utils.diffHTMLColorPair(justAdded, hidden, isUsed);
			builder.append("<font color=\"").append(color.color).append("\">");
			if (i != 0){
				builder.append("/");
			}
			builder.append(Utils.escapeHtml(terminal.toString())).append("</font>");
		}
		return builder.toString();
	}

	public boolean closure(DiffHistory.ItemList itemDestList){
		DiffHistory.ItemList toBeAdded = new DiffHistory.ItemList();
		if (diffHistory.didSomethingChange()) {
			toBeAdded.add(diffHistory.createItemWOTimestamp());
		}
		boolean somethingReallyChanged = false;
		boolean somethingChanged = true;
		int doneTill = 0;  // first index that isn't done yet
		while (somethingChanged){
			somethingChanged = false;
			for (int i = doneTill; i < size(); i++){
				Situation situation = get(i);
				if (situation.inFrontOfNonTerminal()){
					//somethingChanged = true; // remove it??
					List<Symbol> term = situation.right.subList(situation.position + 1, situation.right.size());
					Context context = new Context(grammar.calculateFirst1SetForTerm(term, situation.context));
					NonTerminal n = (NonTerminal)situation.nextSymbol();
					for (Production prod : grammar.getProductionOfNonTerminal(n)){
						boolean changed = add(new Situation(prod, (Context)context.clone()), false);
						if (changed && DiffGraph.mode == DiffGraph.Mode.SITUATION_LEVEL){
							toBeAdded.add(diffHistory.createItemWOTimestamp(situation));
						}
						somethingChanged = changed || somethingChanged;
					}
				}
			}
			somethingReallyChanged = somethingReallyChanged || somethingChanged;
			doneTill = size();
		}
		if (somethingReallyChanged){
			//toBeAdded.add(diffHistory.createUsedItemWOTimestamp());
			itemDestList.addAll(toBeAdded);
		}
		return somethingReallyChanged;
	}

	public List<Situation> getUsedSituation(DiffState usedDiffState, Symbol usedSymbol){
		List<Situation> situations = new ArrayList<>();
		for (Situation situation : usedDiffState) {
			if (usedSymbol instanceof NonTerminal){
				if (situation.inFrontOfNonTerminal((NonTerminal)usedSymbol)){
					situations.add(situation);
				}
			} else if (usedSymbol instanceof Terminal){
				if (situation.inFrontOfTerminal((Terminal)usedSymbol)) {
					situations.add(situation);
				}
			}
		}
		return situations;
	}

	public boolean merge(DiffState other, DiffHistory.ItemList itemDestList,
	                     DiffState usedDiffState, Symbol usedSymbol){
		List<Situation> usedSituations = getUsedSituation(usedDiffState, usedSymbol);
		DiffHistory.ItemList toBeAdded = new DiffHistory.ItemList();
		if (diffHistory.didSomethingChange()) {
			toBeAdded.add(diffHistory.createItemWOTimestamp());
			DiffHistory.Item usedItem = usedDiffState.diffHistory.createUsedItemWOTimestamp(usedSymbol);
			usedItem.usedSituations.addAll(usedSituations);
			toBeAdded.add(usedItem);
		}
		boolean somethingChanged = false;
		for (Situation situation : this){
			for (Situation otherSituation : other){
				if (otherSituation.canMergeRegardingContext(situation)){
					boolean changed = situation.merge(otherSituation);
					if (changed && DiffGraph.mode == DiffGraph.Mode.SITUATION_LEVEL){
						toBeAdded.add(diffHistory.createItemWOTimestamp());
						DiffHistory.Item usedItem = usedDiffState.diffHistory.createUsedItemWOTimestamp(usedSymbol);
						usedItem.usedSituations.addAll(usedSituations);
						toBeAdded.add(usedItem);
					}
					somethingChanged = changed || somethingChanged;
					break;
				}
			}
		}
		if (somethingChanged){
			itemDestList.addAll(toBeAdded);
		}
		return somethingChanged;
	}

	public boolean canMerge(State other, DiffHistory.ItemList itemDestList){
		((DiffState)other).closure(itemDestList);
		this.closure(itemDestList);
		if (other.size() != size()){
			return false;
		}
		for (Situation situation : this){
			boolean mergeable = false;
			for (Situation otherSituation : other){
				if (otherSituation.canMergeRegardingContext(situation)){
					mergeable = true;
				}
			}
			if (!mergeable){
				return false;
			}
		}
		return true;
	}

}

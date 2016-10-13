package swp.parser.lr;

import swp.grammar.Grammar;
import swp.grammar.NonTerminal;
import swp.grammar.Production;
import swp.grammar.Symbol;
import swp.util.Utils;

import java.util.*;

/**
 * Created by parttimenerd on 15.07.16.
 */
public class State extends ArrayList<Situation> implements Comparable<State> {

	protected static int stateCounter = 0;

	public int id;
	public final Grammar grammar;

	public Map<Symbol, State> adjacentStates = new HashMap<>();
	public List<Situation> nonClosureItems = new ArrayList<>();

	public State(Grammar grammar){
		id = stateCounter++;
		this.grammar = grammar;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		List<Situation> tempSituations = (List<Situation>)clone();
		Collections.sort(tempSituations);
		for (int j = 0; j < size(); j++) {
			if (j != 0){
				builder.append("\n");
			}
			builder.append("- " + tempSituations.get(j).toString());
		}
		return "State " + id + "\n" + builder.toString();
	}

	public boolean closure(){
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
						somethingChanged = add(new Situation(prod, (Context)context.clone()), false) || somethingChanged;
					}
				}
			}
			somethingReallyChanged = somethingReallyChanged || somethingChanged;
			doneTill = size();
		}
		return somethingReallyChanged;
	}

	@Override
	public boolean add(Situation situation) {
		return add(situation, true);
	}

	public boolean add(Situation situation, boolean isNotInClosure) {
		boolean merged = false;
		for (Situation situation1 : this){
			if (situation1.canMergeDisregardingContext(situation)) {
				return situation1.merge(situation);
			}
		}
		if (!merged){
			super.add(situation);
			if (isNotInClosure) {
				nonClosureItems.add(situation);
			}
			return true;
		}
		return false;
	}

	public boolean hasShiftableSituations(){
		for (Situation situation : this){
			if (situation.inFrontOfNonTerminal() || situation.inFrontOfTerminal()){
				return true;
			}
		}
		return false;
	}

	/**
	 * You need to set the map of this state yourself.
	 * @return
	 */
	public Map<Symbol, State> shift(){
		Map<Symbol, State> adjacentStates = new HashMap<>();
		for (Situation situation : this){
			if (situation.inFrontOfTerminal() || situation.inFrontOfNonTerminal()) {
				Symbol symbol = situation.nextSymbol();
				//if (situation.inFrontOfTerminal(grammar.eof)){
				//	continue;
				//}
				if (!adjacentStates.containsKey(symbol)){
					adjacentStates.put(symbol, new State(grammar));
				}
				adjacentStates.get(symbol).add(situation.advance());
			}
		}
		return adjacentStates;
	}

	public String toGraphvizString(){
		StringBuilder builder = new StringBuilder();
		builder.append("\"state").append(id)
				.append("\" [style = \"filled, bold\" penwidth = 5 " +
						"color=\"\" fillcolor = \"white\"")
				.append(" label=<<table border=\"0\" cellborder=\"0\" cellpadding=\"3\" " +
						"bgcolor=\"white\"><tr>" +
						"<td bgcolor=\"black\" align=\"center\" colspan=\"2\">" +
						"<font color=\"white\">State ").append(id)
				.append("</font></td></tr>");
		List<Situation> tempSituations = (List<Situation>)clone();
		Collections.sort(tempSituations);
		for (Situation situation : tempSituations){
			String content = situation.toHTMLString();
			builder.append("<tr><td align=\"left\" port=\"r0\">")
					.append(content).append("</td></tr>");
		}
		builder.append("</table>> ];\n");
		for (Symbol shiftSymbol : adjacentStates.keySet()){
			State state = adjacentStates.get(shiftSymbol);
			builder.append("state").append(id).append(" -> ").append("state").append(state.id);
			builder.append("[ penwidth = 5 fontsize = 28 fontcolor = \"black\" color = \"#00000000\" label = \"");
			builder.append(Utils.escapeHtml(shiftSymbol.toString())).append("\"];\n");
		}
		return builder.toString();
	}

	public boolean canMerge(State other){
		other.closure();
		this.closure();
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

	public boolean merge(State other){
		boolean somethingChanged = false;
		for (Situation situation : this){
			for (Situation otherSituation : other){
				if (otherSituation.canMergeRegardingContext(situation)){
					somethingChanged = situation.merge(otherSituation) || somethingChanged;
					break;
				}
			}
		}
		return somethingChanged;
	}

	public boolean isSubsetOf(State other){
		System.out.println("sdf");
		for (Situation situation : other) {
			for (Situation sit : this) {
				if (sit.canMergeDisregardingContext(situation)){
					if (!sit.context.isSubsetOf(situation.context)){
						return false;
					}
				}
			}
		}
		return true;
	}

	private boolean containsSituationWithSubsetOfContext(Situation situation){

		return false;
	}

	public Context getContextOfNonClosureItems(){
		Context context = new Context();
		for (Situation sit : nonClosureItems){
			context.addAll(sit.context);
		}
		return context;
	}

	@Override
	public int compareTo(State o) {
		return Integer.compare(id, o.id);
	}
}

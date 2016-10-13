package swp.parser.lr;

import swp.grammar.Grammar;
import swp.grammar.NonTerminal;
import swp.grammar.Terminal;
import swp.lexer.TerminalSet;
import swp.util.Utils;

import java.util.*;

/**
 * Created by parttimenerd on 25.08.16.
 */
public class ExtLRParserTable extends LRParserTable {

	public Map<Integer, Set<Integer>> contextsOfStates = new HashMap<>();

	/**
	 * ({context}, left hand side symbol, |right hand side|)
	 */
	public Map<Integer, List<Utils.Triple<Set<Integer>, NonTerminal, Integer>>> infoForStates = new HashMap<>();

	public ExtLRParserTable(LRParserTable table) {
		super(table.grammar, table.actionTable, table.gotoTable, table.productionInformation);
	}

	public ExtLRParserTable(Grammar grammar) {
		super(grammar);
	}

	public void addExtStateInformation(State state){
		Set<Integer> types = new HashSet<>();
		for (Terminal terminal : state.getContextOfNonClosureItems()){
			types.add(terminal.id);
		}
		contextsOfStates.put(state.id, types);
		List<Utils.Triple<Set<Integer>, NonTerminal, Integer>> triples = new ArrayList<>();
		for (Situation situation : state.nonClosureItems){
			types = new HashSet<>();
			for (Terminal terminal : situation.context){
				types.add(terminal.id);
			}
			triples.add(new Utils.Triple<>(types, situation.left, situation.rightSize()));
		}
		infoForStates.put(state.id, triples);
	}

	public String toString(TerminalSet set) {
		StringBuilder builder = new StringBuilder();
		builder.append(super.toString());
		return builder.toString();
	}
}

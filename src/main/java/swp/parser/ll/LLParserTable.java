package swp.parser.ll;

import swp.grammar.*;
import swp.lexer.TerminalSet;

import java.util.*;

/**
 * Created by parttimenerd on 02.08.16.
 */
public class LLParserTable {

	private TerminalSet terminalSet;

	public final Grammar grammar;

	/**
	 * Maps a non terminal and an token type id to an executed production id.
	 */
	public Map<NonTerminal, Map<Integer, Integer>> table;

	/**
	 * Maps production ids to their right hand side.
	 */
	public Map<Integer, List<Symbol>> productions;

	public LLParserTable(Grammar grammar){
		this.grammar = grammar;
		table = new HashMap<>();
		productions = new HashMap<>();
	}

	public static LLParserTable fromGrammar(Grammar grammar){
		LLParserTable llTable = new LLParserTable(grammar);
		for (Production production : grammar.getProductions()){
			Set<Terminal> firstFollowSet = grammar.calculateFirstFollowForProduction(production);
			for (Terminal lookahead : firstFollowSet) {
				llTable.insertAction(production.left, lookahead, production);
			}
		}
		return llTable;
	}

	public void insertAction(NonTerminal nonTerminal, Terminal lookahead, Production executedProduction){
		if (terminalSet == null) {
			terminalSet = lookahead.terminalSet;
		}
		if (!productions.containsKey(executedProduction.id)) {
			productions.put(executedProduction.id, executedProduction.right);
		}
		if (!table.containsKey(nonTerminal)){
			table.put(nonTerminal, new HashMap<>());
		}
		Map<Integer, Integer> row = table.get(nonTerminal);
		if (row.containsKey(lookahead.id)){
			System.err.printf("Conflict between %s and %s at lookahead token %s, the second production will be used\n",
					grammar.getProductionForId(row.get(lookahead.id)), executedProduction, lookahead);
		}
		row.put(lookahead.id, executedProduction.id);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		List<NonTerminal> nonTerminals = new ArrayList<>(table.keySet());
		Collections.sort(nonTerminals);
		for (int i = 0; i < nonTerminals.size(); i++){
			if (i != 0){
				builder.append("\n");
			}
			Map<Integer, Integer> row = table.get(nonTerminals.get(i));
			builder.append(nonTerminals.get(i)).append(" = {");
			List<Integer> terminalIds = new ArrayList<>(row.keySet());
			Collections.sort(terminalIds);
			for (int j = 0; j < terminalIds.size(); j++){
				int terminalId = terminalIds.get(j);
				builder.append(" ").append(terminalSet.typeToString(terminalId)).append(" = {");
				List<Symbol> production = productions.get(row.get(terminalIds.get(j)));
				for (Symbol symbol : production){
					builder.append(" ").append(symbol);
				}
				builder.append(" }");
			}
			builder.append(" }");
		}
		return builder.toString();
	}
}

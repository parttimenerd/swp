package swp.parser.early;

import swp.grammar.Grammar;
import swp.grammar.NonTerminal;
import swp.grammar.Production;
import swp.grammar.Terminal;
import swp.lexer.Lexer;
import swp.lexer.Token;

import java.util.ArrayList;
import java.util.List;

public class EarleyParser {

	Lexer lexer;
	Grammar grammar;
	List<Closure> table = new ArrayList<>();
	List<Token> tokens = new ArrayList<>();
	int currentTokenNum = 0;
	Token current;
	boolean somethingChanged = true;
	boolean logSteps = true;

	public EarleyParser(Grammar grammar, Lexer lexer){
		this.grammar = grammar.insertStartNonTerminal();
		this.lexer = lexer;
		List<Production> startProds = grammar.getProductionOfNonTerminal(grammar.getStart());
		addToS(0, new EarlyItem(startProds.get(0)));
		tokens.add(lexer.cur());
		do {
			tokens.add(lexer.next());
		} while (lexer.cur().type != 0);
		current = tokens.get(0);
	}

	public void advanceTokenNum(){
		if (currentTokenNum >= tokens.size() - 1){
			current = tokens.get(tokens.size() - 1);
		} else {
			currentTokenNum++;
			current = tokens.get(currentTokenNum);
		}
	}

	public void parse(){
		for (int i = 0; i < tokens.size(); i++){
			while (somethingChanged){
				somethingChanged = false;
				for (int j = 0; j < table.size(); j++) {
					prediction(j);
					completion(j);
				}
			}
			scanning(i);
			advanceTokenNum();
			somethingChanged = true;
		}
	}

	private void logStep(String method, EarlyItem source, int source_s, EarlyItem result, int result_s){
		if (logSteps){
			System.out.println(String.format("log %10s S_%d %30s to S_%d %.30s", method + ":", source_s, source,
					result_s, result));
		}
	}

	public void prediction(int i){
		List<EarlyItem> S_i = table.get(i);
		for (int index = 0; index < S_i.size(); index++){
			EarlyItem item = S_i.get(index);
			if (item.currentSituation.inFrontOfNonTerminal()){
				NonTerminal n = (NonTerminal)item.currentSituation.nextSymbol();
				for (Production prod : grammar.getProductionOfNonTerminal(n)){
					EarlyItem newItem = new EarlyItem(prod, i);
					if (addToS(i, newItem)) {
						logStep("prediction", item, i, newItem, i);
					}
				}
			}
		}
	}

	public void completion(int i){
		List<EarlyItem> S_i = table.get(i);
		for (int index = 0; index < S_i.size(); index++){
			EarlyItem item = S_i.get(index);
			NonTerminal A = item.currentSituation.left;
			if (item.currentSituation.atEnd()){ // item = [X -> \gamma . , j]
				for (int S_j_index = 0; S_j_index < table.size(); S_j_index++){
					Closure S_j = table.get(S_j_index);
					for (int index_ = 0; index_ < S_j.size(); index_++){
						EarlyItem item_ = S_j.get(index_);
						int k = item_.startTokenId;
						if (item_.currentSituation.inFrontOfNonTerminal(A)){ // X -> a . A ß
							Situation newSituation = item_.currentSituation.advance();
							EarlyItem newItem = new EarlyItem(newSituation, k);
							if(addToS(i, newItem)){
								logStep("completion", item, i, newItem, index);
							}
						}
					}
				}
			}
		}
	}

	public void scanning(int i){
		List<EarlyItem> S_i = table.get(i);
		for (EarlyItem item : S_i){
			int j = item.startTokenId;  // [X -> ß . a ß', j]
			if (item.currentSituation.inFrontOfTerminal()){
				Terminal terminal = (Terminal)item.currentSituation.nextSymbol();
				if (terminal.id == current.type){
					EarlyItem newItem = new EarlyItem(item.currentSituation.advance(), j);
					if(addToS(i + 1, newItem)){
						logStep("scanning", item, i, newItem, i + 1);
					}
				}
			}
		}
	}

	public boolean addToS(int i, EarlyItem item){
		while (i >= table.size()){
			table.add(new Closure(table.size()));
			somethingChanged = true;
		}
		List<EarlyItem> S_i = table.get(i);
		if (!S_i.contains(item)){
			S_i.add(item);
			somethingChanged = true;
			return true;
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (Closure closure : table){
			builder.append("Early Set " + closure.i + "\n");
			builder.append(closure.toString());
			if (closure.i < table.size() - 1){
				builder.append("\n");
			}
		}
		return builder.toString();
	}
}

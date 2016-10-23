package swp.grammar.random;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import swp.grammar.Grammar;
import swp.grammar.NonTerminal;
import swp.grammar.Production;
import swp.grammar.Symbol;
import swp.grammar.Terminal;

/**
 * A generator of random sentences that are valid for a given grammar.
 */
public class SentenceGenerator {

	private final Grammar grammar;
	private Set<Production> onlySingleUseProds = new HashSet<>();
	private Map<NonTerminal, Set<Production>> singleProdNonTerminals;
	private Random rand = new Random();
	private Map<Production, Double> productionLengths = new HashMap<>();

	public SentenceGenerator(Grammar grammar) {
		this.grammar = grammar;
		this.singleProdNonTerminals = grammar.calculateSingleProductionNonTerminals();
		for (Production production : grammar.getProductions()) {
			productionLengths.put(production, production.rightSize() * 2.0);
			if (production.nonTerminals.size() == 1 && production.rightSize() == 1){
				productionLengths.put(production, 1.0);
			}
		}
	}

	/**
	 *
	 * @return null if no valid sequence is found
	 */
	public TerminalSequence generateRandomSentence(){
		System.out.println(grammar.longDescription());
		try {
			Map<Production, Integer> weights = new HashMap<>();
			for (Production production : grammar.getProductions()) {
				weights.put(production, 1);
			}
			return generateRandomSentence(grammar.getStart(), 0.8);
		} catch (StackOverflowError err){
			return generateRandomSentence();
		}
	}

	private TerminalSequence generateRandomSentence(NonTerminal startNonTerminal, double factor){
		//System.out.println(weights);
		Production production = weightedProduction(productionLengths, factor, startNonTerminal.getProductions());
		TerminalSequence currentListOfTerminals = new TerminalSequence();
		for (Symbol symbol : production.right) {
			if (symbol instanceof NonTerminal) {
				NonTerminal rhsNonTerm = (NonTerminal) symbol;
				TerminalSequence res = generateRandomSentence(rhsNonTerm, factor);
				currentListOfTerminals.addAll(res);
			} else if (symbol instanceof Terminal) {
				currentListOfTerminals.add((Terminal) symbol);
			}
		}
		return currentListOfTerminals;
	}

	private boolean checkNonTerminal(NonTerminal nonTerminal, Set<Production> notUsableProductions){
		return true;
	}

	private Production weightedProduction(Map<Production, Double> counts, double factor, List<Production> avProds){
		float sum = 0;
		for (Production avProd : avProds) {
			sum += Math.pow(factor, counts.get(avProd));
		}
		double randomNum = Math.random() * (sum + 0.0);
		Production ret = avProds.get(avProds.size() - 1);
		sum = 0;
		for (Production avProd : avProds) {
			sum += Math.pow(factor, counts.get(avProd));
			if (randomNum <= sum){
				ret = avProd;
				break;
			}
		}
		//System.out.println(ret.id + "  " + avProds + "  " + counts);
		return ret;
	}
}

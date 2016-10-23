package swp.grammar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;

import swp.lexer.TerminalSet;
import swp.parser.lr.BaseAST;
import swp.parser.lr.ListAST;
import swp.util.SerializableFunction;

import static swp.util.Utils.join;

/**
 * Grammar consisting of terminals, non terminals and productions.
 *
 * Use the (Ext)GrammarBuilder to build a grammar instance properly.
 *
 * @see ExtGrammarBuilder ExtGrammarBuilder
 */
public class Grammar implements Serializable {

	/**
	 * Symbols in this grammar, symbols = nonTerminals ∪ terminals (but without epsilon!)
	 */
	private Set<Symbol> symbols;
	/**
	 * Non terminals in the grammar
	 */
	private Set<NonTerminal> nonTerminals;

	private List<Production> productions;

	private TerminalSet alphabet;

	private Set<Terminal> terminals;

	private NonTerminal start;

	/**
	 * EOF terminal
	 */
	public Terminal eof;

	private Map<NonTerminal, Set<TerminalOrEpsilon>> first1Sets;
	private Map<NonTerminal, Set<Terminal>> follow1Sets;
	private Set<NonTerminal> epsilonableNonTerminals;

	protected Map<Integer, SerializableFunction<ListAST, BaseAST>> reduceActions = new HashMap<>();

	/**
	 * Create a new Grammar object
	 *
	 * Filters productions that are unreachable from the start symbol and removes duplicate rules.
	 *
	 * @param alphabet base alphabet
	 * @param nonTerminals initially used non terminals
	 * @param start start non terminal
	 * @param productions initial set of productions
	 */
	public Grammar(TerminalSet alphabet, Set<NonTerminal> nonTerminals, NonTerminal start,
	               List<Production> productions) {
		this.nonTerminals = nonTerminals;
		this.alphabet = alphabet;
		this.productions = productions;
		this.start = start;
		removeDuplicateProductions();
		removeUnreachable();
		updateTerminalsAndSymbols();
		this.eof = new Terminal(0, alphabet);
	}

	public List<Production> getProductionOfNonTerminal(NonTerminal nonTerminal) {
		List<Production> productions = new ArrayList<>();
		for (Production production : this.productions) {
			if (production.left == nonTerminal) {
				productions.add(production);
			}
		}
		return productions;
	}

	/**
	 * Insert a new start terminal with a <pre>A' ->  A EOF</pre> rule (assuming <pre>A</pre> is the current
	 * start non terminal)
	 *
	 * @return new grammar
	 */
	public Grammar insertStartNonTerminal(){
		Set<String> names = new HashSet<>();
		int biggestId = 0;
		for (NonTerminal nonTerminal : nonTerminals) {
			names.add(nonTerminal.name);
			biggestId = Math.max(nonTerminal.id, biggestId);
		}
		int biggestProductionId = productions.stream().map((x) -> x.id).max(Integer::compare).get();
		String startName = this.start.name + "'";
		while (names.contains(startName)) {
			startName += "'";
		}
		Set<NonTerminal> nonTerminals = new HashSet<>(this.nonTerminals);
		List<Production> productions = new ArrayList<>(this.productions);
		NonTerminal nonTerminal = new NonTerminal(biggestId + 1, startName);
		List<Symbol> right = new ArrayList<>();
		right.add(this.start);
		right.add(eof);
		Production production = new Production(biggestProductionId + 1, nonTerminal, right);
		nonTerminals.add(nonTerminal);
		productions.add(production);
		nonTerminal.addProduction(production);
		return new Grammar(this.alphabet, nonTerminals, nonTerminal, productions);
	}

	private void updateTerminalsAndSymbols(){
		this.terminals = new HashSet<>();
		for (Production prod : productions){
			this.terminals.addAll(prod.terminals);
		}
		Set<Symbol> s = new HashSet<>(this.terminals);
		s.addAll(nonTerminals);
		this.symbols = s;
	}

	private void removeDuplicateProductions(){
		Set<Production> duplicates = new HashSet<>();
		for (int i = 0; i < productions.size() - 1; i++){
			Production first = productions.get(i);
			if (duplicates.contains(first)){
				continue;
			}
			for (int j = i + 1; j < productions.size(); j++){
				Production second = productions.get(j);
				if (duplicates.contains(second) || first.left != second.right
						|| first.right.size() != second.right.size()){
					continue;
				}
				int k = 0;
				for (; k < first.right.size(); k++){
					if (first.right.get(k) != second.right.get(k)){
						break;
					}
				}
				if (k != first.right.size()){
					duplicates.add(second);
				}
			}
		}
		productions.removeAll(duplicates);
		for (Production prod : duplicates){
			prod.left.getProductions().remove(prod);
		}
	}

	private void removeUnreachable(){
		Set<NonTerminal> reached = new HashSet<>();
		Stack<NonTerminal> depthFirstStack = new Stack<>();
		depthFirstStack.add(start);
		reached.add(start);
		Set<NonTerminal> visited = new HashSet<>();
		while (!depthFirstStack.isEmpty()){
			NonTerminal t = depthFirstStack.pop();
			visited.add(t);
			for (Production prod : t.getProductions()){
				reached.addAll(prod.nonTerminals);
				for (NonTerminal nonTerminal : prod.nonTerminals){
					if (!visited.contains(nonTerminal)){
						depthFirstStack.add(nonTerminal);
					}
				}
			}
		}
		List<Production> filteredProds = new ArrayList<>();
		for (Production prod : productions){
			if (reached.contains(prod.left)){
				filteredProds.add(prod);
			}
		}
		this.productions = filteredProds;
		this.nonTerminals = reached;
	}

	public String longDescription(){
		return "Start non terminal: " + start + "\n" +
				"NonTerminals: " + nonTerminals + "\n" +
				"Terminals: " + terminals + "\n" +
				"Productions: \n" + join(productions, "\n");
	}

	protected void setReduceAction(int productionId, SerializableFunction<ListAST, BaseAST> action){
		reduceActions.put(productionId, action);
	}

	/**
	 * Reduce the passed production with the passed expression asts
	 *
	 * @param productionId id of the production
	 * @param asts passed asts
	 * @return self
	 */
	public BaseAST reduce(int productionId, List<BaseAST> asts){
		if (!reduceActions.containsKey(productionId)) {
			/*List<BaseAST> flattenedASTs = new ArrayList<>();
			for (BaseAST ast : asts){
				for (BaseAST expression : ast.children()){
					if (expression instanceof ListAST )
					flattenedASTs.add(new ASTLeaf(token));
				}
			}
			return new ListAST(flattenedASTs);*/
			if (asts.size() != 1) {
				return new ListAST(asts);
			} else {
				return asts.get(0);
			}
		} else {
			return reduceActions.get(productionId).apply(new ListAST(asts));
		}
	}

	/**
	 * Calculate the non terminals that can produce an epsilon.
	 */
	public Set<NonTerminal> calculateEpsilonable(){
		if (epsilonableNonTerminals != null){
			return epsilonableNonTerminals;
		}
		Set<NonTerminal> epsSet = new HashSet<>();
		Set<Production> currentProds = new HashSet<>(productions);
		for (Production prod : productions) {
			if (prod.isEpsilonProduction()){
				epsSet.add(prod.left);
				currentProds.remove(prod);
			}
		}
		boolean somethingChanged = false;
		do {
			somethingChanged = false;
			for (Production prod : currentProds) {
				if (prod.terminals.isEmpty()){
					int i = 0;
					for (; i < prod.right.size(); i++) {
						Symbol sym = prod.right.get(i);
						if (sym instanceof NonTerminal && !epsSet.contains(sym)) {
							break;
						}
					}
					if (i == prod.right.size()){
						somethingChanged = epsSet.add(prod.left) || somethingChanged;
					}
				}
			}
		} while (somethingChanged);
		epsilonableNonTerminals = epsSet;
		return epsSet;
	}

	/**
	 * From http://stackoverflow.com/a/35727772:
	 *
	 * The simplest algorithm to describe is the this one, similar to the algorithm presented in the
	 * Dragon Book, which will suffice for any practical grammar:
	 *
	 *   1. For each non-terminal, compute whether it is nullable.
	 *
	 *   2. Using the above, initialize FIRST(N) for each non-terminal N to the set of leading symbols for
	 *     each production for N. A symbol is a leading symbol for a production if it is either the first
	 *     symbol in the right-hand side or if every symbol to its left is nullable.
	 *     (These sets will contain both terminals and non-terminals; don't worry about that for now.)
	 *
	 *   3. Do the following until no FIRST set is changed during the loop:
	 *
	 *   4. For each non-terminal N, for each non-terminal M in FIRST(N), add every element
	 *   in FIRST(M) to FIRST(N) (unless, of course, it is already present).
	 *
	 * Remove all the non-terminals from all the FIRST sets.
	 * @return first(k=1) set
	 */
	public Map<NonTerminal, Set<TerminalOrEpsilon>> calculateFirst1Set(){
		if (first1Sets != null){
			return first1Sets;
		}
		Set<NonTerminal> epsilonable = calculateEpsilonable();
		Map<NonTerminal, Set<Symbol>> first = new HashMap<>();
		for (NonTerminal nonTerminal : nonTerminals) {
			Set<Symbol> leadingSymbols = new HashSet<>();
			for (Production production : nonTerminal.getProductions()){
				for (int i = 0; i < production.right.size(); i++){
					Symbol sym = production.right.get(i);
					leadingSymbols.add(sym);
					if (!epsilonable.contains(sym)){
						break;
					}
				}
			}
			first.put(nonTerminal, leadingSymbols);
		}
		//System.out.println("first = " + first);
		boolean firstChanged;
		do {
			firstChanged = false;
			for (NonTerminal nonTerminal : nonTerminals){
				Set<Symbol> oldSet = first.get(nonTerminal);
				Set<Symbol> newSet = new HashSet<>(first.get(nonTerminal));
				for (Symbol symbol : oldSet){
					if (symbol instanceof NonTerminal) {
						newSet.addAll(first.get(symbol));
					}
				}
				if (oldSet.size() != newSet.size()){
					firstChanged = true;
				}
				//System.out.println("newSet = " + newSet);
				//System.out.println("oldSet = " + oldSet);
				oldSet.addAll(newSet);
			}
		} while (firstChanged);
		Map<NonTerminal, Set<TerminalOrEpsilon>> firstSets = new HashMap<>();
		for (NonTerminal nonTerminal : nonTerminals){
			Set<TerminalOrEpsilon> firstSet = new HashSet<>();
			for (Symbol symbol : first.get(nonTerminal)){
				if (symbol.isEpsOrTerminal()) {
					firstSet.add((TerminalOrEpsilon) symbol);
				}
			}
			firstSets.put(nonTerminal, firstSet);
		}
		first1Sets = firstSets;
		return firstSets;
	}

	public List<Terminal> calculateFirst1SetForTerm(List<Symbol> term, Collection<Terminal> suffix){
		Set<TerminalOrEpsilon> set = calculateFirst1SetForTerm(term);
		if (isTermEpsilonable(term)){
			set.addAll(suffix);
		}
		ArrayList<Terminal> terminals = new ArrayList<>();
		for (TerminalOrEpsilon teo : set){
			if (!(teo instanceof Epsilon)) {
				terminals.add((Terminal) teo);
			}
		}
		return terminals;
	}

	public Set<TerminalOrEpsilon> calculateFirst1SetForTerm(List<Symbol> term){
		Set<NonTerminal> epsSet = calculateEpsilonable();
		Map<NonTerminal, Set<TerminalOrEpsilon>> firstSets = calculateFirst1Set();
		Set<TerminalOrEpsilon> set = new HashSet<>();
		term = removeEpsilons(term);
		Loop: for (Symbol symbol : term){
			if (symbol instanceof NonTerminal){
				for (TerminalOrEpsilon toe : firstSets.get(symbol)){
					if (toe instanceof Terminal){
						set.add(toe);
					}
				}
				if (!epsSet.contains(symbol)){
					break Loop;
				}
			} else {  // terminal
				set.add((Terminal)symbol);
				break Loop;
			}
		}
		if (isTermEpsilonable(term)){
			set.add(new Epsilon());
		}
		return set;
	}

	private boolean isTermEpsilonable(List<Symbol> term){
		Set<NonTerminal> epsSet = calculateEpsilonable();
		for (Symbol symbol : term){
			if (symbol instanceof NonTerminal){
				if (!epsSet.contains(symbol)){
					return false;
				}
			} else if (symbol instanceof Terminal){
				return false;
			}
		}
		return true;
	}


	/**
	 *

	 If A is the start nonterminal, put EOF (a new terminal indicating the end of input) in F[0](A).

	 For each production X → αAβ, put FIRST(β) − {EPSILON} in F[n](A),

	 and if EPSILON is in FIRST(β) then put F[n-1](X) into F[n](A)

	 For each production X → αA, put F[n-1](X) into F[n](A).

	 Stop when F[n](*) == F[n-1](*)

	 FOLLOW(*) == F[n](*)
	 * @return
	 */
	/*public Map<NonTerminal, Set<Terminal>> calculateFollow1Set() {
		if (follow1Sets != null) {
			return follow1Sets;
		}
		follow1Sets = new HashMap<>();
		follow1Sets.put(start, new HashSet<>(makeArrayList(eof)));
		boolean somethingChanged = true;
		while (somethingChanged){
			somethingChanged = false;
			for (Production production : productions){

			}
		}
		return follow1Sets;
	}*/

/*
	private static class NonEpsGrammar {

		public final Set<NonTerminal> epsilonable;

		public final Map<NonTerminal, Set<List<Symbol>>> productionsPerNonTerminal;

		public NonEpsGrammar(Set<NonTerminal> epsilonable, Map<NonTerminal,
				Set<List<Symbol>>> productionsPerNonTerminal) {
			this.epsilonable = epsilonable;
			this.productionsPerNonTerminal = productionsPerNonTerminal;
		}

		@Override
		public String toString() {
			return join(productionsPerNonTerminal.entrySet().stream().map((x) -> {
				List<String> strs = new ArrayList<String>();
				for (List<Symbol> l : x.getValue()){
					String str = "";
					for (Symbol sym : l){
						str += " " + sym.toString();
					}
					strs.add(str);
				}
				return x.getKey() + "  →" + join(strs, " |");
			}).collect(Collectors.toList()), "\n");
		}
	}

	public NonEpsGrammar getNonDeterministicVersion(){
		Map<NonTerminal, Set<List<Symbol>>> nonDeterm = new HashMap<>();
		for (NonTerminal nonTerminal : nonTerminals) {
			Set<List<Symbol>> set = new HashSet<>();
			for (Production production : nonTerminal.getProductions()){
				getNonDeterministicVersion_(removeEpsilons(production.right), set, 0);
			}
			nonDeterm.put(nonTerminal, set);
		}
		return new NonEpsGrammar(calculateEpsilonable(), nonDeterm);
	}

	private void getNonDeterministicVersion_(List<Symbol> symbols, Set<List<Symbol>> acc, int pos){
		if (pos >= symbols.size()){
			if (!symbols.isEmpty()) {
				acc.add(symbols);
			}
			return;
		}
		getNonDeterministicVersion_(symbols, acc, pos + 1);
		if (symbols.get(pos) instanceof NonTerminal && calculateEpsilonable().contains(symbols.get(pos))){
			List<Symbol> newList = new ArrayList<>(symbols);
			newList.remove(pos);
			getNonDeterministicVersion_(newList, acc, pos);
		}
	}

	public Map<NonTerminal, Set<List<Terminal>>> calculateFirstSet(int k){
		// NonTerminal -> {[Symbols]}
		Map<NonTerminal, Set<List<Terminal>>> first = new HashMap<>();
		for (NonTerminal nonTerminal : nonTerminals){
			first.put(nonTerminal, new HashSet<>());
		}

		NonEpsGrammar grammar = getNonDeterministicVersion();

		Map<NonTerminal, Set<List<Symbol>>> workingFirst = new HashMap<>();
		for (NonTerminal nonTerminal : nonTerminals){
			Set<List<Symbol>> symbols = new HashSet<>();
			symbols.addAll(grammar.productionsPerNonTerminal.get(nonTerminal));
			workingFirst.put(nonTerminal, symbols);
		}



		Consumer<List<Symbol>> cut = (l) -> {
			while (l.size() > k){
				l.remove(l.size() - 1);
			}
		};

		Function<List<Symbol>, Boolean> hasNonTerminals = (l) -> {
			cut.accept(l);
			for (Symbol sym : l){
				if (sym instanceof NonTerminal){
					return true;
				}
			}
			return false;
		};

		for (NonTerminal nonTerminal : calculateEpsilonable()){
			first.get(nonTerminal).add(new ArrayList<>());
		}
		// has no non terminals in the first k symbols?
		Function<List<Symbol>, Boolean> finished = (l) -> {
			for (int i = 0; i < l.size() && i < k; i++){
				if (l.get(i) instanceof NonTerminal){
					return false;
				}
			}
			return true;
		};

		boolean somethingChanged;

		do {
			somethingChanged = false;

			Map<NonTerminal, Set<List<Symbol>>> newWorkingFirst = new HashMap<>();

			for (NonTerminal nonTerminal : nonTerminals){
				Set<List<Symbol>> oldSet = workingFirst.get(nonTerminal);
				Set<List<Symbol>> set = new HashSet<>();
				for (List<Symbol> prod : oldSet){
					somethingChanged |= extendProduction(set, oldSet, workingFirst, first, finished, cut, prod, 0, nonTerminal);
				}
				newWorkingFirst.put(nonTerminal, set);
			}
			workingFirst = newWorkingFirst;

		}	while(somethingChanged);

		return first;
	}

	private boolean extendProduction(Set<List<Symbol>> acc, Set<List<Symbol>> old,
	                                 Map<NonTerminal, Set<List<Symbol>>> workingFirst,
	                                 Map<NonTerminal, Set<List<Terminal>>> finishedFirst,
	                                 Function<List<Symbol>, Boolean> finished,
	                                 Consumer<List<Symbol>> cut,
	                                 List<Symbol> production, int pos,
	                                 NonTerminal nonTerminal){
		boolean somethingChanged = false;
		if (pos >= production.size()){
			cut.accept(production);
			if (finished.apply(production)){
				List<Terminal> terminals = new ArrayList<>();
				for (Symbol symbol : production){
					terminals.add((Terminal)symbol);
				}
				somethingChanged = finishedFirst.get(nonTerminal).add(terminals);
			} else {
				somethingChanged = old.contains(production);
				acc.add(production);
			}
			return somethingChanged;
		}
		if (production.get(pos) instanceof Terminal){
			return extendProduction(acc, old, workingFirst, finishedFirst, finished, cut, production, pos + 1, nonTerminal);
		}
		Set<List<Symbol>> wFirst = workingFirst.get(production.get(pos));
		Set<List<Terminal>> fFirst = finishedFirst.get(production.get(pos));

		Function<List<Symbol>, Boolean> handle = (l) -> {
			List<Symbol> newList = new ArrayList<>();
			for (int i = 0; i < pos; i++){
				newList.add(production.get(i));
			}
			newList.addAll(l);
			for (int i = pos + 1; i < production.size(); i++){
				newList.add(production.get(i));
			}
			cut.accept(newList);
			return extendProduction(acc, old, workingFirst, finishedFirst, finished, cut, production, pos + 1, nonTerminal);
		};

		for (List<Symbol> symbols : wFirst){
			somethingChanged |= handle.apply(symbols);
		}

		return somethingChanged;
	}
*/
	/**
	 * Calculate the follow 1 set for all non terminals
	 *
	 * First put $ (the end of input marker) in Follow(S) (S is the start symbol)
	 * If there is a production A → aBb, (where a can be a whole string) then everything in FIRST(b) except for ε is placed in FOLLOW(B).
	 * If there is a production A → aB, then everything in FOLLOW(A) is in FOLLOW(B)
	 * If there is a production A → aBb, where FIRST(b) contains ε, then everything in FOLLOW(A) is in FOLLOW(B)
	 * @return
	 */
	public Map<NonTerminal, Set<Terminal>> calculateFollow1Set(){
		if (follow1Sets != null){
			return follow1Sets;
		}
		Map<NonTerminal, Set<Symbol>> follow = new HashMap<>();
		Map<NonTerminal, Set<TerminalOrEpsilon>> first = calculateFirst1Set();
		Set<NonTerminal> epsilonable = calculateEpsilonable();
		for (NonTerminal nonTerminal : nonTerminals){
			follow.put(nonTerminal, new HashSet<>());
		}
		Function<Symbol, Set<Symbol>> getFollowSet = (sym) -> {
			if (sym instanceof NonTerminal){
				return follow.get(sym);
			} else {
				Set<Symbol> set = new HashSet<>();
				set.add(sym);
				return set;
			}
		};
		follow.get(start).add(eof);
		boolean followChanged;
		do {
			followChanged = false;
			for (Production production : productions){
				if (production.isEpsilonProduction()){
					continue;
				}
				Set<Symbol> lastFollow = new HashSet<>();
				lastFollow.addAll(follow.get(production.left));
				for (int i = production.right.size() - 1; i >= 0; i--){
					if (production.right.get(i) instanceof NonTerminal){
						NonTerminal rightPart = (NonTerminal)production.right.get(i);
						if (follow.get(rightPart).addAll(lastFollow)){
							followChanged = true;
						}
						if (!epsilonable.contains(rightPart)){
							lastFollow.clear();
						}
						lastFollow.addAll(first.get(rightPart));
					} else {  // terminal
						lastFollow.clear();
						lastFollow.add(production.right.get(i));
					}
				}
			}
		} while (followChanged);
		Map<NonTerminal, Set<Terminal>> followSets = new HashMap<>();
		for (NonTerminal nonTerminal : nonTerminals){
			Set<Terminal> followSet = new HashSet<>();
			for (Symbol symbol : follow.get(nonTerminal)){
				if (symbol instanceof NonTerminal){
					for (Symbol sym : follow.get(symbol)){
						if (sym instanceof Terminal){
							followSet.add((Terminal)sym);
						}
					}
				} else if (symbol instanceof Terminal){
					followSet.add((Terminal)symbol);
				}
			}
			followSets.put(nonTerminal, followSet);
		}
		follow1Sets = followSets;
		return followSets;
	}

	public Set<Terminal> calculateFollow1SetForNonTerminal(NonTerminal nonTerminal){
		Map<NonTerminal, Set<Terminal>> sets = calculateFollow1Set();
		return sets.get(nonTerminal);
	}

	public Set<Terminal> calculateFirstFollowForProduction(Production production) {
		return new HashSet<>(calculateFirst1SetForTerm(production.right, calculateFollow1SetForNonTerminal(production.left)));
	}

	public Set<TerminalOrEpsilon> calculateFirst1ForProduction(List<Symbol> prod){
		Set<NonTerminal> epsSet = calculateEpsilonable();
		Map<NonTerminal, Set<TerminalOrEpsilon>> firstSets = calculateFirst1Set();
		Set<TerminalOrEpsilon> set = new HashSet<>();
		prod = removeEpsilons(prod);
		for (Symbol symbol : prod){
			if (symbol instanceof NonTerminal){
				for (TerminalOrEpsilon toe : firstSets.get(symbol)){
					if (toe instanceof Terminal){
						set.add(toe);
					}
				}
				if (!epsSet.contains(symbol)){
					return set;
				}
			} else {  // terminal
				set.add((Terminal)symbol);
				return set;
			}
		}
		set.add(new Epsilon());
		return set;
	}

	public boolean isProductionEpsilonable(Production prod){
		if (!calculateEpsilonable().contains(prod.right)){
			return false;
		}
		return isProductionEpsilonable(prod.right);
	}

	public boolean isProductionEpsilonable(List<Symbol> prod){
		Set<NonTerminal> epsSet = calculateEpsilonable();
		prod = removeEpsilons(prod);
		for (Symbol symbol : prod){
			if ((symbol instanceof NonTerminal && !(epsSet.contains(symbol)))
					|| symbol instanceof Terminal){
				return false;
			}
		}
		return true;
	}

	private List<Symbol> removeEpsilons(List<Symbol> prod){
		if (!prod.contains(new Epsilon())){
			return prod;
		}
		List<Symbol> ret = new ArrayList<>();
		for (Symbol symbol : prod){
			if (!(symbol instanceof Epsilon)){
				ret.add(symbol);
			}
		}
		return ret;
	}

	public boolean isSLL1(){
		return isSLL1(false);
	}

	public boolean isSLL1(boolean log){
		Map<NonTerminal, Set<TerminalOrEpsilon>> firstSets = calculateFirst1Set();
		Map<NonTerminal, Set<Terminal>> followSets = calculateFollow1Set();
		for (NonTerminal nonTerminal : nonTerminals){
			for (Production prod1 : productions){
				for (Production prod2 : productions) {
					if (prod1 == prod2) {
						continue;
					}
					if (!isProductionEpsilonable(prod1) && !isProductionEpsilonable(prod2)) {
						Set<TerminalOrEpsilon> first1 = calculateFirst1ForProduction(prod1.right);
						Set<TerminalOrEpsilon> first2 = calculateFirst1ForProduction(prod2.right);
						Set<TerminalOrEpsilon> intersection = new HashSet<>(first1);
						intersection.retainAll(first2);
						if (intersection.size() > 0) {
							if (log)
								System.out.println("Not SLL(1): First1(" + prod1 +
										") ∩ First1(" + prod2 + ") = " + intersection);
							return false;
						}
					}
					NonTerminal A;
					Production prod;
					if (isProductionEpsilonable(prod1)) {
						A = prod1.left;
						prod = prod2;
					} else {
						A = prod2.left;
						prod = prod1;
					}
					if (A != null) {
						Set<TerminalOrEpsilon> first = calculateFirst1ForProduction(prod.right);
						Set<Terminal> follow = follow1Sets.get(A);
						first.retainAll(follow);
						if (first.size() > 0) {
							if (log)
								System.out.println("Not SLL(1): Follow1(" + A +
										") ∩ First1(" + prod + ") = " + first);
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	public List<Production> getProductions(){
		return Collections.unmodifiableList(productions);
	}

	public Production getProductionForId(int id){
		return productions.get(id);
	}
/*
	public Set<List<Terminal>> firstSetForNonTerminal(int k, NonTerminal nonTerminal){
		return firstSetForNonTerminal(k, makeArrayList(nonTerminal), null);
	}

	public Set<List<Terminal>> firstSetForNonTerminal(int k, List<Symbol> prodList, Set<Symbol> noexpand){
		List<Terminal> terminals = new ArrayList<>();
		int pos = 0;

		// Collect terminals
		while (k > 0){
			if (pos >= prodList.size()){
				List<Terminal> k_eofs = new ArrayList<>(k);
				for (int i = 0; i < k; i++){
					k_eofs.add(eof);
				}
				terminals.addAll(k_eofs);
				k = 0;
				break;
			}
			Symbol s = prodList.get(pos);
			if (s instanceof Terminal){
				terminals.add((Terminal) s);
				k -= 1;
				noexpand = null;
			} else {
				break;
			}
			pos += 1;
		}
		if (pos >= prodList.size() || prodList.get(pos) instanceof Terminal){
			Set<List<Terminal>> set = new HashSet<>();
			set.addAll(makeArrayList(terminals));
			return set;
		}
		Symbol s = prodList.get(pos);
		assert s instanceof NonTerminal;
		Set<Symbol> subnoexpand;
		if (noexpand != null){
			if (noexpand.contains(s)){
				return new HashSet<>();
			}
			subnoexpand = noexpand;
		} else {
			subnoexpand = new HashSet<>();
		}
		Set<List<Terminal>> res = new HashSet<>();
		for (Production production : productions){
			if (production.left != s){
				continue;
			}
			List<Symbol> subprod = new ArrayList<>(production.right);
			subprod.addAll(prodList.subList(pos + 1, prodList.size()));
			subprod = removeEpsilons(subprod);
			for (List<Terminal> f : firstSetForNonTerminal(k, subprod, subnoexpand)){
				List<Terminal> comb = new ArrayList<>(terminals);
				terminals.addAll(f);
				res.add(comb);
			}
		}
		return res;
	}*/

	/*public Map<Production, List<Terminal>> calculateFirstSetsPerProduction(int k, boolean padWithEOF){
		Set<NonTerminal> epsilonable = calculateEpsilonable();
		Map<Production, List<Pair<List<Terminal>, Boolean>>> firstsMap = new HashMap<>();
		Map<Production, Set<List<Terminal>>> firstsMap2 = new HashMap<>();
		for (Production prod : productions) firstsMap.put(prod, new HashSet<>());
		List<Production> unfinished = new ArrayList<>(productions);
		List<Production> newlyFinished = new ArrayList<>();
		boolean somethingChanged = false;

		do {
			somethingChanged = false;

			for (Production prod : unfinished){
				Set<List<Terminal>> oldFirstSet = firstsMap.get(prod);
				Set<List<Terminal>> newFirstSet = new HashSet<>();
				for ()
			}

			unfinished.removeAll(newlyFinished);
			newlyFinished.clear();
		} while (somethingChanged);

		return firsts;
	}*/

	public NonTerminal getStart(){
		return start;
	}

	private NonTerminal getNonTerminal(String name){
		for (NonTerminal nonTerminal : nonTerminals){
			if (Objects.equals(nonTerminal.name, name)){
				return nonTerminal;
			}
		}
		throw new Error("No such non terminal " + name);
	}

	/**
	 * Takes rules like A → B in to account.
	 * @return map that contains non terminals that derive to a single production
	 */
	public Map<NonTerminal, Set<Production>> calculateSingleProductionNonTerminals(){
		Map<NonTerminal, Set<Production>> ret = new HashMap<>();
		Map<Production, Set<NonTerminal>> nonTermSetMap = new HashMap<>();
		for (Production production : productions) {
			nonTermSetMap.put(production, new HashSet<>());
		}
		for (NonTerminal nonTerminal : nonTerminals) {
			ret.put(nonTerminal, new HashSet<>());
		}
		for (NonTerminal nonTerminal : nonTerminals) {
			if (nonTerminal.getProductions().size() == 1){
				nonTermSetMap.get(nonTerminal.getProductions().get(0)).add(nonTerminal);
				ret.get(nonTerminal).add(nonTerminal.getProductions().get(0));
			}
		}
		boolean somethingChanged = true;

		while (somethingChanged){
			somethingChanged = false;
			for (NonTerminal lhs : nonTerminals) {
				Set<NonTerminal> intersectionOfAllProds = new HashSet<>();
				intersectionOfAllProds.addAll(productions.get(0).nonTerminals);
				for (Production production : lhs.getProductions()) {
					intersectionOfAllProds.retainAll(production.nonTerminals);
				}
				Set<Production> toAdd = new HashSet<>();
				for (NonTerminal rhs : intersectionOfAllProds) {
					toAdd.addAll(ret.get(rhs));
				}
				somethingChanged = ret.get(lhs).addAll(toAdd) || somethingChanged;
			}
		}

		return ret;
	}
}

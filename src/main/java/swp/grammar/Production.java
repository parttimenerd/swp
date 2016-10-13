package swp.grammar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A grammar production with a left and a right hand side.
 */
public class Production implements Serializable {

	/**
	 * Id of the production
	 */
	public final int id;
	/**
	 * Left hand side of the production (the associated non terminal)
	 */
	public final NonTerminal left;
	/**
	 * Right hand side of the production (doesn't include any epsilon if the right hand side consists of more than
	 * epsilons).
	 */
	public final List<Symbol> right;

	/**
	 * Non terminals used in the right hand side
	 */
	public  List<NonTerminal> nonTerminals;

	/**
	 * Terminals used in the right hand side
	 */
	public final List<Terminal> terminals;

	public Production(int id, NonTerminal left, List<Symbol> right) {
		this.id = id;
		this.left = left;
		List<Symbol> r = new ArrayList<>();
		Epsilon eps = null;
		for (Symbol sym : right){
			if (!(sym instanceof Epsilon)){
				r.add(sym);
			} else {
				eps = (Epsilon)sym;
			}
		}
		if (r.isEmpty()){
			r.add(eps);
		}
		this.right = r;
		List<NonTerminal> nonTerminals = new ArrayList<>();
		List<Terminal> terminals = new ArrayList<>();
		for (Symbol symbol : r) {
			if (symbol instanceof NonTerminal){
				nonTerminals.add((NonTerminal)symbol);
			} else if (symbol instanceof Terminal){
				terminals.add((Terminal) symbol);
			}
		}
		this.nonTerminals = Collections.unmodifiableList(nonTerminals);
		this.terminals = Collections.unmodifiableList(terminals);
	}

	public String formatRightSide(){
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < right.size(); i++) {
			builder.append(right.get(i));
			if (i < right.size() - 1) {
				builder.append(" ");
			}
		}
		return builder.toString();
	}

	@Override
	public String toString() {
		return id + " " + left.toString() + " → " + formatRightSide();
	}

	/**
	 * Can this production be derived to epsilon?
	 */
	public boolean isEpsilonProduction(){
		return right.isEmpty() || right.get(0) instanceof Epsilon;
	}

	@Override
	public boolean equals(Object obj) {
		boolean eq = obj instanceof Production && ((Production)obj).id == id;
		return eq;
	}

	/**
	 * Size of the right hand side.
	 */
	public int rightSize(){
		return isEpsilonProduction() ? 0 : this.right.size();
	}
}

package swp.grammar;

import java.io.Serializable;
import java.util.*;

/**
 * A non terminal symbol with associated productions.
 */
public class NonTerminal extends Symbol implements Serializable {

	/**
	 * Name of the non terminal, typically uppercase
	 */
	public final String name;

	public final int id;
	/**
	 * List of productions that have this non terminal on their left side.
	 */
	private List<Production> productions = new ArrayList<>();

	public NonTerminal(int id, String name) {
		this.name = name;
		this.id = id;
	}

	public List<Production> getProductions(){
		return productions;
	}

	public boolean hasProductions(){
		return !productions.isEmpty();
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int hashCode() {
		return id;
	}

	public void addProduction(Production production) {
		productions.add(production);
	}

	public boolean hasEpsilonProduction(){
		for (Production production : productions) {
			if (production.isEpsilonProduction()){
				return true;
			}
		}
		return false;
	}

	public boolean hasEOFEndedProduction(){
		for (Production production : productions){
			for (Terminal terminal : production.terminals){
				if (terminal.id == 0) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public int compareTo(Symbol o) {
		if (!(o instanceof NonTerminal)){
			return super.compareTo(o);
		}
		return name.compareTo(((NonTerminal)o).name);
	}
}

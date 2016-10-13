package swp.grammar;

import java.io.Serializable;

/**
 * Base class for terminal symbols and non terminal symbols.
 */
public class Symbol implements Serializable, Comparable<Symbol> {

	@Override
	public int hashCode() {
		if (this instanceof NonTerminal){
			return ((NonTerminal)this).id + 1;
		}
		if (this instanceof Terminal){
			return -((Terminal)this).id - 1;
		}
		return 0;
	}

	public boolean isEpsOrTerminal(){
		return this instanceof Epsilon || this instanceof Terminal;
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && obj.hashCode() == this.hashCode() && (obj.getClass() == this.getClass());
	}


	@Override
	public int compareTo(Symbol o) {
		return Integer.compare(hashCode(), o.hashCode());
	}
}

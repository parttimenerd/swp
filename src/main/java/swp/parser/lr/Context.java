package swp.parser.lr;

import swp.grammar.Terminal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Right hand context. Its elements are sorted.
 */
public class Context extends ArrayList<Terminal> {

	public Set<Integer> terminalIds = new HashSet<>();

	public Context(){
		super();
	}

	public Context(List<Terminal> terminals){
		for (Terminal terminal : terminals){
			add(terminal);
		}
	}

	public void addTerminal(Terminal terminal){
		if (!contains(terminal)){
			add(terminal);
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		//Collections.sort(this);
		for (int i = 0; i < size(); i++) {
			if (i != 0) {
				builder.append("/");
			}
			builder.append(get(i));
		}
		return builder.toString();
	}

	@Override
	public boolean equals(Object o) {
		return super.equals(o);
	}

	@Override
	public boolean add(Terminal terminal) {
		if (!contains(terminal)){
			terminalIds.add(terminal.id);
			if (isEmpty()){
				super.add(terminal);
				return true;
			}
			for (int i = 0; i < size(); i++) {
				if (get(i).compareTo(terminal) == 1) {
					super.add(i, terminal);
					return true;
				}
			}
			super.add(terminal);
			return true;
		}
		return false;
	}

	@Override
	public boolean contains(Object o) {
		return (o instanceof Terminal) && contains((Terminal)o);
	}

	public boolean contains(Terminal terminal) {
		return terminalIds.contains(terminal.id);
	}

	public boolean merge(Context context){
		boolean somethingChanged = false;
		for (Terminal terminal : context){
			somethingChanged |= add(terminal);
		}
		return somethingChanged;
	}

	@Override
	public Object clone() {
		Context newContext = new Context();
		newContext.addAll(this);
		newContext.terminalIds = new HashSet<>(terminalIds);
		return newContext;
	}

	public boolean isSubsetOf(Context other){
		if (other.size() >= size()){
			for (int i = 0; i < size(); i++) {
				if (other.get(i) != get(i)){
					return false;
				}
			}
			return true;
		}
		return false;
	}
}

package swp.parser.early;

import swp.grammar.*;

import java.util.List;

/**
 * Created by parttimenerd on 07.07.16.
 */
public class Situation extends Production implements Comparable<Situation> {

	/**
	 * The dot is before the $position.th right hand side symbol
	 */
	public final int position;

	public Situation(int id, NonTerminal left, List<Symbol> right, int position) {
		super(id, left, right);
		this.position = position;
	}

	public Situation(Production production){
		this(production.id, production.left, production.right, 0);
	}

	@Override
	public boolean equals(Object obj) {
		boolean eq = super.equals(obj) && obj instanceof Situation && ((Situation)obj).position == position;
		return eq;
	}

	@Override
	public String formatRightSide() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < position; i++) {
			builder.append(right.get(i));
		}
		builder.append("· ");
		for (int i = position; i < right.size(); i++) {
			builder.append(right.get(i));
			if (i < right.size() - 1) {
				builder.append(" ");
			}
		}
		return builder.toString();
	}

	public boolean canAdvance(){
		return position < right.size() && !(right.get(0) instanceof Epsilon);
	}

	public Situation advance(){
		if (!canAdvance()){
			throw new Error("Can't advance");
		}
		return new Situation(id, left, right, position + 1);
	}

	public Symbol nextSymbol(){
		if (canAdvance()){
			return this.right.get(this.position);
		}
		return new Epsilon();
	}

	public boolean inFrontOfTerminal(){
		return nextSymbol() instanceof Terminal;
	}

	public boolean inFrontOfTerminal(Terminal terminal){
		return inFrontOfTerminal() && terminal.equals(nextSymbol());
	}

	public boolean inFrontOfNonTerminal(){
		return nextSymbol() instanceof NonTerminal;
	}

	public boolean inFrontOfNonTerminal(NonTerminal nonTerminal){
		return inFrontOfNonTerminal() && nonTerminal.equals(nextSymbol());
	}

	public boolean inFrontOfEpsilon(){
		return nextSymbol() instanceof Epsilon;
	}

	public boolean atEnd(){
		return !canAdvance();
	}

	@Override
	public int hashCode() {
		return (int)Math.pow(id, position) % (id + 1);
	}

	@Override
	public int compareTo(Situation o) {
		if (position == 0 && o.position != 0){
			return 1;
		}
		if (position != 0 && o.position == 0){
			return -1;
		}
		if (o.id != id){
			return Integer.compare(id, o.id);
		}
		return Integer.compare(position, o.position);
	}
}

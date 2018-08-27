package swp.parser.lr;

import java.util.*;

import swp.grammar.*;
import swp.util.Utils;

/**
 * Created by parttimenerd on 07.07.16.
 */
public class Situation extends swp.parser.early.Situation {

	public Context context;

	public Situation(int id, NonTerminal left, List<Symbol> right, int position, Context context) {
		super(id, left, right, position);
		this.context = context;
		this.right.removeIf(symbol -> symbol == new Epsilon());
	}

	public Situation(Production production, Context context){
		this(production.id, production.left, production.right, 0, context);
	}

	@Override
	public String formatRightSide() {
		return super.formatRightSide() + "; " + formatContext();
	}

	public String formatContext(){
		StringBuilder builder = new StringBuilder();
		Collections.sort(context);
		for (int i = 0; i < context.size(); i++){
			if (i != 0){
				builder.append("/");
			}
			builder.append(context.get(i));
		}
		return builder.toString();
	}

	public Situation advance(){
		if (!canAdvance()){
			throw new Error("Can't advance");
		}
		return new Situation(id, left, right, position + 1, (Context)context.clone());
	}

	public Situation clone(){
		return new Situation(id, left, right, position, (Context)context.clone());
	}

	public Symbol nextSymbol(){
		if (canAdvance()){
			return this.right.get(this.position);
		}
		return new Epsilon();
	}

	public String toHTMLString(){
		StringBuilder builder = new StringBuilder();
		builder.append(toHTMLStringWoContext());
		builder.append("; &#32;");
		builder.append(Utils.escapeHtml(formatRightSide()));
		return builder.toString();
	}

	public String toHTMLStringWoContext(){
		StringBuilder builder = new StringBuilder();
		builder.append(id).append(" ").append(Utils.escapeHtml(left.toString()));
		builder.append(" → ");
		for (int i = 0; i < position; i++) {
			builder.append(Utils.escapeHtml(right.get(i).toString()));
			builder.append("&#32;");
		}
		builder.append("&bull; ");
		for (int i = position; i < right.size(); i++) {
			builder.append(Utils.escapeHtml(right.get(i).toString()));
			if (i < right.size() - 1) {
				builder.append("&#32;");
			}
		}
		return builder.toString();
	}

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj) && context.equals(((Situation)obj).context);
	}

	public boolean canMergeDisregardingContext(Situation situation){
		return !(situation.left != left || !situation.right.equals(right) || situation.position != position);
	}

	public boolean canMergeRegardingContext(Situation situation){
		if (!canMergeDisregardingContext(situation)){
			return false;
		}
		if (Graph.isLALR){
			return true;
		} else {
			return context.equals(situation.context);
		}
	}

	public boolean merge(Situation situation){
		return context.merge(situation.context);
	}
}

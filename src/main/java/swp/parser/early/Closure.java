package swp.parser.early;

import java.util.ArrayList;

/**
 * Created by parttimenerd on 07.07.16.
 */
public class Closure extends ArrayList<EarlyItem> {

	public final int i;

	public Closure(int i){
		super();
		this.i = i;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (int j = 0; j < size(); j++) {
			if (j != 0){
				builder.append("\n");
			}
			builder.append("- " + this.get(j).toString());
		}
		return builder.toString();
	}
}

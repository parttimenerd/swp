package swp.lexer;

import swp.util.Pair;
import swp.util.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Set terminal symbol types.
 *
 * By convention type '0' signals the end of input.
 */
public abstract class TerminalSet implements Serializable {


	public abstract String typeToString(int type);

	public abstract int stringToType(String typeName);

	public abstract boolean isValidType(int type);

	public abstract boolean isValidTypeName(String typeName);

	public abstract List<Integer> getValidTypes();

	public String typesToString(List<Integer> types){
		return typesToString(types, true);
	}

	public String typesToString(List<Integer> types, boolean withRanges){
		List<String> ret = new ArrayList<>();
		if (withRanges) {
			List<Pair<Integer, Integer>> pairs = Utils.groupIntegers(types);
			int lastChar = -1;
			for (Pair<Integer, Integer> pair : pairs) {
				if (!Objects.equals(pair.first, pair.second)) {
					ret.add(typeToString(pair.first) + "-" + typeToString(pair.second));
				} else {
					ret.add(typeToString(pair.first));
				}
			}
		} else {
			for (int type : types){
				ret.add(typeToString(type));
			}
		}
		return "{" + String.join(" ", ret) + "}";
	}
}

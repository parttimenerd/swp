package swp.lexer.lr;

import java.io.Serializable;
import java.util.*;

import swp.SWPException;
import swp.lexer.TerminalSet;

/**
 * Created by parttimenerd on 28.07.16.
 */
public class StringTerminals extends TerminalSet implements Serializable {

	private List<String> tokenNames;
	private Map<String, Integer> tokenNamesToType = new HashMap<>();

	public StringTerminals(List<String> tokenNames){
		this.tokenNames = tokenNames;
		for (int i = 0; i < tokenNames.size(); i++){
			tokenNamesToType.put(tokenNames.get(i), i);
		}
	}

	public int addTerminal(String name){
		tokenNames.add(name);
		tokenNamesToType.put(name, tokenNames.size() - 1);
		return tokenNames.size() - 1;
	}

	@Override
	public String typeToString(int type) {
		if (type >= tokenNames.size()){
			throw new NoSuchElementException("terminal " + type);
		}
		return tokenNames.get(type);
	}

	@Override
	public int stringToType(String typeName) {
		try {
			return tokenNamesToType.get(typeName);
		} catch (NullPointerException exp){
			throw new SWPException(String.format("No such token %s", typeName));
		}
	}

	@Override
	public boolean isValidType(int type) {
		return type >= 0 && type < tokenNames.size();
	}

	@Override
	public boolean isValidTypeName(String typeName) {
		return tokenNamesToType.containsKey(typeName);
	}

	@Override
	public List<Integer> getValidTypes() {
		List<Integer> ret = new ArrayList<>(tokenNames.size());
		for (int i = 0; i < tokenNames.size(); i++){
			ret.add(i);
		}
		return ret;
	}
}

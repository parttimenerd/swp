package swp.lexer.alphabet;

import swp.util.Utils;
import swp.lexer.TerminalSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Set of (printable) ASCII letters.
 */
public class AlphabetTerminals extends TerminalSet {

	private static AlphabetTerminals instance;
	private List<Integer> validTypes;
	private List<String> typeDescriptions;

	public AlphabetTerminals(){
		validTypes = new ArrayList<>(Utils.MAX_CHAR - (Utils.MIN_CHAR + 1) + 2);
		typeDescriptions = new ArrayList<>(validTypes.size());
		validTypes.add(0);
		typeDescriptions.add("EOF");
		for (int i = Utils.MIN_CHAR; i <= Utils.MAX_CHAR; i++) {
			validTypes.add(i);
			typeDescriptions.add(Utils.toPrintableRepresentation(Character.toString((char)i)));
		}
	}

	@Override
	public String typeToString(int type) {
		assert isValidType(type);
		if (type == 0){
			return typeDescriptions.get(0);
		}
		return typeDescriptions.get(type - (Utils.MIN_CHAR - 1));
	}

	@Override
	public int stringToType(String typeName) {
		return typeName.toCharArray()[0];
	}

	@Override
	public boolean isValidType(int type) {
		return (type >= Utils.MIN_CHAR && type <= Utils.MAX_CHAR) || type == 0;
	}

	@Override
	public boolean isValidTypeName(String typeName) {
		return typeName.length() == 1 && isValidType(stringToType(typeName));
	}

	@Override
	public List<Integer> getValidTypes() {
		return Collections.unmodifiableList(validTypes);
	}

	public static AlphabetTerminals getInstance(){
		if (instance == null){
			instance = new AlphabetTerminals();
		}
		return instance;
	}
}

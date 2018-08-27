package swp.grammar;

import java.io.Serializable;
import java.util.*;

import swp.SWPException;
import swp.lexer.TerminalSet;
import swp.parser.lr.*;
import swp.util.*;

/**
 * Allows the simple creation of grammars.
 *
 * In this class integers are treated as terminals (their ids) and strings are treated as non terminals.
 */
public class GrammarBuilder implements Serializable {

	private Set<String> usedNonTerminals = new HashSet<>();
	private Set<Integer> usedTerminals = new HashSet<>();
	private List<Object[]> productions = new ArrayList<>();
	private Map<Integer, SerializableFunction<ListAST, BaseAST>> reduceActions = new HashMap<>();
	public final TerminalSet alphabet;
	/**
	 * For each non terminal A the last number of the additional non terminal A#NUMBER.
	 * Used to create new non terminals. Assumes that "#" isn't used anywhere in a non terminal id.
	 */
	private Map<String, Integer> currentNumForNonTerminal = new HashMap<>();
	private static final String additionalNumSeparator = "#";
	private int additionalNonTerminalCounter = 0;

	public GrammarBuilder(TerminalSet alphabet) {
		this.alphabet = alphabet;
	}

	/**
	 * Creates a new non terminal. It's name starts with the passed non terminals name.
	 * @param nonTerminal name of non terminal
	 *
	 * @return new non terminal
	 */
	public String createNewNonTerminal(String nonTerminal){
		if (nonTerminal.contains(additionalNumSeparator)){
			nonTerminal = nonTerminal.split(additionalNumSeparator)[1];
		}
		int newNumber = currentNumForNonTerminal.getOrDefault(nonTerminal, -1) + 1;
		currentNumForNonTerminal.put(nonTerminal, newNumber);
		return nonTerminal + additionalNumSeparator + newNumber;
	}

	/**
	 * Creates a new random non terminal.
	 *
	 * @return new non terminal
	 */
	public String createNewNonTerminal(){
		return additionalNumSeparator + additionalNonTerminalCounter++;
	}

	/**
	 * Creates rules that match the passed symbols many or zero times.
	 *
	 * @param args passed symbols
	 * @return new symbols that represent this construct
	 */
	public Object[] star(Object... args){
		return minimal(0, args);
	}

	/**
	 * Returns it's arguments.
	 */
	public Object[] combine(Object... args){
		return args;
	}

	/**
	 * Creates rules that match the passed symbols at least `minAppearances` times
	 *
	 * @param minAppearances minimum number of matches
	 * @param args passed symbols
	 * @return new symbols that represent this construct
	 */
	public Object[] minimal(int minAppearances, Object... args){
		String newNonTerminal = createNewNonTerminal();
		Object[] arr_ = Utils.appendToArray(args, newNonTerminal);
		add(newNonTerminal, arr_).action(asts -> {
			BaseAST left = asts.get(0);
			ListAST ast = new ListAST();
			if (asts.get(1) instanceof ListAST) {
				ListAST right = (ListAST) asts.get(1);
				ast.add(left);
				ast.append(right);
			} else {
				ast = asts;
			}
			return ast;
		});
		if (minAppearances != 0) {
			Object[] arr = new Object[minAppearances * args.length];
			for (int i = 0; i < arr.length; i++) {
				arr[i] = args[i % args.length];
			}
			add(newNonTerminal, args).action(asts -> {
				return new ListAST(asts);
			});
		} else {
			add(newNonTerminal, "").action(asts -> {
				return new ListAST();
			});
		}
		return new Object[]{newNonTerminal};
	}

	public Object[] or(Object... args){
		String newNonTerminal = createNewNonTerminal();
		for (Object arg : args){
			add(newNonTerminal, arg);
		}
		return new Object[]{newNonTerminal};
	}

	public Object[] orWithActions(Pair<Object[], SerializableFunction<ListAST, BaseAST>>... parts){
		Object[] ret = new Object[parts.length];
		String newNonTerminal = createNewNonTerminal();
		for (int i = 0; i < ret.length; i++){
			add(newNonTerminal, parts[i].first);
			action(parts[i].second);
			ret[i] = newNonTerminal;
		}
		return new Object[]{newNonTerminal};
	}

	public Object[] maybe(Object... args){
		String newNonTerminal = createNewNonTerminal();
		add(newNonTerminal, args).action(ListAST::new);
		add(newNonTerminal, "").action(asts -> new ListAST());
		return new Object[]{newNonTerminal};
	}

	public Object[] string(String str){
		Object[] arr = new Object[str.length()];
		char[] chars = str.toCharArray();
		for (int i = 0; i < chars.length; i++){
			arr[i] = chars[i];
		}
		return arr;
	}

	/**
	 * Terimnal range including 'end'
	 * @param start
	 * @param end
	 * @return
	 */
	public Object[] range(int start, int end){
		String newNonTerminal = createNewNonTerminal();
		for (int i = start; i <= end; i++){
			add(newNonTerminal, i);
		}
		return new Object[]{newNonTerminal};
	}

	public Object[] single(int type){
		return new Object[]{type};
	}

	/**
	 * Adds a new production (and the used terminals and non terminals).
	 *
	 * The entries of the right hand side are
	 *  - strings: names of non terminals
	 *  - integers: ids of terminals
	 *  - "": equivalent to ε
	 *
	 * @param left name of the defining non terminal on the left hand side of the production
	 * @param right right hand side of the production
	 */
	public GrammarBuilder add(String left, Object... right){
		right = flatten(right);
		List<Object> prod = new ArrayList<>(right.length);
		assert isTerminal(left);
		if (alphabet.isValidTypeName(left)){
			throw new SWPException(String.format("Ambiguity while building the grammar: '%s' is the name of a terminal and therefore " +
					"can't be used as a non terminal name", left));
		}
		usedNonTerminals.add(left);
		prod.add(left);
		if (right.length == 0){
			prod.add(null);
		}
		for (int i = 0; i < right.length; i++){
			Object obj = right[i];
			if (obj instanceof String){
				if (((String) obj).isEmpty()){
					prod.add("");
				} else {
					String str = (String)obj;
					if (alphabet.isValidTypeName(str)){
						int terminal = alphabet.stringToType(str);
						prod.add(terminal);
						usedTerminals.add(terminal);
					} else {
						prod.add(obj);
						usedNonTerminals.add(str);
					}
				}
			} else if (obj instanceof Integer || obj instanceof Character){
				int id;
				if (obj instanceof Character){
					id = (int)((char)obj);
				} else {
					id = (int)obj;
				}
				assert alphabet.isValidType(id);
				usedTerminals.add(id);
				prod.add(id);
			} else {
				throw new Error("Right part of production object list has unsupported type");
			}
		}
		productions.add(prod.toArray());
		return this;
	}

	private Object[] flatten(Object[] arr){
		ArrayList<Object> list = flattenToList(arr);
		return list.toArray();
	}

	private ArrayList<Object> flattenToList(Object[] arr){
		ArrayList<Object> ret = new ArrayList<>();
		for (Object sub : arr){
			if (sub instanceof Integer || sub instanceof String || sub instanceof Character){
				ret.add(sub);
			} else {
				ret.addAll(flattenToList((Object[])sub));
			}
		}
		return ret;
	}

	/**
	 * Sets the action for the last added production.
	 *
	 * @param action method executed when the production is matched
	 * @return self
	 */
	public GrammarBuilder action(SerializableFunction<ListAST, BaseAST> action){
		reduceActions.put(productions.size() - 1, action);
		return this;
	}

	private boolean isTerminal(Object obj){
		return obj instanceof String && ((String)obj).length() > 0;
	}

	public Grammar toGrammar(String startNonTerminal) {
		Map<String, NonTerminal> nonTerminals = new HashMap<>();
		Map<Integer, Terminal> terminals = new HashMap<>();
		List<Production> productions = new ArrayList<>();
		Epsilon epsilon = new Epsilon();
		for (Integer usedTerminal : usedTerminals) {
			terminals.put(usedTerminal, new Terminal(usedTerminal, alphabet));
		}
		int id = 0;
		for (String nonTerminal : usedNonTerminals) {
			nonTerminals.put(nonTerminal, new NonTerminal(id++, nonTerminal));
		}
		id = 0;
		for (Object[] prod : this.productions) {
			NonTerminal left = nonTerminals.get(prod[0]);
			List<Symbol> right = new ArrayList<>();
			for (int i = 1; i < prod.length; i++) {
				Object obj = prod[i];
				if (obj instanceof String) {
					if (((String) obj).isEmpty()) {
						right.add(epsilon);
					} else {
						right.add(nonTerminals.get(obj));
					}
				} else {
					right.add(terminals.get(obj));
				}
			}
			Production production = new Production(id++, left, right);
			left.addProduction(production);
			productions.add(production);
		}
		Grammar g = new Grammar(alphabet, new HashSet<NonTerminal>(nonTerminals.values()),
				nonTerminals.get(startNonTerminal), productions);
		if (!g.getStart().hasEOFEndedProduction()) {
			g = g.insertStartNonTerminal();
		}
		for (int prod : reduceActions.keySet()) {
			g.setReduceAction(prod, reduceActions.get(prod));
		}
		return g;
	}

	public NonTerminalOrTerminal convert(Object o){
		if (o instanceof NonTerminalOrTerminal){
			return (NonTerminalOrTerminal)o;
		}
		return new NonTerminalOrTerminal(o);
	}

	public NonTerminalOrTerminal[] convert(Object[] os){
		boolean shouldConvert = false;
		for (Object o : os){
			if (!(o instanceof NonTerminalOrTerminal)){
				shouldConvert = true;
				break;
			}
		}
		if (shouldConvert){
			NonTerminalOrTerminal[] arr = new NonTerminalOrTerminal[os.length];
			for (int i = 0; i < arr.length; i++){
				arr[i] = new NonTerminalOrTerminal(os[i]);
			}
			return arr;
		}
		return (NonTerminalOrTerminal[])os;
	}

	public class NonTerminalOrTerminal {
		public final boolean isNonTerminal;
		public final String nonTerminal;
		public final int terminalType;

		public NonTerminalOrTerminal(Object o){
			if (o instanceof String){
				isNonTerminal = true;
				nonTerminal = (String)o;
				terminalType = -1;
			} else {
				isNonTerminal = false;
				terminalType = (int)o;
				nonTerminal = "";
			}
		}

		@Override
		public String toString() {
			if (isNonTerminal){
				return "<non terminal " + nonTerminal + ">";
			} else {
				return alphabet.typeToString(terminalType);
			}
		}
	}
}

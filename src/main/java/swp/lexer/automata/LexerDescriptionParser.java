package swp.lexer.automata;

import java.io.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import swp.SWPException;
import swp.grammar.*;
import swp.lexer.*;
import swp.parser.lr.*;
import swp.util.*;

/**
 * Parser for a lexer description.
 */
public class LexerDescriptionParser {

	private LRParserTable parserTable = null;
	private Table basicLexerTable;
	public Automaton automaton = new Automaton();


	public LexerDescriptionParser() {
		init();
	}

	private void init(){
		if (parserTable != null){
			return;
		}
		Automaton a = new Automaton();

		// basic
		a.addTerminal("EOF", mn -> mn.append('\0'));
		a.addTerminal("L_PAREN", "(").addTerminal("R_PAREN", ")") // parens and brackets
				.addTerminal("L_CURLY", "{").addTerminal("R_CURLY", "}");
		a.addTerminal("COMMA", ",")
				.addTerminal("MAYBE", "?")
				.addTerminal("EQUALS", "=")
				.addTerminal("PLUS", "+")
				.addTerminal("STAR", "*")
				.addTerminal("OR", "|");
		a.addTerminal("WS", mn -> mn.create(" ").or(mn.create("#").append(mn.create().appendExcluding('\n').star())));
		a.addTerminal("LBRK", mn -> mn.create("\n").or(mn.create(";")));
		a.addTerminal("WILDCARD", ".");

		// escaped character
		a.addMacro("$escaped", ms-> ms.create().append("\\").appendAllChars());
		a.addTerminal("ESCAPED", ms-> ms.use("$escaped"));
		a.addMacro("$range_char", ms -> ms.use("$escaped").or(ms.create('0', 'Z')).or(ms.create('a', 'z'))
				.or('_', '+', '/', '.', '(', ')', '<', '>', '=', '?', '\'', '|', '{', '}', '~', '!', '"',
						'$', '%', '&', '`', '/', ':', ';', '#', ',', '*'));
		a.addTerminal("CHAR", ms -> ms.create('a', 'z')
				.or('_', '/', '<', '>', '\'', '~', '!', '"',
						'$', '%', '&', '`', '/', ':', ','));
		a.addMacro("$range", ms -> ms.use("$range_char").append("-").append(ms.use("$range_char")));
		a.addTerminal("CHAR_RANGE", ms ->
				ms.create().append("[").append(ms.create("^").maybe()).append(ms.use("$range").or(ms.create().append(ms.use("$escaped").or(ms.use("$range_char")))).or(ms.use("$range_char")).star()).append("]")
		);
		a.addTerminal("NUMBER", mn -> mn.append('0', '9').plus());
		a.addMacro("$upper_case_char", mn -> mn.append('A', 'Z'));
		a.addTerminal("ID", mn -> mn.or(
				mn.use("$upper_case_char").append(mn.use("$upper_case_char").or(mn.create('0', '9')).or(mn.create("_")).star()),
				mn.create("$").append(mn.create('0', '9').or(mn.create('A', 'Z'), mn.create('a', 'z'), mn.create("_")).plus())
		));

		//a.toImage("lexer_description_lexer_automaton", "svg");
		Automaton determ = a.toDeterministicVersion();
		//determ.toImage("lexer_description_lexer_determ_automaton", "svg");
		basicLexerTable = determ.toTable().compress();
		//System.out.println(basicLexerTable);
		GrammarBuilder builder = new GrammarBuilder(basicLexerTable.terminalSet);

		builder.add("code", builder.maybe("line", builder.star("LBRK", builder.maybe("code"))));
		builder.add("line", "ID", "EQUALS", "expression").action(list -> {
			String matched = list.get(0).getMatchedString();
			MetaState metaState = ((MetaStateNode)list.get(2)).metaState;
			if (matched.startsWith("$")){
				automaton.addMacro(matched.substring(1), metaState);
			} else {
				automaton.addTerminal(matched, metaState);
			}
			return null;
		});

		builder.add("expression", builder.orWithActions(
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("expr", "OR", "expression"),
						list -> new MetaStateNode(((MetaStateNode)list.get(0)).metaState.or(((MetaStateNode)list.get(2)).metaState))
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("expr"),
						list -> list.get(0))
		));
		builder.add("expr", builder.orWithActions(
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.star("expression"),
						list -> new MetaStateNode(astsToMetaState(list.getAll("meta"), MetaState::append))
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("expr", "STAR"),
						list -> new MetaStateNode(((MetaStateNode)list.getAll("meta").get(0)).metaState.star())
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("expr", "MAYBE"),
						list -> new MetaStateNode(((MetaStateNode)list.getAll("meta").get(0)).metaState.maybe())
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("expr", "PLUS"),
						list -> new MetaStateNode(((MetaStateNode)list.getAll("meta").get(0)).metaState.plus())
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("expr", "number_range"),
						list -> {
							MetaState ms = ((MetaStateNode)list.get(0)).metaState;
							Pair<Integer, Integer> range = ((NumberRangeNode)list.get(1)).value;
							MetaState ret;
							switch (range.second){
								case -1:
									ret = ms.range(range.first);
									break;
								case -2:
									ret = ms.rangeOpenEnd(range.first);
									break;
								default:
									ret = ms.range(range.first, range.second);
							}
							return new MetaStateNode(ret);
						}
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("term"),
						list -> ((MetaStateNode)list.get(0)))
				)
		);

		builder.add("number_range", builder.orWithActions(
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("L_CURLY", "NUMBER", "R_CURLY"),
						list -> new NumberRangeNode(new Pair<Integer, Integer>(Integer.parseInt(list.get(1).getMatchedString()), -1))
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("L_CURLY", "NUMBER", "COMMA", "R_CURLY"),
						list -> new NumberRangeNode(new Pair<Integer, Integer>(Integer.parseInt(list.get(1).getMatchedString()), -2))
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("L_CURLY", "NUMBER", "COMMA", "NUMBER", "R_CURLY"),
						list -> {
							//System.out.println("### " + list.getMatchedString());
							return new NumberRangeNode(new Pair<Integer, Integer>(Integer.parseInt(list.get(1).getMatchedString()),
									Integer.parseInt(list.get(3).getMatchedString())));
						}
				)
		));

		builder.add("term", builder.orWithActions(
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(
						builder.or("ESCAPED", "CHAR", "EQUALS", "NUMBER"),
						list -> new MetaStateNode(parseEscaped(list.getMatchedString()))
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(
						builder.combine("CHAR_RANGE"),
						list -> new MetaStateNode(parseCharRange(list.getMatchedString()))
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(
						builder.combine("WILDCARD"),
						list -> new MetaStateNode(automaton.createMetaNode().appendAllCharsWoEOF())
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(
						builder.combine("ID"),
						list -> {
							String matched = list.getMatchedString();
							if (matched.startsWith("$")){
								return new MetaStateNode(automaton.createMetaNode().use(matched.substring(1)));
							}
							return new MetaStateNode(automaton.createMetaNode().append(matched));
						}
				),
				new Pair<Object[], SerializableFunction<ListAST, BaseAST>>(builder.combine("L_PAREN", "expression", "R_PAREN"),
						list -> (MetaStateNode)list.getAll("meta").get(0)
				)
		));
		//builder.add("A", "B", "C").add("B", '4').add("C", "").add("C", '3');
		Graph.isLALR = true;
		Grammar g = builder.toGrammar("code");
		//System.out.println(g.longDescription());
		//System.out.println(g.longDescription());
		//System.out.println(g.longDescription());
		//System.out.println("First_1 set = " + g.calculateFirst1Set());
		Graph graph = Graph.createFromGrammar(g);
		//System.out.println(graph);
		//graph.toImage("lexer_description_parser", "svg");
		parserTable = graph.toParserTable();

		//Utils.repl(str -> createLexer(str));
	}

	private MetaState parseEscaped(String st){
		// Based on https://gist.github.com/uklimaschewski/6741769
		StringBuilder sb = new StringBuilder(st.length());
		MetaState ms = automaton.createMetaNode();
		for (int i = 0; i < st.length(); i++) {
			char ch = st.charAt(i);
			if (ch == '\\') {
				char nextChar = (i == st.length() - 1) ? '\\' : st
						.charAt(i + 1);
				// Octal escape?
				if (nextChar >= '0' && nextChar <= '7') {
					String code = "" + nextChar;
					i++;
					if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
							&& st.charAt(i + 1) <= '7') {
						code += st.charAt(i + 1);
						i++;
						if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
								&& st.charAt(i + 1) <= '7') {
							code += st.charAt(i + 1);
							i++;
						}
					}
					sb.append((char) Integer.parseInt(code, 8));
					continue;
				}
				switch (nextChar) {
					case '\\':
						ch = '\\';
						break;
					case 'b':
						ch = '\b';
						break;
					case 'f':
						ch = '\f';
						break;
					case 'n':
						ch = '\n';
						break;
					case 'r':
						ch = '\r';
						break;
					case 't':
						ch = '\t';
						break;
					case '\"':
						ch = '\"';
						break;
					case '\'':
						ch = '\'';
						break;
					case 'd':
						return ms.create('0', '9');
					case 'D':
						return ms.appendExcluding(new Pair<Integer, Integer>((int)'0', (int)'9'));
					case 's':
						ms.or('\t', '\r', '\n', '\f', ' ');
					case 'S':
						return ms.appendExcluding('\t', '\n', '\r', '\f', ' ');
					case 'w':
						return ms.or(ms.create('a', 'z'), ms.create('A', 'Z'), ms.create('0','9'), ms.create("_"));
					case 'W':
						return ms.appendExcluding(
								new Pair<Integer, Integer>((int)'a', (int)'z'),
								new Pair<Integer, Integer>((int)'A', (int)'Z'),
								new Pair<Integer, Integer>((int)'0', (int)'9'),
								new Pair<Integer, Integer>((int)'_', (int)'_'));
					// Hex Unicode: u????
					case 'u':
						if (i >= st.length() - 5) {
							ch = 'u';
							break;
						}
						int code = Integer.parseInt(
								"" + st.charAt(i + 2) + st.charAt(i + 3)
										+ st.charAt(i + 4) + st.charAt(i + 5), 16);
						sb.append(Character.toChars(code));
						i += 5;
						continue;
					default:
						ch = nextChar;
						break;
				}
				i++;
			}
			sb.append(ch);
		}
		return ms.append(sb.toString());
	}

	private MetaState parseCharRange(String charRangeExpr){
		MetaState ms = automaton.createMetaNode();
		int start = charRangeExpr.charAt(1) == '^' ? 2 : 1;
		charRangeExpr = charRangeExpr.substring(start, charRangeExpr.length() - 1);
		String unescaped = unescapeCharRange(charRangeExpr);
		Set<Integer> chars = new HashSet<>();
		for (int i = 0; i < unescaped.length(); i++){
			if (i < unescaped.length() - 2 && unescaped.charAt(i + 1) == '-'){
				for (int c = unescaped.codePointAt(i); c <= unescaped.codePointAt(i + 2); c++){
					chars.add(c);
				}
				i += 2;
			} else {
				chars.add(unescaped.codePointAt(i));
			}
		}
		if (start == 2) { // with '^'
			Integer[] arr = new Integer[Utils.MAX_CHAR - Utils.MIN_CHAR - chars.size()];
			int arrIndex = 0;
			for (int i = 1; i <= Utils.MAX_CHAR; i++){
				if (!chars.contains(i)){
					arr[arrIndex] = i;
					arrIndex++;
				}
			}
			return ms.or(arr);
		}
		return ms.or(chars.toArray(new Integer[]{}));
	}

	private String unescapeCharRange(String st){
		//Based on https://gist.github.com/uklimaschewski/6741769

		StringBuilder sb = new StringBuilder(st.length());

		loop: for (int i = 0; i < st.length(); i++) {
			char ch = st.charAt(i);
			if (ch == '\\') {
				char nextChar = (i == st.length() - 1) ? '\\' : st
						.charAt(i + 1);
				// Octal escape?
				if (nextChar >= '0' && nextChar <= '7') {
					String code = "" + nextChar;
					i++;
					if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
							&& st.charAt(i + 1) <= '7') {
						code += st.charAt(i + 1);
						i++;
						if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
								&& st.charAt(i + 1) <= '7') {
							code += st.charAt(i + 1);
							i++;
						}
					}
					sb.append((char) Integer.parseInt(code, 8));
					continue;
				}
				switch (nextChar) {
					case '\\':
						ch = '\\';
						break;
					case 'b':
						ch = '\b';
						break;
					case 'f':
						ch = '\f';
						break;
					case 'n':
						ch = '\n';
						break;
					case 'r':
						ch = '\r';
						break;
					case 't':
						ch = '\t';
						break;
					case '\"':
						ch = '\"';
						break;
					case '\'':
						ch = '\'';
						break;
					case 'd':
						IntStream.range('0', '9').forEach(sb::append);
						break;
					case 's':
						sb.append("\t\r\n\f ");
						break;
					case 'w':
						IntStream.range('0', '9').forEach(sb::append);
						IntStream.range('a', 'z').forEach(sb::append);
						IntStream.range('A', 'Z').forEach(sb::append);
						sb.append("_");
						break;
					// Hex Unicode: u????
					case 'u':
						if (i >= st.length() - 5) {
							ch = 'u';
							break;
						}
						int code = Integer.parseInt(
								"" + st.charAt(i + 2) + st.charAt(i + 3)
										+ st.charAt(i + 4) + st.charAt(i + 5), 16);
						sb.append(Character.toChars(code));
						i += 5;
						continue;
					default:
						throw new AutomatonConstructionError(String.format("Unknown meta character \\%s in char range", nextChar));
				}
				i++;
			}
			sb.append(ch);
		}
		return sb.toString();
	}

	private List<MetaState> astsToMetaStates(List<BaseAST> asts){
		List<MetaState> ret = new ArrayList<>();
		for (BaseAST ast : asts){
			ret.add(((MetaStateNode)ast).metaState);
		}
		return ret;
	}

	private MetaState astsToMetaState(List<BaseAST> asts, BiFunction<MetaState, MetaState, MetaState> combineFn){
		MetaState metaState = automaton.createMetaNode();
		for (MetaState state : astsToMetaStates(asts)){
			metaState = combineFn.apply(metaState, state);
		}
		return metaState;
	}

	private Lexer createLexer(String input){
		return new AutomatonLexer(basicLexerTable, input, new int[]{' '}, new int[]{basicLexerTable.terminalSet.stringToType("WS")});
	}

	public void toImage(String lexerGrammar, String name, String type){
		toAutomaton(lexerGrammar);
		automaton.toImage(name, type);
	}

	public Table eval(String input){
		toAutomaton(input);
		//automaton.toImage("lexer_eval_non_determ", "svg");
		return automaton.toDeterministicVersion().toTable().compress();
	}

	public Table eval(List<Pair<String, String>> terminals){
		return eval(terminals, true);
	}

	public Table eval(List<Pair<String, String>> terminals, boolean compress){
		automaton.clear();
		automaton.addTerminal("EOF", "\0");
		for (Pair<String, String> terminal : terminals) {
			try {
				Lexer lex = createLexer(terminal.first + " = " + terminal.second);
				LRParser parser = new LRParser(parserTable.grammar, lex, parserTable);
				parser.parse();
			} catch (SWPException exp){
				throw new SWPException(String.format("In description of terminal %s (%s): %s",
						terminal.first, Utils.toPrintableRepresentation(terminal.second),
						exp.getMessage()));
			}
		}
		Table table = automaton.toDeterministicVersion().toTable();
		if (compress){
			return table.compress();
		}
		return table;
	}

	private Automaton toAutomaton(String lexerGrammar){
		automaton.clear();
		automaton.addTerminal("EOF", "\0");
		Lexer lex = createLexer(lexerGrammar);
		LRParser parser = new LRParser(parserTable.grammar, lex, parserTable);
		parser.parse();
		return automaton;
	}

	public void repl(){
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String line = "";
		System.out.print("");
		try {
			while (!(line = input.readLine()).equals("")){
				try {
					automaton.clear();
					automaton.addTerminal("EOF", "\0");
					Lexer lex = createLexer(line);
					System.out.print("=> Tokens: ");
					do {
						System.out.print(lex.next() + " ");
					} while (lex.cur().type != 0);
					System.out.println("\n");
					lex = createLexer(line);
					LRParser parser = new LRParser(parserTable.grammar, lex, parserTable);
					parser.parse();
					automaton.toDeterministicVersion().toImage("lexer_repl", "svg");
					System.out.print("=> " + automaton.toTable().compress());
					System.out.print("\n");
				} catch (Error ex){
					ex.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class MetaStateNode extends BaseAST {

		public final MetaState metaState;

		public MetaStateNode(MetaState metaState){
			this.metaState = metaState;
		}

		@Override
		public List<Token> getMatchedTokens() {
			return new ArrayList<>();
		}

		@Override
		public String toString() {
			return "" + metaState;
		}

		@Override
		public String type() {
			return "meta";
		}
	}

	private static class NumberRangeNode extends CustomAST<Pair<Integer, Integer>> {

		public NumberRangeNode(Pair<Integer, Integer> value) {
			super(value);
		}

		@Override
		public String type() {
			return "number_range";
		}
	}
}

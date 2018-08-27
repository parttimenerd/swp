package swp.parser.lr;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.*;

import swp.*;
import swp.grammar.*;
import swp.lexer.Lexer;
import swp.lexer.automata.*;
import swp.util.*;

public class Generator {

	public final static boolean OUTPUT_GRAPHS_IF_CACHED = false;

	private static Cache<String, Pair<Table, LRParserTable>> cache = new Cache<>(10);

	public static <E extends Enum<E> & LexerTerminalEnum> Generator getCachedIfPossible(
			String id, Class<E> lexerDescription,
			String[] ignoredTerminals,
			Consumer<ExtGrammarBuilder> parserBuilder,
			String parserStartSymbol) {
		return getCachedIfPossible(id, lexerDescription, ignoredTerminals, parserBuilder, parserStartSymbol, (g) -> {});
	}

	public static <E extends Enum<E> & LexerTerminalEnum> Generator getCachedIfPossible(
			String id, Class<E> lexerDescription,
			String[] ignoredTerminals,
	        Consumer<ExtGrammarBuilder> parserBuilder,
	        String parserStartSymbol,
			Consumer<Grammar> grammarConsumer) {
		Function<LexerDescriptionParser, Table> lexerBuilder = (parser) -> {
			Field eof = null;
			if (!lexerDescription.getEnumConstants()[0].name().equals("EOF")){
				throw new SWPException("A lexer enum has to declare an \"EOF\" field as it's first field");
			}
			E[] enumConstants = lexerDescription.getEnumConstants();
			List<Pair<String, String>> terminalDescriptions = new ArrayList<>();
			for (E enumConstant : enumConstants) {
				if (!enumConstant.name().equals("EOF")){
					terminalDescriptions.add(new Pair<>(enumConstant.name(), enumConstant.getTerminalDescription()));
				}
			}
			return parser.eval(terminalDescriptions);
		};
		return getCachedIfPossible(id, lexerBuilder, ignoredTerminals, parserBuilder, parserStartSymbol, grammarConsumer);
	}

	public static Generator getCachedIfPossible(String id, String lexerDescription,
	                                            String[] ignoredTerminals,
	                                            Consumer<ExtGrammarBuilder> parserBuilder,
	                                            String parserStartSymbol,
	                                            Consumer<Grammar> grammarConsumer){
		return getCachedIfPossible(id, parser -> parser.eval(lexerDescription), ignoredTerminals,
				parserBuilder, parserStartSymbol, grammarConsumer);
	}

	public static Generator getCachedIfPossible(String id, String lexerDescription,
	                                            String[] ignoredTerminals,
	                                            Consumer<ExtGrammarBuilder> parserBuilder,
	                                            String parserStartSymbol){
		return getCachedIfPossible(id, parser -> parser.eval(lexerDescription), ignoredTerminals,
				parserBuilder, parserStartSymbol, (g) -> {});
	}

	private static Generator getCachedIfPossible(String id, Function<LexerDescriptionParser, Table> lexerBuilder,
	                                            String[] ignoredTerminals,
	                                            Consumer<ExtGrammarBuilder> parserBuilder,
	                                            String parserStartSymbol,
	                                             Consumer<Grammar> grammarConsumer){
		if (id == null){
			return new Generator(generatePair(lexerBuilder, ignoredTerminals, parserBuilder, parserStartSymbol,
					grammarConsumer));
		}
		Pair<Table, LRParserTable> pair = cache.getIfPresent(id);
		if (pair == null){
			if (Config.cacheInFile() && !id.isEmpty()) {
				if (doFilesForIdExist(id)) {
					try {
						pair = load(id);
					} catch (ClassNotFoundException | IOException e) {
						pair = null;
					}
				}
				if (pair == null){
					pair = generatePair(lexerBuilder, ignoredTerminals, parserBuilder, parserStartSymbol,
							OUTPUT_GRAPHS_IF_CACHED, id, grammarConsumer);
					try {
						store(id, pair);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} else {
				pair = generatePair(lexerBuilder, ignoredTerminals, parserBuilder, parserStartSymbol, grammarConsumer);
			}
			cache.put(id, pair);
		}
		return new Generator(cache.getIfPresent(id));
	}

	private static Pair<Table, LRParserTable> generatePair(Function<LexerDescriptionParser, Table> lexerBuilder,
	                                                       String[] ignoredTerminals,
	                                                       Consumer<ExtGrammarBuilder> parserBuilder,
	                                                       String parserStartSymbol,
	                                                       Consumer<Grammar> grammarConsumer) {
		return generatePair(lexerBuilder, ignoredTerminals, parserBuilder, parserStartSymbol, false, "",
				grammarConsumer);
	}

	private static Pair<Table, LRParserTable> generatePair(Function<LexerDescriptionParser, Table> lexerBuilder,
	                                                       String[] ignoredTerminals,
	                                                       Consumer<ExtGrammarBuilder> parserBuilder,
	                                                       String parserStartSymbol,
	                                                       boolean outputGraph,
	                                                       String id,
	                                                       Consumer<Grammar> grammarConsumer) {
		Pair<File, File> fileNames = getFilePair(id);
		LexerDescriptionParser parser = new LexerDescriptionParser();
		Table table = lexerBuilder.apply(parser);
		if (outputGraph) {
			parser.automaton.toImage(fileNames.first.getAbsolutePath(), "svg");
			parser.automaton.toDeterministicVersion().toImage(fileNames.first.getAbsolutePath() + "_determ", "svg");
		}
		ExtGrammarBuilder extBuilder = new ExtGrammarBuilder(parser.automaton.terminalSet);
		parserBuilder.accept(extBuilder);
		Grammar grammar = extBuilder.toGrammar(parserStartSymbol);
		grammarConsumer.accept(grammar);
		Graph lrGraph = Graph.createFromGrammar(grammar);
		if (outputGraph) {
			lrGraph.toImage(fileNames.second.getAbsolutePath(), "svg");
		}
		LRParserTable parserTable = lrGraph.toParserTable();
		parserTable._ignoredTerminals = new int[ignoredTerminals.length];
		for (int i = 0; i < ignoredTerminals.length; i++) {
			parserTable._ignoredTerminals[i] = table.terminalSet.stringToType(ignoredTerminals[i]);
		}
		return new Pair<>(table, parserTable);
	}

	private static Pair<File, File> getFilePair(String id) {
		String prefix = Config.getTmpDir() + "/" + id + "_";
		return new Pair<>(new File(prefix + "lexer.ser"), new File(prefix + "parser.ser"));
	}

	private static boolean doFilesForIdExist(String id){
		Pair<File, File> pair = getFilePair(id);
		return pair.first.exists() && pair.second.exists();
	}

	private static void store(String id, Pair<Table, LRParserTable> pair) throws IOException {
		Pair<File, File> fileNames = getFilePair(id);
		fileNames.first.getParentFile().mkdirs();
		fileNames.second.getParentFile().mkdirs();
		try (ObjectOutput oo = new ObjectOutputStream(new FileOutputStream(fileNames.first))) {
			oo.writeObject(pair.first);
		}
		try (ObjectOutput oo = new ObjectOutputStream(new FileOutputStream(fileNames.second))) {
			oo.writeObject(pair.second);
		}
	}

	private static Pair<Table, LRParserTable> load(String id) throws ClassNotFoundException, IOException {
		Pair<File, File> fileNames = getFilePair(id);
		Table table = null;
		LRParserTable parserTable = null;
		try (ObjectInput oi = new ObjectInputStream(new FileInputStream(fileNames.first))) {
			table = (Table)oi.readObject();
		}
		try (ObjectInput oi = new ObjectInputStream(new FileInputStream(fileNames.second))) {
			parserTable = (LRParserTable) oi.readObject();
		}
		return new Pair<>(table, parserTable);
	}

	public static interface LexerTerminalEnum {
		String getTerminalDescription();
	}

	private Table lexerTable;
	private LRParserTable parserTable;

	private Generator(Table lexerTable, LRParserTable parserTable){
		this.lexerTable = lexerTable;
		this.parserTable = parserTable;
	}

	private Generator(Pair<Table, LRParserTable> pair){
		this(pair.first, pair.second);
	}

	public BaseAST parse(String input){
		Lexer lexer = createLexer(input);
		LRParser parser = new LRParser(lexer, parserTable);
		return parser.parse();
	}

	public Lexer createLexer(String input){
		return new AutomatonLexer(lexerTable, input, new int[]{}, parserTable._ignoredTerminals);
	}
}

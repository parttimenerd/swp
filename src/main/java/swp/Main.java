package swp;

import swp.grammar.*;
import swp.grammar.random.SentenceGenerator;
import swp.lexer.alphabet.AlphabetTerminals;
import swp.parser.lr.*;

public class Main {

    public static void main(String[] args) {
	    //AlphabetLexer.repl();

	    GrammarBuilder builder = new GrammarBuilder(AlphabetTerminals.getInstance());
		builder.add("AA", '(', "AA", ')')
				.add("AA", 'c');


	    Grammar g2 = builder.toGrammar("AA");

	    new DiffGraph(g2, "/tmp/abc").createGIF(1);

	   /* GrammarBuilder builder = new GrammarBuilder(AlphabetTerminals.getInstance());
	    builder.add("AB", '(', "AB", ')')
			    .add("AB", "BB")
			    .add("BB", "AB", '4', "BB")
	            .add("BB", "b");

	    Grammar g2 = builder.toGrammar("AB");
	    System.out.println(g2.calculateSingleProductionNonTerminals());
	    SentenceGenerator gen2 = new SentenceGenerator(g2);
	    while (1 == Math.abs(1)) System.out.println(gen2.generateRandomSentence());
*/
	    GrammarBuilder builder2 = new GrammarBuilder(AlphabetTerminals.getInstance());
	    builder2.add("AB", '(', "BB", ')')
	            .add("BB", "BB", "BB")
	            .add("BB", 'c');
	    Grammar g3 = builder2.toGrammar("AB");
	    SentenceGenerator gen = new SentenceGenerator(g3);
	    System.out.println(gen.generateRandomSentence());
	  //  new DiffGraph(g2, "/tmp/lalr").createPNGs().createMP4(1);

	    Graph.isLALR = false;

	   // new DiffGraph(g2, "/tmp/lr").createMP4(1);

	    //new SimpleCalculator4();
	    /*
	    System.out.println(g2.calculateFollow1Set());
	    LLParserTable llParserTable = LLParserTable.fromGrammar(g2);
	    System.out.println(llParserTable);
	    Lexer lex = new AlphabetLexer("41433", new int[]{});
	    LLParser llParser = new LLParser(lex, llParserTable);
	    System.out.println(llParser.parse());*/
	    /*GrammarBuilder builder = new GrammarBuilder(new AlphabetTerminals());
	    builder.add("start", "id", "4").add("id", '3');
	    Graph.isLALR = true;
	    Grammar g = builder.toGrammar("start");
	    System.out.println(g.longDescription());
	    System.out.println("First_1 set = " + g.calculateFirst1Set());
	    Graph graph = Graph.createFromGrammar(g);
	    System.out.println(graph);
	    graph.toImage("test", "svg");
	    LRParserTable table = graph.toExtParserTable();
	    Lexer lex = new AlphabetLexer("4", new int[]{});
	    //Lexer lex = new AlphabetLexer("4");
	    LRParser parser = new LRParser(g, lex, table);
	    System.out.println(table.toString(lex.getTerminalSet()));
	    System.out.println(parser.parse().toPrettyString());
	   /* System.out.println("Epsilonable = " + g.calculateEpsilonable());
	    System.out.println("g.calculateFirst1Set() = " + g.calculateFirst1Set());
	    System.out.println("g.calculateFollow1Set() = " + g.calculateFollow1Set());
	    System.out.println("g.isSLL1() = " + g.isSLL1());
	    //System.out.println("g.firstSetForNonTerminal(1, g.getNonTerminal(\"A\")) = " + g.firstSetForNonTerminal(1, g.getNonTerminal("A")));
	    System.out.println("g.getNonDeterministicVersion() = " + g.getNonDeterministicVersion());
	    System.out.println("g.calculateFirstSet(1) = " + g.calculateFirstSet(1));*/
	    /*Lexer lex = new AlphabetLexer("14");
	    EarleyParser parser = new EarleyParser(g, lex);
	    parser.parse();
	    System.out.println(parser);*/
	    //System.out.println(new SimpleCalculator().eval("100 + 10 * 0.9 / 4 / 10000000000"));
	    /*LexerBuilder lexerBuilder = new LexerBuilder(new int[]{' '});
	    lexerBuilder.add("number", builder1 -> new Object[]{builder1.maybe(builder1.or('+', '-')), "int", builder1.maybe('.', "int")});
	    lexerBuilder.add("int", builder1 -> builder1.minimal(1, builder1.range('0', '9')))
	            .add("LPAREN", builder1 -> builder1.single('('))
	            .add("RPAREN", builder1 -> builder1.single(')'))
	            .add("PLUS", builder1 -> builder1.single('+'))
			    .add("MINUS", builder1 -> builder1.single('-'))
			    .add("DIVIDE", builder1 -> builder1.single('/'))
			    .add("MULTIPLY", builder1 -> builder1.single('*'));*/
	    //Lexer lexer = lexerBuilder.toLexer("090+456");

	    /*
	    Automaton automaton = new Automaton();
	    //automaton.terminalSet.addTerminal("bla");
	    //automaton.initialMetaNode.append("hallo");
	    //automaton.initialMetaNode.plus();//.getExit().makeFinal("bla");
	    automaton.addTerminal("eof", mn -> mn.append('\0'));
	    automaton.addTerminal("plus", "+").addTerminal("minus", "-").addTerminal("multiply", "*")
			    .addTerminal("divide", "/").addTerminal("lparen", "(").addTerminal("rparen", ")");
	    automaton.addMacro("digits", mn -> mn.append('0', '9').plus());
	    automaton.addTerminal("number", mn -> {
	        return mn.append(mn.use("digits")).append(mn.create(".").append(mn.use("digits")).maybe());
	    });
	    automaton.toImage("test_automaton", "svg");
	    Automaton determ = automaton.toDeterministicVersion();
	    determ.toImage("test_determ_automaton", "svg");
	    //Automaton min = automaton.toMinimalDeterministicVersion();
	    //min.toImage("test_min_automaton", "svg");
	    Table table = determ.toTable();
	    System.out.println(table);
	    CompressedTable compressedTable = table.compress();
	    System.out.println(compressedTable);
	    //System.out.println(table);
	    //Utils.repl(s -> new AutomatonLexer(table, s, new int[]{' '}));
	    System.out.println(new SimpleCalculator().eval("4*4+(3+4)*5+4"));
	    */
		/*
	    LexerDescriptionParser lexerDescriptionParser = new LexerDescriptionParser();
	    //Utils.parserRepl(lexerDescriptionParser::eval);
	    lexerDescriptionParser.repl();
	    */
	    /*LexerDescriptionParser parser = new LexerDescriptionParser();
	    Table table = parser.eval("INT = [0-9]+; ID = [a-zA-Z]+");
	    ExtGrammarBuilder extBuilder = new ExtGrammarBuilder(table.terminalSet);
	    //System.out.println(table.terminalSet.typeToString(1));
	    extBuilder.create("a").rule("INT ID? INT?");
	    System.out.println(extBuilder.toGrammar("a").longDescription());
	    Generator.getCachedIfPossible("ddssdf", "INT = [0-9]+; ID = [a-zA-Z]+", new String[0],
			    (builder) -> builder.create("a").rule("INT ID? INT?"), "a");*/
	    //Utils.parserRepl(input -> new SimpleCalculator4().eval(input));
    }
}

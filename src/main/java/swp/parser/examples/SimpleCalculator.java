package swp.parser.examples;

import java.math.BigDecimal;
import java.util.*;

import swp.grammar.*;
import swp.lexer.*;
import swp.lexer.automata.*;
import swp.parser.lr.*;

/**
 * Different ways to implement a simple calculator.
 */
public class SimpleCalculator {

	private LRParserTable parserTable;
	private Table lexerTable;

	public SimpleCalculator(){
		Automaton automaton = new Automaton();
		automaton.addTerminal("eof", mn -> mn.append('\0'));
		automaton.addTerminal("plus", "+").addTerminal("minus", "-").addTerminal("multiply", "*")
				.addTerminal("divide", "/").addTerminal("lparen", "(").addTerminal("rparen", ")");
		automaton.addMacro("digits", mn -> mn.append('0', '9').plus());
		automaton.addTerminal("number", mn -> {
			return mn.append(mn.use("digits")).append(mn.create(".").append(mn.use("digits")).maybe());
		});
		automaton.toImage("simple_calculator_automaton", "svg");
		Automaton determ = automaton.toDeterministicVersion();
		determ.toImage("simple_calculator_determ_automaton", "svg");
		lexerTable = determ.toTable().compress();
		System.out.println(lexerTable);
		GrammarBuilder builder = new GrammarBuilder(lexerTable.terminalSet);
		builder.add("E", "E", "plus", "T")
				.action(asts -> {
					return new NumberNode(((NumberNode)asts.get(0)).num.add(((NumberNode)asts.get(2)).num));
				})
				.add("E", "T").action(asts -> new NumberNode(((NumberNode)asts.get(0)).num))
				.add("T", "T", "multiply", "F")
				.action(asts -> {
					return new NumberNode(((NumberNode)asts.get(0)).num.multiply(((NumberNode)asts.get(2)).num));
				})
				.add("T", "F")
				.add("T", "F").action(asts -> new NumberNode(((NumberNode)asts.get(0)).num))
				.add("F", "lparen", "E", "rparen").action(asts -> new NumberNode(((NumberNode)asts.get(1)).num))
				.add("F", "num").action(asts -> {
					return new NumberNode(new BigDecimal(asts.getMatchedString()));
				})
				.add("num", builder.maybe("+-"), "number")
				.add("int", builder.minimal(1, builder.range('0', '9')))
				.add("+-", builder.or("plus", "minus"));
		//builder.add("A", "B", "C").add("B", '4').add("C", "").add("C", '3');
		Graph.isLALR = true;
		Grammar g = builder.toGrammar("E");
		//System.out.println(g.longDescription());
		//System.out.println(g.longDescription());
		//System.out.println("First_1 set = " + g.calculateFirst1Set());
		Graph graph = Graph.createFromGrammar(g);
		//System.out.println(graph);
		graph.toImage("simple_calculator", "svg");
		parserTable = graph.toParserTable();

	}

	public BigDecimal eval(String input){
		Lexer lex = new AutomatonLexer(lexerTable, input, new int[]{' '});
		LRParser parser = new LRParser(parserTable.grammar, lex, parserTable);
		return ((NumberNode)parser.parse()).num;
	}

	private static class NumberNode extends BaseAST {

		public final BigDecimal num;

		public NumberNode(BigDecimal num){
			this.num = num;
		}

		@Override
		public List<Token> getMatchedTokens() {
			return new ArrayList<>();
		}

		@Override
		public String toString() {
			return "" + num;
		}
	}
}

package swp.parser.ll;

/**
 * Created by parttimenerd on 02.08.16.
 */

import swp.grammar.Grammar;
import swp.grammar.NonTerminal;
import swp.grammar.Symbol;
import swp.grammar.Terminal;
import swp.lexer.Lexer;
import swp.lexer.Token;
import swp.parser.lr.ASTLeaf;
import swp.parser.lr.BaseAST;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implements an LL(1) parser. Might or might not work properly. It's useless for almost every real world grammar.
 */
public class LLParser {

	private final Grammar grammar;
	private final Lexer lexer;
	private final LLParserTable table;
	Token current;
	List<Token> tokens = new ArrayList<>();
	int currentTokenNum = 0;
	private ArrayList<StackFrame> stack = new ArrayList<>();
	private boolean includeEOFToken = false;

	public LLParser(Lexer lexer, LLParserTable table, boolean includeEOFToken){
		this(table.grammar, lexer, table);
		this.includeEOFToken = includeEOFToken;
	}

	public LLParser(Lexer lexer, LLParserTable table){
		this(table.grammar, lexer, table);
	}

	public LLParser(Grammar grammar, Lexer lexer, LLParserTable table){
		this.grammar = grammar;
		this.lexer = lexer;
		this.table = table;
		tokens.add(lexer.cur());
		do {
			tokens.add(lexer.next());
		} while (lexer.cur().type != 0);
		current = tokens.get(0);
	}

	public void advanceTokenNum(){
		if (currentTokenNum >= tokens.size() - 1){
			current = tokens.get(tokens.size() - 1);
		} else {
			currentTokenNum++;
			current = tokens.get(currentTokenNum);
		}
	}

	public BaseAST parse(){
		stack.add(new StackFrame(grammar.getStart(), null, new ArrayList<BaseAST>(), -1, true));
		while (true) {
			int tokenType = current.type;

			StackFrame topStack = pop();
			Symbol topStackSymbol = topStack.symbol;

			if (topStackSymbol instanceof Terminal) {
				if (tokenType != ((Terminal) topStackSymbol).id) {
					throw new Error("Expected " + topStackSymbol + " but got " + current);
				}
				topStack.subAstsOfParent.add(new ASTLeaf(current));
				if (topStack.isLastSymbolOfProduction) {
					StackFrame elem = topStack;
					while (elem.parent != null && elem.isLastSymbolOfProduction) {
						elem.parent.ast = grammar.reduce(elem.productionId, elem.subAstsOfParent);
						if (elem.parent.parent != null) {
							elem.parent.parent.subAstsOfParent.add(elem.parent.ast);
						}
						elem = elem.parent;
					}
					if (elem.parent == null){
						return elem.ast;
					}
				}
				advanceTokenNum();
			} else {
				Map<Integer, Integer> row = table.table.get((NonTerminal) topStackSymbol);
				if (!row.containsKey(tokenType)) {
					ArrayList<String> arr = new ArrayList<>();
					for (int t : row.keySet()) {
						arr.add(lexer.getTerminalSet().typeToString(t));
					}
					Collections.sort(arr);
					throw new Error(String.format("Unexpected %s, expected %s", current, arr));
				}
				addToStack(topStack, row.get(tokenType), table.productions.get(row.get(tokenType)));
			}
		}
	}

	private void addToStack(StackFrame parent, int productionId, List<Symbol> rightHandSide){
		List<BaseAST> tmp = new ArrayList<>();
		for (int i = rightHandSide.size() - 1; i >= 0; i--){
			stack.add(new StackFrame(rightHandSide.get(i), parent, tmp, productionId, i == rightHandSide.size() - 1));
		}
	}

	private StackFrame pop(){
		StackFrame ret = stack.get(stack.size() - 1);
		stack.remove(stack.size() - 1);
		return ret;
	}

	class StackFrame {
		public final Symbol symbol;
		public final List<BaseAST> subAstsOfParent;
		public final StackFrame parent;
		public final int productionId;
		public final boolean isLastSymbolOfProduction;
		public BaseAST ast;

		public StackFrame(Symbol symbol, StackFrame parent, List<BaseAST> subAstsOfParent, int productionId, boolean isLastSymbolOfProduction) {
			this.symbol = symbol;
			this.subAstsOfParent = subAstsOfParent;
			this.productionId = productionId;
			this.isLastSymbolOfProduction = isLastSymbolOfProduction;
			this.parent = parent;
		}
	}
}


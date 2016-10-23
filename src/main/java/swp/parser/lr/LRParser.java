package swp.parser.lr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import swp.SWPException;
import swp.grammar.Grammar;
import swp.grammar.NonTerminal;
import swp.lexer.Lexer;
import swp.lexer.Token;
import swp.util.Pair;
import swp.util.Utils;

/**
 * Implements an LR(1) parser.
 */
public class LRParser {

	private final Grammar grammar;
	private final Lexer lexer;
	private final LRParserTable table;
	private ArrayList<StackFrame> stack = new ArrayList<>();
	private ArrayList<BaseAST> astStack = new ArrayList<>();
	private boolean includeEOFToken = false;
	private boolean hadError = false;

	public LRParser(Lexer lexer, LRParserTable table, boolean includeEOFToken){
		this(table.grammar, lexer, table);
		this.includeEOFToken = includeEOFToken;
	}

	public LRParser(Lexer lexer, LRParserTable table){
		this(table.grammar, lexer, table);
	}

	public LRParser(Grammar grammar, Lexer lexer, LRParserTable table){
		this.grammar = grammar;
		this.lexer = lexer;
		this.table = table;
		stack.add(new StackFrame(0));
	}

	public BaseAST parse(){
		while (true){
			int tokenId = lexer.cur().type;
			Map<Integer, LRParserTable.Action> row = table.actionTable.get(currentState());
			if (!row.containsKey(tokenId)){
				if (table instanceof ExtLRParserTable && extTableRecover()){
					continue;
				}
				ArrayList<String> arr = new ArrayList<>();
				for (int t : row.keySet()){
					arr.add(lexer.getTerminalSet().typeToString(t));
				}
				Collections.sort(arr);
				String errorMsg = String.format("Unexpected %s, expected %s at state %d", lexer.cur(), arr, currentState());
				if (table instanceof ExtLRParserTable){
					hadError = true;
					System.err.println(errorMsg);
				} else {
					throw new Error(errorMsg);
				}
			}
			LRParserTable.Action action = row.get(tokenId);
			switch (action.name()){
				case "shift":
					stack.add(new StackFrame(((LRParserTable.ShiftAction) action).stateToBeShifted, lexer.cur()));
					if (!hadError) {
						astStack.add(new ASTLeaf(lexer.cur()));
					}
					lexer.next();
					break;
				case "reduce":
					int prodId = ((LRParserTable.ReduceAction) action).productionId;
					Pair<NonTerminal, Integer> prodInfo = table.productionInformation.get(prodId);
					List<BaseAST> reducedASTs = new ArrayList<>();
					for (int i = 0; i < prodInfo.second; i++){
						if (!hadError) {
							reducedASTs.add(0, astStack.get(astStack.size() - 1));
							astStack.remove(astStack.size() - 1);
						}
						stack.remove(stack.size() - 1);
					}
					if (!hadError) {
						try {
							astStack.add(grammar.reduce(prodId, reducedASTs));
						} catch (SWPException ex){
							String newErrorMsg = String.format("Error around %s: %s", lexer.cur(), ex.getMessage());
							SWPException newEx = new SWPException(newErrorMsg);
							newEx.setStackTrace(ex.getStackTrace());
							throw newEx;
						}
					}
					int newState = currentState();
					stack.add(new StackFrame(table.gotoTable.get(newState).get(prodInfo.first)));
 				    break;
				case "accept":
					if (hadError){
						return null;
					}
					if (includeEOFToken) {
						astStack.get(astStack.size() - 1).<ListAST>as().add(new ASTLeaf(lexer.cur()));
					}
					return astStack.get(astStack.size() - 1);
			}
		}
	}

	private boolean extTableRecover(){
		List<ExtTableRecoverState> recoverStates = new ArrayList<>();
		ExtLRParserTable table = (ExtLRParserTable)this.table;
		Set<Integer> terminals = table.contextsOfStates.get(currentState());
		while (!terminals.contains(lexer.cur().type) && lexer.cur().type != 0){ //skip until the current character is in a situation context
			System.out.println("skip " + lexer.cur());
			lexer.next();
		}
		if (lexer.cur().type == 0){
			return false;
		}
		for (Utils.Triple<Set<Integer>, NonTerminal, Integer> triple : table.infoForStates.get(currentState())) {
			if (triple.first.contains(lexer.cur().type)) {
				recoverStates.add(new ExtTableRecoverState(stack, triple));
			}
		}
		while (!recoverStates.isEmpty()){
			List<ExtTableRecoverState> newRecoverStates = new ArrayList<>();
			for (ExtTableRecoverState recoverState : recoverStates){
				for (int i = 0; i < recoverState.info.third; i++){
					recoverState.stackFrames.remove(recoverState.stackFrames.size() - 1);
				}
				int newTopState = table.gotoTable.get(recoverState.stackFrames.get(recoverState.stackFrames.size() - 1).state).get(recoverState.info.second);
				recoverState.stackFrames.add(new StackFrame(newTopState, false));

				if (table.actionTable.get(newTopState).containsKey(lexer.cur().type)){
					stack = recoverState.stackFrames;
					return true;
				} else {
					for (Utils.Triple<Set<Integer>, NonTerminal, Integer> triple : table.infoForStates.get(newTopState)) {
						if (triple.first.contains(lexer.cur().type)) {
							newRecoverStates.add(new ExtTableRecoverState(stack, triple));
						}
					}
				}
			}
			recoverStates = newRecoverStates;
		}
		return false;
	}

	class ExtTableRecoverState {

		public ArrayList<StackFrame> stackFrames;
		/**
		 * (context, left hand side, |right hand side|) of non closure item
		 */
		public Utils.Triple<Set<Integer>, NonTerminal, Integer> info;

		public ExtTableRecoverState(ArrayList<StackFrame> stackFrames, Utils.Triple<Set<Integer>, NonTerminal, Integer> info) {
			this.stackFrames = (ArrayList<StackFrame>)stackFrames.clone();
			this.info = info;
		}
	}


	public int currentState(){
		return stack.get(stack.size() - 1).state;
	}

	class StackFrame {
		public final int state;
		public final boolean valid;
		public BaseAST ast;
		public List<BaseAST> temp = new ArrayList<>();

		public StackFrame(int state){
			this(state, true);
		}

		public StackFrame(int state, boolean valid){
			this.state = state;
			this.valid = valid;
		}

		public StackFrame(int state, Token token){
			this(state, true);
			this.ast = new ASTLeaf(token);
		}
	}
}

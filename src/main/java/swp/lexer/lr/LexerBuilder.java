package swp.lexer.lr;

import java.util.*;
import java.util.function.Function;

import swp.grammar.*;
import swp.lexer.*;
import swp.lexer.alphabet.*;
import swp.parser.lr.*;
import swp.util.Utils;

/**
 * "EOF" is the default token signaling the end of input, uses the alphabet lexer under the hood
 */
public class LexerBuilder {

	private final String grammarStartNonTerminal = "####0";
	private List<String> tokenNames = new ArrayList<>();
	private final GrammarBuilder grammarBuilder;
	private int[] ignoredUnderlyingTokenTypes;
	private final StringTerminals stringTerminals;

	public LexerBuilder(int[] ignoredTokens){
		grammarBuilder = new GrammarBuilder(new AlphabetTerminals());
		this.ignoredUnderlyingTokenTypes = ignoredTokens;
		stringTerminals = new StringTerminals(Utils.makeArrayList("EOF"));
		grammarBuilder.add(grammarStartNonTerminal, grammarStartNonTerminal + "0", '\0');
		grammarBuilder.add(grammarStartNonTerminal + "0", "");
		tokenNames.add("EOF");
	}

	public LexerBuilder add(String tokenName, Function<GrammarBuilder, Object[]> grammarCreator){
		tokenNames.add(tokenName);
		stringTerminals.addTerminal(tokenName);
		int type = tokenNames.size() - 1;
		grammarBuilder.add(grammarStartNonTerminal + "0", tokenName, grammarStartNonTerminal + "0");
		grammarBuilder.add(tokenName, grammarCreator.apply(grammarBuilder)).action(asts -> {
			return tokenToASTLeaf(type, new ListAST(asts));
		});
		return this;
	}

	private ASTLeaf tokenToASTLeaf(int type, BaseAST subTokens){
		List<Token> children = subTokens.getMatchedTokens();
		StringBuilder builder = new StringBuilder(children.size());
		for (Token token : children){
			builder.appendCodePoint(token.type);
		}
		return new ASTLeaf(new Token(type, stringTerminals, builder.toString(), children.get(0).location));
	}

	private StringTerminals createTerminalSet(){
		return new StringTerminals(tokenNames);
	}

	public ListLexer toLexer(String input){
		return  toLexer(input, new String[]{});
	}

	public ListLexer toLexer(String input, String[] ignoredTokenTypes){
		Graph.isLALR = false;
		Grammar grammar = grammarBuilder.toGrammar(grammarStartNonTerminal);
		//System.out.println(grammar.longDescription());
		Graph graph = Graph.createFromGrammar(grammar);
		//System.out.println(graph);
		graph.toImage("lexer_graph", "svg");
		LRParserTable parserTable = graph.toParserTable();
		LRParser parser = new LRParser(new AlphabetLexer(input, ignoredUnderlyingTokenTypes), parserTable, true);
		BaseAST ast = parser.parse();
		ListAST list = ast.<ListAST>as();
		System.out.println(list.toPrettyString());
		List<Token> tokens = list.getMatchedTokens();
		Token token = tokens.get(tokens.size() - 1);
		tokens.set(tokens.size() - 1, new Token(0, stringTerminals, "", token.location));
		return new ListLexer(stringTerminals, ignoredTokenTypes, tokens);
	}

	private class TokenNode extends BaseAST {

		public final Token token;

		public TokenNode(Token token){
			this.token = token;
		}

		@Override
		public List<Token> getMatchedTokens() {
			return Utils.makeArrayList(token);
		}
	}
}

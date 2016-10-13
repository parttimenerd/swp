package swp;

import swp.lexer.Lexer;
import swp.lexer.automata.AutomatonLexer;
import swp.lexer.automata.LexerDescriptionParser;
import swp.lexer.automata.Table;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class LexerDescriptionParserTest {

	static LexerDescriptionParser lexerDescriptionParser = new LexerDescriptionParser();

	@org.junit.Test
	public void invalidLexerGrammars() throws Exception {
		String[] invalidGrammars = new String[]{
				"a", "bc", "A", "A = $a"
		};
		for (String invalidGrammar : invalidGrammars){
			checkInvalidLexerGrammar(invalidGrammar);
		}
	}

	@org.junit.Test
	public void validLexerGrammars() throws Exception {
		String[] validGrammars = new String[]{
				"A = a"
		};
		for (String validGrammar : validGrammars){
			checkValidLexerGrammar(validGrammar);
		}
	}

	@org.junit.Test
	public void unlexable() throws Exception {
		String[] unlexable = new String[]{
				"A = a", "b",
				"A = [a]{2,}", "a",
				"A = . EOF", "sd",
				"A = [^\\t]", "\t"
		};
		for (int i = 0; i < unlexable.length; i += 2){
			checkUnlexable(unlexable[i], unlexable[i + 1]);
		}
	}

	@org.junit.Test
	public void lexable() throws Exception {
		checkLexable("A = a", "a", "A");
		String grammar = "A = (\"[^\\w]*\") | 3";
		checkLexable("A = (\"[^\\w]*\") | 3",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		checkLexable("A = \"[^\\w]*\" | 3",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		checkLexable("A = 3 | \"[^\\w]*\"",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		checkLexable("A = .{0} \"[^\\w]*\" | 3",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		checkLexable("A = .{0, 10} \"[^\\w]*\" | 3",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		checkLexable("A = [0-9]{0} \"[^\\w]*\" | 3",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		checkLexable("A = \"[^\\w]*\" | 3 |",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		checkLexable("A = (.{0} \"[^\\w]*\" | 3)*",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		checkLexable("A = .{0} \"[^\\w]*\" | 3??*+*+*+*?{1,20}",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		checkLexable("A = .{0} \"[^\\w]*\" | (3* .?)+",
				"\"sdf\"", "A",
				"\"\"", "A",
				"3", "A");
		//checkLexable(grammar, "\"sdf\"", "A");
		//checkLexable(grammar, "3", "A");
		//checkLexable(grammar, "\"\"", "A");
	}

	@org.junit.Test
	public void lexable2() throws Exception {
		toImage("A = a{3}", "checkUnlexable");
		checkUnlexable("A = a{1}{2}", "aaa");
		checkLexable("A = a{1}{2}", "aa", "A");
		checkLexable("A = a{1,}{2}", "aa", "A");
		checkLexable("A = a{1}{2,}", "aa", "A");
		checkLexable("A = a{1}{2,}", "aaa", "A");
		checkLexable("A = a{1,}{2,}", "aa", "A");
	}

	public void checkInvalidLexerGrammar(String lexerGrammar){
		try {
			createLexer(lexerGrammar, "");
		} catch (Error error){
			assertTrue(String.format("The lexer grammar \"%s\" isn't valid", lexerGrammar), true);
		}
		assertTrue(String.format("The lexer grammar \"%s\" is valid, but it shouldn't", lexerGrammar), true);
	}

	public void checkValidLexerGrammar(String lexerGrammar){
		try {
			createLexer(lexerGrammar, "");
		} catch (Error error){
			assertTrue(String.format("The lexer grammar \"%s\" isn't valid, but it should: %s", lexerGrammar, error.toString()), false);
		}
		assertTrue(String.format("The lexer grammar \"%s\" is valid", lexerGrammar), true);
	}

	public void checkUnlexable(String lexerGrammar, String input){
		Lexer lexer = createLexer(lexerGrammar, input);
		List<String> gotTokens = new ArrayList<>();
		try {
			do {
				lexer.next();
				gotTokens.add(lexer.cur().toSimpleString());
			} while (lexer.cur().type != 0);
		} catch (Error error) {
			assertTrue(String.format("Lexing \"%s\" with \"%s\" isn't possible", input, lexerGrammar), true);
			return;
		}
		toImage(lexerGrammar, "checkUnlexable");
		assertTrue(String.format("Lexing \"%s\" with \"%s\" shoudn't be possible, lexed: %s", input, lexerGrammar, String.join(" ", gotTokens)), false);
	}

	private void toImage(String lexerGrammar, String name){
		lexerDescriptionParser.toImage(lexerGrammar, name, "svg");
	}

	public void checkLexable(String lexerGrammar, String input, String expectedTokenTypes){
		checkLexable(lexerGrammar, input, true, expectedTokenTypes.split(" "));
	}

	public void checkLexable(String lexerGrammar, String... expectedTokenTypes){
		checkValidLexerGrammar(lexerGrammar);
		Table table = lexerDescriptionParser.eval(lexerGrammar);
		for (int i = 0; i < expectedTokenTypes.length; i += 2){
			try {
				Lexer lex = new AutomatonLexer(table, expectedTokenTypes[i], new int[]{' '});
				assertEquals(String.format("Lexing \"%s\" with \"%s\"", expectedTokenTypes[i], lexerGrammar), expectedTokenTypes[i + 1] + " EOF", formatLexerTokens(lex));
			} catch (Error error){
				assertTrue(String.format("Unexpected error occured while lexing \"%s\" with \"%s\": %s", expectedTokenTypes[i], lexerGrammar, error.toString()), true);
			}
		}
	}

	public void checkLexable(String lexerGrammar, String input, boolean woEOF, String... expectedTokenTypes){
		checkValidLexerGrammar(lexerGrammar);
		Lexer lexer = createLexer(lexerGrammar, input);
		List<String> gotTokens = new ArrayList<>();
		do {
			if (lexer.next().type != 0 || !woEOF) {
				gotTokens.add(lexer.cur().toSimpleString());
			}
		} while (lexer.cur().type != 0);
		assertArrayEquals(String.format("Lexing \"%s\" with \"%s\"", input, lexerGrammar), expectedTokenTypes, gotTokens.toArray(new String[]{}));
	}

	private String formatLexerTokens(Lexer lexer){
		List<String> gotTokens = new ArrayList<>();
		do {
			gotTokens.add(lexer.next().toSimpleString());
		} while (lexer.cur().type != 0);
		return String.join(" ", gotTokens);
	}

	private Lexer createLexer(String lexerGrammar, String input){
		Table table = lexerDescriptionParser.eval(lexerGrammar);
		return new AutomatonLexer(table, input, new int[]{' '});
	}
}
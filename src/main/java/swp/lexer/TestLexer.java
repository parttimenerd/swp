package swp.lexer;

/**
 * Created by parttimenerd on 14.05.16.
 */
public class TestLexer {

	private Lexer lexer;

	public TestLexer(Lexer lexer){
		this.lexer = lexer;
	}

	public void printAllTokens(){
		do {
			System.out.print(lexer.next() + " ");
		} while (lexer.cur().type != 0);
	}
}

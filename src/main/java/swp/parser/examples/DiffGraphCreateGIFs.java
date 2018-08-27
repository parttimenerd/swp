package swp.parser.examples;

import swp.grammar.*;
import swp.lexer.alphabet.AlphabetTerminals;
import swp.parser.lr.DiffGraph;

/**
 * Example for creating GIFs, MP4s and PNGs of a L(AL)R automaton construction.
 */
public class DiffGraphCreateGIFs {

	public static void main(String[] args) {
		/**
		 * The simplest way is to just use the pretty basic GrammarBuilder
		 */
		GrammarBuilder builder = new GrammarBuilder(AlphabetTerminals.getInstance());
		builder.add("AB", '(', "AB", ')') // Strings are the rules (and the first argument is the left hand side)
				.add("AB", 'a');

		Grammar g2 = builder.toGrammar("AB");

		new DiffGraph(g2, "/tmp/lalr").createPNGs();//.createGIF(1).createMP4(1);

		//Graph.isLALR = false; // Create an LR automaton

	//	new DiffGraph(g2, "/tmp/lr").createPNGs().createGIF(1).createMP4(1);
	}
}

package swp.parser;

import swp.parser.lr.BaseAST;

/**
 * Created by parttimenerd on 15.05.16.
 */
public abstract class Parser {

	abstract BaseAST parse();
}

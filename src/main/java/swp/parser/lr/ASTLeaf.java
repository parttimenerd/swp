package swp.parser.lr;

import java.util.List;

import swp.lexer.Token;
import swp.util.Utils;

/**
 * Created by parttimenerd on 22.07.16.
 */
public class ASTLeaf extends BaseAST {

	public final Token token;

	public ASTLeaf(Token token){
		this.token = token;
	}

	@Override
	public List<Token> getMatchedTokens() {
		return Utils.makeArrayList(token);
	}

	@Override
	public String toPrettyString(String indent, String incr) {
		return indent + toString();
	}

	@Override
	public String toString() {
		return "leaf(" + token + ")";
	}

	@Override
	public String type() {
		return "leaf";
	}
}

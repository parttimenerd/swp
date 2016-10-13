package swp.parser.lr;

import swp.util.Utils;
import swp.lexer.Token;

import java.util.List;

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
	protected String toPrettyString(int ident, int total) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < total; i++){
			builder.append("\t");
		}
		builder.append(this);
		return builder.toString();
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

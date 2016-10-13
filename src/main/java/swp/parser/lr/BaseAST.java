package swp.parser.lr;

import swp.lexer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by parttimenerd on 22.07.16.
 */
public abstract class BaseAST {

	public abstract List<Token> getMatchedTokens();

	public List<BaseAST> children(){
		return new ArrayList<>();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("(").append(type());
		for (BaseAST child : children()){
			builder.append(" ");
			builder.append(child);
		}
		builder.append(")");
		return builder.toString();
	}

	public String toPrettyString(){
		return toPrettyString(1, 0);
	}

	public String toPrettyString(int ident){
		return toPrettyString(ident, 0);
	}

	protected String toPrettyString(int ident, int total){
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < total; i++){
			builder.append("\t");
		}
		builder.append("(").append(type());
		builder.append("\n");
		List<BaseAST> children = children();
		for (int i = 0; i < children.size(); i++){
			builder.append(children.get(i).toPrettyString(ident, total + ident)).append("\n");
		}
		if (builder.codePointAt(builder.length() - 1) == '\n') {
			builder.deleteCharAt(builder.length() - 1);
		}
		builder.append(")");
		return builder.toString();
	}

	public String getMatchedString(){
		StringBuilder builder = new StringBuilder();
		for (Token token : getMatchedTokens()){
			builder.append(token.value);
		}
		return builder.toString();
	}

	public String type(){
		return "base";
	}

	public <T extends BaseAST> T as(){
		return (T)this;
	}
}

package swp.parser.lr;

import java.util.*;

import swp.lexer.Token;

/**
 * A basic AST
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
		return toPrettyString("", "\t");
	}

	public String toPrettyString(String indent, String incr){
		StringBuilder builder = new StringBuilder();
		builder.append(indent);
		builder.append("(").append(type());
		builder.append("\n");
		List<BaseAST> children = children();
		for (int i = 0; i < children.size(); i++){
			builder.append(children.get(i).toPrettyString(indent + incr, incr)).append("\n");
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

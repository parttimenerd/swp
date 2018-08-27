package swp.parser.lr;

import java.util.*;

import swp.lexer.Token;

/**
 * Created by parttimenerd on 25.08.16.
 */
public class CustomAST<T> extends BaseAST {

	public final T value;

	public CustomAST(T value) {
		this.value = value;
	}

	@Override
	public List<Token> getMatchedTokens() {
			return new ArrayList<>();
		}

	@Override
	public String toString() {
		return "" + value;
	}

	@Override
	public String type() {
		return "custom";
	}

	public static <T> CustomAST<T> create(T value){
		return new CustomAST<>(value);
	}
}

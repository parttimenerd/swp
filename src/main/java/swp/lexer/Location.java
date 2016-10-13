package swp.lexer;

public class Location {

	public final int line;
	public final int column;

	public Location(int line, int column){
		this.line = line;
		this.column = column;
	}

	@Override
	public String toString() {
		return "[" + line + ":" + column + "]";
	}
}

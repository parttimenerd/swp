package swp.parser.lr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import swp.lexer.Token;

/**
 * Created by parttimenerd on 22.07.16.
 */
public class ListAST<AST extends BaseAST> extends BaseAST implements Collection<AST>, List<AST>, RandomAccess {

	private List<AST> children;

	public ListAST(List<AST> children){
		this.children = children;
	}

	public ListAST(){
		this(new ArrayList<>());
	}

	public ListAST append(List<AST> asts){
		children.addAll(asts);
		return this;
	}

	public ListAST append(ListAST list){
		append(list.children);
		return this;
	}


	@Override
	public int size() {
		return children.size();
	}

	@Override
	public boolean isEmpty() {
		return children.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return children.contains(o);
	}

	@Override
	public Iterator<AST> iterator() {
		return children.iterator();
	}

	@Override
	public void forEach(Consumer<? super AST> action) {

	}

	@Override
	public Object[] toArray() {
		return children.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return children.toArray(a);
	}

	@Override
	public boolean add(AST ast){
		children.add(ast);
		return true;
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return children.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends AST> c) {
		return children.addAll(c);
	}

	@Override
	public boolean addAll(int index, Collection<? extends AST> c) {
		return children.addAll(index, c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeIf(Predicate<? super AST> filter) {
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void replaceAll(UnaryOperator<AST> operator) {
		children.replaceAll(operator);
	}

	@Override
	public void sort(Comparator<? super AST> c) {
		children.sort(c);
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public AST get(int index) {
		return children.get(index);
	}

	public <T extends AST> T getAs(int index){
		return (T)children.get(index);
	}

	public List<BaseAST> getAll(String astType){
		List<BaseAST> ret = new ArrayList<>();
		for (BaseAST ast : this){
			if (ast.type().equals("list")){
				ret.addAll(((ListAST)ast).getAll(astType));
			} else {
				if (ast.type().equals(astType)) {
					ret.add(ast);
				}
			}
		}
		return ret;
	}

	public List<Token> getMatchedTokens(String tokenType) {
		List<Token> tokens = getMatchedTokens();
		if (tokens.isEmpty()){
			return new ArrayList<>();
		}
		int typeId = tokens.get(0).terminalSet.stringToType(tokenType);
		List<Token> ret = new ArrayList<>();
		for (Token token : tokens){
			if (token.type == typeId){
				ret.add(token);
			}
		}
		return ret;
	}

	@Override
	public AST set(int index, AST element) {
		return children.set(index, element);
	}

	@Override
	public void add(int index, AST element) {
		children.add(index, element);
	}

	@Override
	public AST remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int indexOf(Object o) {
		return children.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return children.lastIndexOf(o);
	}

	@Override
	public ListIterator<AST> listIterator() {
		return children.listIterator();
	}

	@Override
	public ListIterator<AST> listIterator(int index) {
		return children.listIterator(index);
	}

	@Override
	public List<AST> subList(int fromIndex, int toIndex) {
		return new ListAST(children.subList(fromIndex, toIndex));
	}

	@Override
	public Spliterator<AST> spliterator() {
		return children.spliterator();
	}

	@Override
	public Stream<AST> stream() {
		return children.stream();
	}

	@Override
	public Stream<AST> parallelStream() {
		return children.parallelStream();
	}

	@Override
	public List<BaseAST> children() {
		return (List<BaseAST>)children;
	}

	@Override
	public String type() {
		return "list";
	}

	@Override
	public List<Token> getMatchedTokens() {
		List<Token> tokens = new ArrayList<>();
		for (BaseAST child : children){
			tokens.addAll(child.getMatchedTokens());
		}
		return tokens;
	}

	public AST getLast() {
		return get(size() - 1);
	}
}

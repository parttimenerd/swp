package nildumu;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import swp.parser.lr.BaseAST;

/**
 * Does a fix point iteration over nodes
 */
public class FixpointIteration {

    /**
     * Fix point iterates in topological order.
     * <p/>
     * Visits every node at least once. Uses the worklist algorithm
     *  @param nodeVisitor returns true if something changed
     * @param node node to start iterating on
     */
    public static void worklist(Parser.NodeVisitor<Boolean> nodeVisitor, Parser.MJNode node, HashSet<Parser.StatementNode> statementNodesToOmitOneTime){
        assert !(node instanceof Parser.ExpressionNode);
        HashSet<Parser.MJNode> visitedBefore = new HashSet<>();
        Stack<Parser.MJNode> nodesToVisit = new Stack<>();
        nodesToVisit.add(node);
        while (!nodesToVisit.isEmpty()){
            Parser.MJNode curNode = nodesToVisit.pop();
            boolean somethingChanged = curNode.accept(nodeVisitor);
            if (somethingChanged || !visitedBefore.contains(curNode)) {
                visitedBefore.add(curNode);
                List<Parser.MJNode> nodesToAdd = curNode.children().stream().filter(c -> {
                    if (statementNodesToOmitOneTime.contains(c)) {
                        statementNodesToOmitOneTime.remove(c);
                        return false;
                    }
                    return c instanceof Parser.MJNode && !(c instanceof Parser.ExpressionNode);
                }).map(c -> (Parser.MJNode)c).collect(Collectors.toList());
                Collections.reverse(nodesToAdd);
                nodesToVisit.addAll(nodesToAdd);
            }
        }
    }

    /**
     * Fix point iterates in topological order. Also walks the expressions.
     * <p/>
     * Visits every node at least once. Uses the worklist algorithm
     *  @param nodeVisitor returns true if something changed
     * @param node node to start iterating on
     */
    public static void worklist2(Parser.NodeVisitor<Boolean> nodeVisitor, Consumer<Parser.ExpressionNode> expressionConsumer,
                                 Parser.MJNode node, Set<Parser.StatementNode> statementNodesToOmitOneTime){
        assert !(node instanceof Parser.ExpressionNode);
        Set<Parser.MJNode> visitedBefore = new HashSet<>();
        Stack<Parser.MJNode> nodesToVisit = new Stack<>();
        nodesToVisit.add(node);
        while (!nodesToVisit.isEmpty()){
            Parser.MJNode curNode = nodesToVisit.pop();
            for (BaseAST childNode : curNode.children()){
                if (childNode instanceof Parser.ExpressionNode){
                    walkExpression(expressionConsumer, (Parser.ExpressionNode)childNode);
                }
            }
            boolean somethingChanged = curNode.accept(nodeVisitor);
            if (somethingChanged || !visitedBefore.contains(curNode)) {
                visitedBefore.add(curNode);
                List<Parser.MJNode> nodesToAdd = curNode.children().stream().filter(c -> {
                    if (statementNodesToOmitOneTime.contains(c)) {
                        statementNodesToOmitOneTime.remove(c);
                        return false;
                    }
                    return c instanceof Parser.MJNode && !(c instanceof Parser.ExpressionNode);
                }).map(c -> (Parser.MJNode)c).collect(Collectors.toList());
                Collections.reverse(nodesToAdd);
                nodesToVisit.addAll(nodesToAdd);
            }
        }
    }

    /**
     * Visits every node in an expression tree in a reversed topological order (i.e. post order).
     * Each sub expression is visited with the visiting results of its children as parameters.
     * <p/>
     * Assumes expression trees.
     *
     * @param expressionVisitor
     * @param expression expression to start with
     */
    public static <V> V walkExpression(Parser.ExpressionVisitorWArgs<V, List<V>> expressionVisitor, Parser.ExpressionNode expression){
        List<V> childResults = expression.children().stream().filter(c -> c instanceof Parser.ExpressionNode)
                .map(c -> walkExpression(expressionVisitor, (Parser.ExpressionNode)c))
                .collect(Collectors.toList());
        return expression.accept(expressionVisitor, childResults);
    }


    /**
     * Visits every node in an expression tree in a reversed topological order (i.e. post order).
     * Each sub expression is visited with the visiting results of its children as parameters.
     * <p/>
     * Assumes expression trees.
     */
    public static void walkExpression(Consumer<Parser.ExpressionNode> visitor, Parser.ExpressionNode expression){
        expression.children().stream().filter(c -> c instanceof Parser.ExpressionNode)
                .forEach(c -> walkExpression(visitor, (Parser.ExpressionNode)c));
        visitor.accept(expression);
    }
    /**
     * Just lists the nodes in the order, that they would be traversed if the fix point would end after the first iteration
     */
    public static void trialRun(Parser.MJNode node){
        worklist(new Parser.NodeVisitor<Boolean>() {
            @Override
            public Boolean visit(Parser.MJNode node) {
                System.out.println("→ " + node.toString());
                return false;
            }

            @Override
            public Boolean visit(Parser.ExpressionNode expression) {
                walkExpression(new Parser.ExpressionVisitorWArgs<Object, List<Object>>() {
                    @Override
                    public Object visit(Parser.ExpressionNode expression, List<Object> argument) {
                        System.out.println("  → " + node.toString());
                        return null;
                    }
                }, expression);
                return false;
            }
        }, node, new HashSet<>());
    }
}
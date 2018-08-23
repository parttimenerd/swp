package nildumu;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import swp.util.Pair;

import static nildumu.Lattices.*;
import static nildumu.Lattices.B.ONE;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.B.ZERO;
import static nildumu.Parser.*;

public class Processor {

    public static Context process(String program){
        return process(program, Context.Mode.BASIC);
    }

    public static Context process(String program, Context.Mode mode){
        ProgramNode node = parse(program);
        return process(node.context.mode(mode), node);
    }

    public static Context process(Context context, MJNode node) {

        final Set<StatementNode> statementNodesToOmitOneTime = new HashSet<>();

        FixpointIteration.worklist2(new NodeVisitor<Boolean>() {

            /**
             * conditional bits with their assumed value for each conditional statement body
             */
            Map<BlockNode, Pair<Bit, Bit>> conditionalBits = new HashMap<>();

            int unfinishedLoopIterations = 0;

            final Map<MJNode, Value> oldValues = new HashMap<>();

            final Stack<Long> nodeValueUpdatesAtCondition = new Stack<>();

            boolean didValueChangeAndUpdate(MJNode node, Value newValue){
                if (oldValues.containsKey(node) && oldValues.get(node) == newValue){
                    return false;
                }
                oldValues.put(node, newValue);
                return true;
            }

            @Override
            public Boolean visit(MJNode node) {
                return false;
            }

            @Override
            public Boolean visit(ProgramNode program) {
                return false;
            }

            @Override
            public Boolean visit(VariableAssignmentNode assignment) {
                context.evaluate(assignment);
                return false;
            }

            @Override
            public Boolean visit(IfStatementNode ifStatement) {
                Value cond = context.nodeValue(ifStatement.conditionalExpression);
                Bit condBit = cond.get(1);
                Lattices.B condVal = condBit.val;
                if (condVal == U && unfinishedLoopIterations > 0){
                    context.weight(condBit, Context.INFTY);
                }
                if (condVal == ONE || condVal == U) {
                    conditionalBits.put(ifStatement.ifBlock, new Pair<>(condBit, new Bit(ONE)));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.ifBlock);
                }
                if (condVal == ZERO || condVal == U) {
                    conditionalBits.put(ifStatement.elseBlock, new Pair<>(condBit, new Bit(ZERO)));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.elseBlock);
                }
                return didValueChangeAndUpdate(ifStatement, cond);
            }

            @Override
            public Boolean visit(BlockNode block) {
                if (conditionalBits.containsKey(block)){
                    Pair<Bit, Bit> bitPair = conditionalBits.get(block);
                    context.pushMods(bitPair.first, bitPair.second);
                    nodeValueUpdatesAtCondition.push(context.getNodeValueUpdateCount());
                }
                return false;
            }

            @Override
            public Boolean visit(WhileStatementNode whileStatement) {
                if (!context.inLoopMode()){
                    throw new NildumuError("while-statements are only supported in modes starting at loop mode");
                }
                Value cond = context.nodeValue(whileStatement.conditionalExpression);
                Bit condBit = cond.get(1);
                Lattices.B condVal = condBit.val;
                if (condVal == U){
                    context.weight(condBit, Context.INFTY);
                }
                if (condVal == ONE || condVal == U) {
                    conditionalBits.put(whileStatement.body, new Pair<>(condBit, new Bit(ONE)));
                    unfinishedLoopIterations++;
                } else {
                    statementNodesToOmitOneTime.add(whileStatement.body);
                }
                nodeValueUpdatesAtCondition.push(context.getNodeValueUpdateCount());
                return didValueChangeAndUpdate(whileStatement, cond);
            }

            @Override
            public Boolean visit(IfStatementEndNode ifEndStatement) {
                context.popMods();
                return nodeValueUpdatesAtCondition.pop() != context.getNodeValueUpdateCount();
            }

            @Override
            public Boolean visit(WhileStatementEndNode whileEndStatement) {
                unfinishedLoopIterations--;
                context.popMods();
                return nodeValueUpdatesAtCondition.pop() != context.getNodeValueUpdateCount();
            }

        }, context::evaluate, node, statementNodesToOmitOneTime);
        return context;
    }
}

package nildumu;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            public Map<BlockNode, Pair<Bit, Bit>> conditionalBits = new HashMap<>();

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
                Lattices.B condVal = cond.get(1).val;
                if (condVal == ONE || condVal == U) {
                    conditionalBits.put(ifStatement.ifBlock, new Pair<>(cond.get(1), new Bit(ONE)));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.ifBlock);
                }
                if (condVal == ZERO || condVal == U) {
                    conditionalBits.put(ifStatement.elseBlock, new Pair<>(cond.get(1), new Bit(ZERO)));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.elseBlock);
                }
                return false;
            }

            @Override
            public Boolean visit(BlockNode block) {
                if (conditionalBits.containsKey(block)){
                    Pair<Bit, Bit> bitPair = conditionalBits.get(block);
                    context.pushMods(bitPair.first, bitPair.second);
                }
                return false;
            }

            @Override
            public Boolean visit(WhileStatementNode whileStatement) {
                throw new NotImplementedException();
            }

            @Override
            public Boolean visit(IfStatementEndNode ifEndStatement) {
                context.popMods();
                return false;
            }

            @Override
            public Boolean visit(WhileStatementEndNode whileEndStatement) {
                return false;
            }

        }, context::evaluate, node, statementNodesToOmitOneTime);
        return context;
    }
}

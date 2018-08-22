package nildumu;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import swp.util.Pair;

import static nildumu.Lattices.*;
import static nildumu.Lattices.B.ONE;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.B.ZERO;

public class Processor {

    public static Context process(String program){
        Parser.ProgramNode node = Parser.parse(program);
        return process(node.context, node);
    }

    public static Context process(Context context, Parser.MJNode node) {

        final Set<Parser.StatementNode> statementNodesToOmitOneTime = new HashSet<>();

        FixpointIteration.worklist2(new Parser.NodeVisitor<Boolean>() {

            @Override
            public Boolean visit(Parser.MJNode node) {
                return false;
            }

            @Override
            public Boolean visit(Parser.ProgramNode program) {
                return false;
            }

            @Override
            public Boolean visit(Parser.VariableAssignmentNode assignment) {
                context.evaluate(assignment);
                return false;
            }

            @Override
            public Boolean visit(Parser.IfStatementNode ifStatement) {
                Value cond = context.nodeValue(ifStatement.conditionalExpression);
                Lattices.B condVal = cond.get(1).val;
                List<Pair<B, Parser.BlockNode>> evaluatedBranches = new ArrayList<>();
                if (condVal == ONE || condVal == U) {
                    evaluatedBranches.add(new Pair<>(ONE, ifStatement.ifBlock));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.ifBlock);
                }
                if (condVal == ZERO || condVal == U) {
                    evaluatedBranches.add(new Pair<>(ZERO, ifStatement.elseBlock));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.elseBlock);
                }
                return false;
            }

            @Override
            public Boolean visit(Parser.WhileStatementNode whileStatement) {
                throw new NotImplementedException();
            }

            @Override
            public Boolean visit(Parser.IfStatementEndNode ifEndStatement) {
                return false;
            }

            @Override
            public Boolean visit(Parser.WhileStatementEndNode whileEndStatement) {
                return false;
            }

        }, context::evaluate, node, statementNodesToOmitOneTime);
        return context;
    }
}

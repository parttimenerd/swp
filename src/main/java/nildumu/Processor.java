package nildumu;

import java.util.*;
import java.util.stream.Collectors;

import swp.util.Pair;

import static nildumu.Lattices.B.*;
import static nildumu.Lattices.*;
import static nildumu.Parser.*;

public class Processor {

    public static boolean transformPlus = false;

    public static Context process(String program){
        return process(program, Context.Mode.BASIC);
    }

    public static Context process(String program, Context.Mode mode){
        return process(program, mode, MethodInvocationHandler.createDefault());
    }

    public static Context process(String program, Context.Mode mode, MethodInvocationHandler handler){
        return process(program, mode, handler, transformPlus);
    }

    public static Context process(String program, Context.Mode mode, MethodInvocationHandler handler, boolean transformPlus){
        return process(Parser.process(program, transformPlus), mode, handler);
    }

    public static Context process(ProgramNode node, MethodInvocationHandler handler){
        handler.setup(node);
        return process(node.context.forceMethodInvocationHandler(handler), node);
    }

    public static Context process(ProgramNode node, Context.Mode mode, MethodInvocationHandler handler){
        node.context.mode(mode);
        handler.setup(node);
        return process(node.context.forceMethodInvocationHandler(handler), node);
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

            final Map<MJNode, Long> lastUpdateCounts = new DefaultMap<>(new HashMap<>(), new DefaultMap.Extension<MJNode, Long>() {
                @Override
                public Long defaultValue(Map<MJNode, Long> map, MJNode key) {
                    return 0l;
                }
            });

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
                context.setVariableValue(assignment.definition, context.nodeValue(assignment.expression));
                return false;
            }

            @Override
            public Boolean visit(OutputVariableDeclarationNode outputDecl) {
                visit((VariableAssignmentNode)outputDecl);
                context.addOutputValue(context.sl.parse(outputDecl.secLevel), context.getVariableValue(outputDecl.definition));
                return false;
            }

            @Override
            public Boolean visit(IfStatementNode ifStatement) {
                Value cond = context.nodeValue(ifStatement.conditionalExpression);
                Bit condBit = cond.get(1);
                Lattices.B condVal = condBit.val();
                if (condVal == U && unfinishedLoopIterations > 0){
                    context.weight(condBit, Context.INFTY);
                }
                if (condVal == ONE || condVal == U) {
                    conditionalBits.put(ifStatement.ifBlock, new Pair<>(condBit, bl.create(ONE)));
                } else {
                    statementNodesToOmitOneTime.add(ifStatement.ifBlock);
                }
                if (condVal == ZERO || condVal == U) {
                    conditionalBits.put(ifStatement.elseBlock, new Pair<>(condBit, bl.create(ZERO)));
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
                    nodeValueUpdatesAtCondition.push(context.getNodeVersionUpdateCount());
                }
                if (lastUpdateCounts.get(block) == context.getNodeVersionUpdateCount()) {
                    return false;
                }
                lastUpdateCounts.put(block, context.getNodeVersionUpdateCount());
                return true;
            }

            @Override
            public Boolean visit(WhileStatementNode whileStatement) {
                if (!context.inLoopMode()){
                    throw new NildumuError("while-statements are only supported in modes starting at loop mode");
                }
                Value cond = context.nodeValue(whileStatement.conditionalExpression);
                Bit condBit = cond.get(1);
                Lattices.B condVal = condBit.val();
                if (condVal == U){
                    context.weight(condBit, Context.INFTY);
                }
                if (condVal == ONE || condVal == U) {
                    conditionalBits.put(whileStatement.body, new Pair<>(condBit, bl.create(ONE)));
                    unfinishedLoopIterations++;
                } else {
                    statementNodesToOmitOneTime.add(whileStatement.body);
                }
                if (didValueChangeAndUpdate(whileStatement, cond)) {
                    nodeValueUpdatesAtCondition.push(context.getNodeVersionUpdateCount());
                    return true;
                }
                return false;
            }

            @Override
            public Boolean visit(IfStatementEndNode ifEndStatement) {
                context.popMods();
                return nodeValueUpdatesAtCondition.pop() != context.getNodeVersionUpdateCount();
            }

            @Override
            public Boolean visit(WhileStatementEndNode whileEndStatement) {
                unfinishedLoopIterations--;
                context.popMods();
                return nodeValueUpdatesAtCondition.pop() != context.getNodeVersionUpdateCount();
            }

            @Override
            public Boolean visit(MethodInvocationNode methodInvocation) {
                System.out.printf("%s(%s)", methodInvocation.definition.name, methodInvocation.arguments.arguments.stream().map(context::nodeValue).map(Value::toString).collect(Collectors.joining(", ")));
                return false;
            }

            @Override
            public Boolean visit(ReturnStatementNode returnStatement) {
                if (returnStatement.hasReturnExpression()){
                    context.setReturnValue(context.nodeValue(returnStatement.expression));
                }
                return false;
            }

            @Override
            public Boolean visit(VariableAccessNode variableAccess) {
                return false;
            }
        }, context::evaluate, node, statementNodesToOmitOneTime);
        return context;
    }
}

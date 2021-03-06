/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.algebricks.compiler.optimizer.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.BroadcastExpressionAnnotation;
import edu.uci.ics.algebricks.compiler.algebra.expressions.BroadcastExpressionAnnotation.BroadcastSide;
import edu.uci.ics.algebricks.compiler.algebra.expressions.IExpressionAnnotation;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.functions.AlgebricksBuiltinFunctions;
import edu.uci.ics.algebricks.compiler.algebra.functions.AlgebricksBuiltinFunctions.ComparisonKind;
import edu.uci.ics.algebricks.compiler.algebra.functions.FunctionIdentifier;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractBinaryJoinOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors.LogicalPropertiesVisitor;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.AbstractJoinPOperator.JoinPartitioningType;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.HybridHashJoinPOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.InMemoryHashJoinPOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.NLJoinPOperator;
import edu.uci.ics.algebricks.compiler.algebra.properties.ILogicalPropertiesVector;
import edu.uci.ics.algebricks.config.AlgebricksConfig;

public class JoinUtils {

    private final static int MB = 1048576;

    private final static double DEFAULT_FUDGE_FACTOR = 1.3;
    private final static int MAX_RECORDS_PER_FRAME = 512;
    private final static int DEFAULT_FRAME_SIZE = 32768;
    private final static int MAX_LEFT_INPUT_SIZE_HYBRID_HASH = (int) (140L * 1024 * MB / DEFAULT_FRAME_SIZE);
    private final static int DEFAULT_MEMORY_SIZE_HYBRID_HASH = (int) (256L * MB / DEFAULT_FRAME_SIZE);

    public static void setJoinAlgorithmAndExchangeAlgo(AbstractBinaryJoinOperator op, IOptimizationContext context)
            throws AlgebricksException {
        List<LogicalVariable> sideLeft = new LinkedList<LogicalVariable>();
        List<LogicalVariable> sideRight = new LinkedList<LogicalVariable>();
        List<LogicalVariable> varsLeft = op.getInputs().get(0).getOperator().getSchema();
        List<LogicalVariable> varsRight = op.getInputs().get(1).getOperator().getSchema();
        if (isHashJoinCondition(op.getCondition().getExpression(), varsLeft, varsRight, sideLeft, sideRight)) {
            BroadcastSide side = getBroadcastJoinSide(op.getCondition().getExpression(), varsLeft, varsRight);
            if (side == null) {
                setHashJoinOp(op, JoinPartitioningType.PAIRWISE, sideLeft, sideRight, context);
            } else {
                switch (side) {
                    case RIGHT:
                        setHashJoinOp(op, JoinPartitioningType.BROADCAST, sideLeft, sideRight, context);
                        break;
                    case LEFT:
                        LogicalOperatorReference opRef0 = op.getInputs().get(0);
                        LogicalOperatorReference opRef1 = op.getInputs().get(1);
                        ILogicalOperator tmp = opRef0.getOperator();
                        opRef0.setOperator(opRef1.getOperator());
                        opRef1.setOperator(tmp);
                        setHashJoinOp(op, JoinPartitioningType.BROADCAST, sideRight, sideLeft, context);
                        break;
                    default:
                        setHashJoinOp(op, JoinPartitioningType.PAIRWISE, sideLeft, sideRight, context);
                }
            }
        } else {
            setNLJoinOp(op);
        }
    }

    private static void setNLJoinOp(AbstractBinaryJoinOperator op) {
        op.setPhysicalOperator(new NLJoinPOperator(op.getJoinKind(), JoinPartitioningType.BROADCAST,
                DEFAULT_MEMORY_SIZE_HYBRID_HASH));
    }

    private static void setHashJoinOp(AbstractBinaryJoinOperator op, JoinPartitioningType partitioningType,
            List<LogicalVariable> sideLeft, List<LogicalVariable> sideRight, IOptimizationContext context)
            throws AlgebricksException {
        op.setPhysicalOperator(new HybridHashJoinPOperator(op.getJoinKind(), partitioningType, sideLeft, sideRight,
                DEFAULT_MEMORY_SIZE_HYBRID_HASH, MAX_LEFT_INPUT_SIZE_HYBRID_HASH, MAX_RECORDS_PER_FRAME,
                DEFAULT_FUDGE_FACTOR));
        if (partitioningType == JoinPartitioningType.BROADCAST) {
            hybridToInMemHashJoin(op, context);
        }
        // op.setPhysicalOperator(new
        // InMemoryHashJoinPOperator(op.getJoinKind(), partitioningType,
        // sideLeft, sideRight,
        // 1024 * 512));
    }

    private static void hybridToInMemHashJoin(AbstractBinaryJoinOperator op, IOptimizationContext context)
            throws AlgebricksException {
        ILogicalOperator opBuild = op.getInputs().get(1).getOperator();
        LogicalPropertiesVisitor.computeLogicalPropertiesDFS(opBuild, context);
        ILogicalPropertiesVector v = context.getLogicalPropertiesVector(opBuild);
        AlgebricksConfig.ALGEBRICKS_LOGGER.fine("// HybridHashJoin inner branch -- Logical properties for " + opBuild
                + ": " + v + "\n");
        if (v != null) {
            int size2 = v.getMaxOutputFrames();
            HybridHashJoinPOperator hhj = (HybridHashJoinPOperator) op.getPhysicalOperator();
            if (size2 > 0 && size2 * hhj.getFudgeFactor() <= hhj.getMemSizeInFrames()) {
                AlgebricksConfig.ALGEBRICKS_LOGGER.fine("// HybridHashJoin inner branch " + opBuild
                        + " fits in memory\n");
                // maintains the local properties on the probe side
                op.setPhysicalOperator(new InMemoryHashJoinPOperator(hhj.getKind(), hhj.getPartitioningType(), hhj
                        .getKeysLeftBranch(), hhj.getKeysRightBranch(), v.getNumberOfTuples() * 2));
            }
        }

    }

    private static boolean isHashJoinCondition(ILogicalExpression e, Collection<LogicalVariable> inLeftAll,
            Collection<LogicalVariable> inRightAll, Collection<LogicalVariable> outLeftFields,
            Collection<LogicalVariable> outRightFields) {
        switch (e.getExpressionTag()) {
            case FUNCTION_CALL: {
                AbstractFunctionCallExpression fexp = (AbstractFunctionCallExpression) e;
                FunctionIdentifier fi = fexp.getFunctionIdentifier();
                if (fi == AlgebricksBuiltinFunctions.AND) {
                    for (LogicalExpressionReference a : fexp.getArguments()) {
                        if (!isHashJoinCondition(a.getExpression(), inLeftAll, inRightAll, outLeftFields,
                                outRightFields)) {
                            return false;
                        }
                    }
                    return true;
                } else {
                    ComparisonKind ck = AlgebricksBuiltinFunctions.getComparisonType(fi);
                    if (ck != ComparisonKind.EQ) {
                        return false;
                    }
                    ILogicalExpression opLeft = fexp.getArguments().get(0).getExpression();
                    ILogicalExpression opRight = fexp.getArguments().get(1).getExpression();
                    if (opLeft.getExpressionTag() != LogicalExpressionTag.VARIABLE
                            || opRight.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
                        return false;
                    }
                    LogicalVariable var1 = ((VariableReferenceExpression) opLeft).getVariableReference();
                    if (inLeftAll.contains(var1) && !outLeftFields.contains(var1)) {
                        outLeftFields.add(var1);
                    } else if (inRightAll.contains(var1) && !outRightFields.contains(var1)) {
                        outRightFields.add(var1);
                    } else {
                        return false;
                    }
                    LogicalVariable var2 = ((VariableReferenceExpression) opRight).getVariableReference();
                    if (inLeftAll.contains(var2) && !outLeftFields.contains(var2)) {
                        outLeftFields.add(var2);
                    } else if (inRightAll.contains(var2) && !outRightFields.contains(var2)) {
                        outRightFields.add(var2);
                    } else {
                        return false;
                    }
                    return true;
                }
            }
            default: {
                return false;
            }
        }
    }

    private static BroadcastSide getBroadcastJoinSide(ILogicalExpression e, List<LogicalVariable> varsLeft,
            List<LogicalVariable> varsRight) {
        if (e.getExpressionTag() != LogicalExpressionTag.FUNCTION_CALL) {
            return null;
        }
        AbstractFunctionCallExpression fexp = (AbstractFunctionCallExpression) e;
        IExpressionAnnotation ann = fexp.getAnnotations().get(BroadcastExpressionAnnotation.BROADCAST_ANNOTATION_KEY);
        if (ann == null) {
            return null;
        }
        BroadcastSide side = (BroadcastSide) ann.getObject();
        if (side == null) {
            return null;
        }
        int i;
        switch (side) {
            case LEFT:
                i = 0;
                break;
            case RIGHT:
                i = 1;
                break;
            default:
                return null;
        }
        ArrayList<LogicalVariable> vars = new ArrayList<LogicalVariable>();
        fexp.getArguments().get(i).getExpression().getUsedVariables(vars);
        if (varsLeft.containsAll(vars)) {
            return BroadcastSide.LEFT;
        } else if (varsRight.containsAll(vars)) {
            return BroadcastSide.RIGHT;
        } else {
            return null;
        }
    }
}

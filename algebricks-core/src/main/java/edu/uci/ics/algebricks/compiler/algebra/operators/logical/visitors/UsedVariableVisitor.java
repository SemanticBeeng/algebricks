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
package edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors;

import java.util.Collection;
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AggregateOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AssignOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.DataSourceScanOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.DieOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.DistinctOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.EmptyTupleSourceOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ExchangeOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.IndexInsertDeleteOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.InnerJoinOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.InsertDeleteOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.LeftOuterJoinOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.LimitOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.NestedTupleSourceOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.OrderOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.OrderOperator.IOrder;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.PartitioningSplitOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ProjectOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ReplicateOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.RunningAggregateOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ScriptOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.SelectOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.SinkOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.SubplanOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.UnionAllOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.UnnestMapOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.UnnestOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.WriteOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.WriteResultOperator;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalOperatorVisitor;
import edu.uci.ics.algebricks.utils.Pair;
import edu.uci.ics.algebricks.utils.Triple;

public class UsedVariableVisitor implements ILogicalOperatorVisitor<Void, Void> {

    private Collection<LogicalVariable> usedVariables;

    public UsedVariableVisitor(Collection<LogicalVariable> usedVariables) {
        this.usedVariables = usedVariables;
    }

    @Override
    public Void visitAggregateOperator(AggregateOperator op, Void arg) {
        for (LogicalExpressionReference exprRef : op.getExpressions()) {
            exprRef.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitAssignOperator(AssignOperator op, Void arg) {
        for (LogicalExpressionReference exprRef : op.getExpressions()) {
            exprRef.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitDataScanOperator(DataSourceScanOperator op, Void arg) {
        // does not use any variable
        return null;
    }

    @Override
    public Void visitDistinctOperator(DistinctOperator op, Void arg) {
        for (LogicalExpressionReference eRef : op.getExpressions()) {
            eRef.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitEmptyTupleSourceOperator(EmptyTupleSourceOperator op, Void arg) {
        // does not use any variable
        return null;
    }

    @Override
    public Void visitExchangeOperator(ExchangeOperator op, Void arg) {
        // does not use any variable
        return null;
    }

    @Override
    public Void visitGroupByOperator(GroupByOperator op, Void arg) throws AlgebricksException {
        for (ILogicalPlan p : op.getNestedPlans()) {
            for (LogicalOperatorReference r : p.getRoots()) {
                VariableUtilities.getUsedVariablesInDescendantsAndSelf(r.getOperator(), usedVariables);
            }
        }
        for (Pair<LogicalVariable, LogicalExpressionReference> g : op.getGroupByList()) {
            g.second.getExpression().getUsedVariables(usedVariables);
        }
        for (Pair<LogicalVariable, LogicalExpressionReference> g : op.getDecorList()) {
            g.second.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitInnerJoinOperator(InnerJoinOperator op, Void arg) {
        op.getCondition().getExpression().getUsedVariables(usedVariables);
        return null;
    }

    @Override
    public Void visitLeftOuterJoinOperator(LeftOuterJoinOperator op, Void arg) {
        op.getCondition().getExpression().getUsedVariables(usedVariables);
        return null;
    }

    @Override
    public Void visitLimitOperator(LimitOperator op, Void arg) {
        op.getMaxObjects().getExpression().getUsedVariables(usedVariables);
        ILogicalExpression offsetExpr = op.getOffset().getExpression();
        if (offsetExpr != null) {
            offsetExpr.getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitDieOperator(DieOperator op, Void arg) {
        op.getAfterObjects().getExpression().getUsedVariables(usedVariables);
        return null;
    }

    @Override
    public Void visitNestedTupleSourceOperator(NestedTupleSourceOperator op, Void arg) {
        // does not use any variable
        return null;
    }

    @Override
    public Void visitOrderOperator(OrderOperator op, Void arg) {
        for (Pair<IOrder, LogicalExpressionReference> oe : op.getOrderExpressions()) {
            oe.second.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitPartitioningSplitOperator(PartitioningSplitOperator op, Void arg) {
        for (LogicalExpressionReference e : op.getExpressions()) {
            e.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitProjectOperator(ProjectOperator op, Void arg) {
        List<LogicalVariable> parameterVariables = op.getVariables();
        for (LogicalVariable v : parameterVariables) {
            if (!usedVariables.contains(v)) {
                usedVariables.add(v);
            }
        }
        return null;
    }

    @Override
    public Void visitRunningAggregateOperator(RunningAggregateOperator op, Void arg) {
        for (LogicalExpressionReference exprRef : op.getExpressions()) {
            exprRef.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitScriptOperator(ScriptOperator op, Void arg) {
        List<LogicalVariable> parameterVariables = op.getInputVariables();
        for (LogicalVariable v : parameterVariables) {
            if (!usedVariables.contains(v)) {
                usedVariables.add(v);
            }
        }
        return null;
    }

    @Override
    public Void visitSelectOperator(SelectOperator op, Void arg) {
        op.getCondition().getExpression().getUsedVariables(usedVariables);
        return null;
    }

    @Override
    public Void visitSubplanOperator(SubplanOperator op, Void arg) throws AlgebricksException {
        for (ILogicalPlan p : op.getNestedPlans()) {
            for (LogicalOperatorReference r : p.getRoots()) {
                VariableUtilities.getUsedVariablesInDescendantsAndSelf(r.getOperator(), usedVariables);
            }
        }
        return null;
    }

    @Override
    public Void visitUnionOperator(UnionAllOperator op, Void arg) {
        for (Triple<LogicalVariable, LogicalVariable, LogicalVariable> m : op.getVariableMappings()) {
            if (!usedVariables.contains(m.first)) {
                usedVariables.add(m.first);
            }
            if (!usedVariables.contains(m.second)) {
                usedVariables.add(m.second);
            }
        }
        return null;
    }

    @Override
    public Void visitUnnestMapOperator(UnnestMapOperator op, Void arg) {
        op.getExpressionRef().getExpression().getUsedVariables(usedVariables);
        return null;
    }

    @Override
    public Void visitUnnestOperator(UnnestOperator op, Void arg) {
        op.getExpressionRef().getExpression().getUsedVariables(usedVariables);
        return null;
    }

    @Override
    public Void visitWriteOperator(WriteOperator op, Void arg) {
        for (LogicalExpressionReference expr : op.getExpressions()) {
            expr.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitWriteResultOperator(WriteResultOperator op, Void arg) {
        op.getPayloadExpression().getExpression().getUsedVariables(usedVariables);
        for (LogicalExpressionReference e : op.getKeyExpressions()) {
            e.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitInsertDeleteOperator(InsertDeleteOperator op, Void arg) {
        op.getPayloadExpression().getExpression().getUsedVariables(usedVariables);
        for (LogicalExpressionReference e : op.getPrimaryKeyExpressions()) {
            e.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitIndexInsertDeleteOperator(IndexInsertDeleteOperator op, Void arg) {
        for (LogicalExpressionReference e : op.getPrimaryKeyExpressions()) {
            e.getExpression().getUsedVariables(usedVariables);
        }
        for (LogicalExpressionReference e : op.getSecondaryKeyExpressions()) {
            e.getExpression().getUsedVariables(usedVariables);
        }
        return null;
    }

    @Override
    public Void visitSinkOperator(SinkOperator op, Void arg) {
        return null;
    }

    @Override
    public Void visitReplicateOperator(ReplicateOperator op, Void arg) throws AlgebricksException {
        return null;
    }

}

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

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
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

public class SchemaVariableVisitor implements ILogicalOperatorVisitor<Void, Void> {

    private Collection<LogicalVariable> schemaVariables;

    public SchemaVariableVisitor(Collection<LogicalVariable> schemaVariables) {
        this.schemaVariables = schemaVariables;
    }

    @Override
    public Void visitAggregateOperator(AggregateOperator op, Void arg) throws AlgebricksException {
        schemaVariables.addAll(op.getVariables());
        return null;
    }

    @Override
    public Void visitAssignOperator(AssignOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitDataScanOperator(DataSourceScanOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitDistinctOperator(DistinctOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitEmptyTupleSourceOperator(EmptyTupleSourceOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitExchangeOperator(ExchangeOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitGroupByOperator(GroupByOperator op, Void arg) throws AlgebricksException {
        for (ILogicalPlan p : op.getNestedPlans()) {
            for (LogicalOperatorReference r : p.getRoots()) {
                VariableUtilities.getLiveVariables(r.getOperator(), schemaVariables);
            }
        }
        for (Pair<LogicalVariable, LogicalExpressionReference> p : op.getGroupByList()) {
            if (p.first != null) {
                schemaVariables.add(p.first);
            }
        }
        for (Pair<LogicalVariable, LogicalExpressionReference> p : op.getDecorList()) {
            if (p.first != null) {
                schemaVariables.add(p.first);
            } else {
                ILogicalExpression e = p.second.getExpression();
                if (e.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                    schemaVariables.add(((VariableReferenceExpression) e).getVariableReference());
                }
            }
        }
        return null;
    }

    @Override
    public Void visitInnerJoinOperator(InnerJoinOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitLeftOuterJoinOperator(LeftOuterJoinOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitLimitOperator(LimitOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitDieOperator(DieOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitNestedTupleSourceOperator(NestedTupleSourceOperator op, Void arg) throws AlgebricksException {
        VariableUtilities.getLiveVariables(op.getSourceOperator(), schemaVariables);
        return null;
    }

    @Override
    public Void visitOrderOperator(OrderOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitPartitioningSplitOperator(PartitioningSplitOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitProjectOperator(ProjectOperator op, Void arg) throws AlgebricksException {
        schemaVariables.addAll(op.getVariables());
        return null;
    }

    @Override
    public Void visitRunningAggregateOperator(RunningAggregateOperator op, Void arg) throws AlgebricksException {
        // VariableUtilities.getProducedVariables(op, schemaVariables);
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitScriptOperator(ScriptOperator op, Void arg) throws AlgebricksException {
        schemaVariables.addAll(op.getOutputVariables());
        return null;
    }

    @Override
    public Void visitSelectOperator(SelectOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitSubplanOperator(SubplanOperator op, Void arg) throws AlgebricksException {
        for (LogicalOperatorReference c : op.getInputs()) {
            VariableUtilities.getLiveVariables(c.getOperator(), schemaVariables);
        }
        VariableUtilities.getProducedVariables(op, schemaVariables);
        for (ILogicalPlan p : op.getNestedPlans()) {
            for (LogicalOperatorReference r : p.getRoots()) {
                VariableUtilities.getLiveVariables(r.getOperator(), schemaVariables);
            }
        }
        return null;
    }

    @Override
    public Void visitUnionOperator(UnionAllOperator op, Void arg) throws AlgebricksException {
        VariableUtilities.getProducedVariables(op, schemaVariables);
        return null;
    }

    @Override
    public Void visitUnnestMapOperator(UnnestMapOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitUnnestOperator(UnnestOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitWriteOperator(WriteOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitWriteResultOperator(WriteResultOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    private void standardLayout(ILogicalOperator op) throws AlgebricksException {
        for (LogicalOperatorReference c : op.getInputs()) {
            VariableUtilities.getLiveVariables(c.getOperator(), schemaVariables);
        }
        VariableUtilities.getProducedVariables(op, schemaVariables);
    }

    @Override
    public Void visitReplicateOperator(ReplicateOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitInsertDeleteOperator(InsertDeleteOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitIndexInsertDeleteOperator(IndexInsertDeleteOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

    @Override
    public Void visitSinkOperator(SinkOperator op, Void arg) throws AlgebricksException {
        standardLayout(op);
        return null;
    }

}

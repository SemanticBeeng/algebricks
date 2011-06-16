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
package edu.uci.ics.algebricks.compiler.algebra.prettyprint;

import java.util.Arrays;
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractOperatorWithNestedPlans;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AggregateOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AssignOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.DataSourceScanOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.DistinctOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.EmptyTupleSourceOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ExchangeOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.InnerJoinOperator;
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
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.SubplanOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.UnionAllOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.UnnestMapOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.UnnestOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.WriteOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.WriteResultOperator;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalOperatorVisitor;
import edu.uci.ics.algebricks.utils.Triple;
import edu.uci.ics.hyracks.api.util.Pair;

public class LogicalOperatorPrettyPrintVisitor implements ILogicalOperatorVisitor<String, Integer> {

    public LogicalOperatorPrettyPrintVisitor() {
    }

    @Override
    public String visitAggregateOperator(AggregateOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("aggregate ").append(op.getVariables()).append(" <- ");
        pprintExprList(op.getExpressions(), buffer);
        return buffer.toString();
    }

    @Override
    public String visitRunningAggregateOperator(RunningAggregateOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("running-aggregate ").append(op.getVariables()).append(" <- ");
        pprintExprList(op.getExpressions(), buffer);
        return buffer.toString();
    }

    @Override
    public String visitEmptyTupleSourceOperator(EmptyTupleSourceOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("empty-tuple-source");
        return buffer.toString();
    }

    @Override
    public String visitGroupByOperator(GroupByOperator op, Integer indent) throws AlgebricksException {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("group by (").append(op.gByListToString()).append(") decor (")
                .append(op.decorListToString()).append(") {");
        printNestedPlans(op, indent, buffer);
        return buffer.toString();
    }

    @Override
    public String visitDistinctOperator(DistinctOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("distinct " + "(");
        pprintExprList(op.getExpressions(), buffer);
        buffer.append(")");
        return buffer.toString();
    }

    @Override
    public String visitInnerJoinOperator(InnerJoinOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("join (").append(op.getCondition().getExpression()).append(")");
        return buffer.toString();
    }

    @Override
    public String visitLeftOuterJoinOperator(LeftOuterJoinOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("left outer join (").append(op.getCondition().getExpression()).append(")");
        return buffer.toString();
    }

    @Override
    public String visitNestedTupleSourceOperator(NestedTupleSourceOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("nested tuple source");
        return buffer.toString();
    }

    @Override
    public String visitOrderOperator(OrderOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("order ");
        for (Pair<OrderOperator.IOrder, LogicalExpressionReference> p : op.getOrderExpressions()) {
            String fst;
            switch (p.first.getKind()) {
                case ASC: {
                    fst = "ASC";
                    break;
                }
                case DESC: {
                    fst = "DESC";
                    break;
                }
                default: {
                    fst = p.first.getExpressionRef().toString();
                }
            }
            buffer.append("(" + fst + ", " + p.second.getExpression() + ") ");
        }
        return buffer.toString();
    }

    @Override
    public String visitAssignOperator(AssignOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("assign ").append(op.getVariables()).append(" <- ");
        pprintExprList(op.getExpressions(), buffer);
        return buffer.toString();
    }

    @Override
    public String visitWriteOperator(WriteOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("write ").append(op.getExpressions());
        return buffer.toString();
    }

    @Override
    public String visitWriteResultOperator(WriteResultOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("load ").append(op.getDatasetName()).append(" from ")
                .append(op.getPayloadExpression()).append(" partitioned by ")
                .append(Arrays.toString(op.getKeyExpressions()));
        return buffer.toString();
    }

    @Override
    public String visitSelectOperator(SelectOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("select " + "(" + op.getCondition().getExpression() + ")");
        return buffer.toString();
    }

    @Override
    public String visitProjectOperator(ProjectOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("project " + "(" + op.getVariables() + ")");
        return buffer.toString();
    }

    @Override
    public String visitPartitioningSplitOperator(PartitioningSplitOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("partitioning-split (" + op.getExpressions() + ")");
        return buffer.toString();
    }

    @Override
    public String visitSubplanOperator(SubplanOperator op, Integer indent) throws AlgebricksException {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("subplan {");
        printNestedPlans(op, indent, buffer);
        return buffer.toString();
    }

    @Override
    public String visitUnionOperator(UnionAllOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("union");
        for (Triple<LogicalVariable, LogicalVariable, LogicalVariable> v : op.getVariableMappings()) {
            buffer.append(" (" + v.first + ", " + v.second + ", " + v.third + ")");
        }
        return buffer.toString();
    }

    @Override
    public String visitUnnestOperator(UnnestOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("unnest " + op.getVariable());
        if (op.getPositionalVariable() != null) {
            buffer.append(" at " + op.getPositionalVariable());
        }
        buffer.append(" <- " + op.getExpressionRef().getExpression());
        return buffer.toString();
    }

    @Override
    public String visitUnnestMapOperator(UnnestMapOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append(
                "unnest-map " + op.getVariables() + " <- " + op.getExpressionRef().getExpression());
        return buffer.toString();
    }

    @Override
    public String visitDataScanOperator(DataSourceScanOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("data-scan " + op.getVariables() + " <- " + op.getDataSource());
        return buffer.toString();
    }

    @Override
    public String visitLimitOperator(LimitOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("limit " + op.getMaxObjects().getExpression());
        ILogicalExpression offset = op.getOffset().getExpression();
        if (offset != null) {
            buffer.append(", " + offset);
        }
        return buffer.toString();
    }

    @Override
    public String visitExchangeOperator(ExchangeOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append("exchange ");
        return buffer.toString();
    }

    protected static final StringBuilder addIndent(StringBuilder buffer, int level) {
        for (int i = 0; i < level; ++i) {
            buffer.append(' ');
        }
        return buffer;
    }

    private void printNestedPlans(AbstractOperatorWithNestedPlans op, Integer indent, StringBuilder buffer)
            throws AlgebricksException {
        boolean first = true;
        if (op.getNestedPlans().isEmpty()) {
            buffer.append("}");
        } else {
            for (ILogicalPlan p : op.getNestedPlans()) {
                // PrettyPrintUtil.indent(buffer, level + 10).append("var " +
                // p.first + ":\n");
                buffer.append("\n");
                if (first) {
                    first = false;
                } else {
                    addIndent(buffer, indent).append("       {\n");
                }
                PlanPrettyPrinter.printPlan(p, buffer, this, indent + 10);
                addIndent(buffer, indent).append("       }");
            }
        }
    }

    @Override
    public String visitScriptOperator(ScriptOperator op, Integer indent) {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append(
                "script (in: " + op.getInputVariables() + ") (out: " + op.getOutputVariables() + ")");
        return buffer.toString();
    }

    private void pprintExprList(List<LogicalExpressionReference> expressions, StringBuilder buffer) {
        buffer.append("[");
        boolean first = true;
        for (LogicalExpressionReference exprRef : expressions) {
            if (first) {
                first = false;
            } else {
                buffer.append(", ");
            }
            buffer.append(exprRef.getExpression());
        }
        buffer.append("]");
    }

    @Override
    public String visitReplicateOperator(ReplicateOperator op, Integer indent) throws AlgebricksException {
        StringBuilder buffer = new StringBuilder();
        addIndent(buffer, indent).append(
                "replicate ");
        return buffer.toString();
    }

}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AbstractLogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractAssignOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractOperatorWithNestedPlans;
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
import edu.uci.ics.algebricks.utils.Triple;

public class IsomorphismVariableMappingVisitor implements ILogicalOperatorVisitor<Void, ILogicalOperator> {

    private Map<LogicalVariable, LogicalVariable> variableMapping;

    public IsomorphismVariableMappingVisitor(Map<LogicalVariable, LogicalVariable> variableMapping) {
        this.variableMapping = variableMapping;
    }

    @Override
    public Void visitAggregateOperator(AggregateOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapChildren(op, arg);
        mapVariablesForAbstractAssign(op, arg);
        return null;
    }

    @Override
    public Void visitRunningAggregateOperator(RunningAggregateOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        mapChildren(op, arg);
        mapVariablesForAbstractAssign(op, arg);
        return null;
    }

    @Override
    public Void visitEmptyTupleSourceOperator(EmptyTupleSourceOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitGroupByOperator(GroupByOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapChildren(op, arg);
        mapVariablesForGroupBy(op, arg);
        mapVariablesInNestedPlans(op, arg);
        return null;
    }

    @Override
    public Void visitLimitOperator(LimitOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitDieOperator(DieOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitInnerJoinOperator(InnerJoinOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitLeftOuterJoinOperator(LeftOuterJoinOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitNestedTupleSourceOperator(NestedTupleSourceOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        ILogicalOperator inputToCreator1 = op.getSourceOperator();
        NestedTupleSourceOperator nts = (NestedTupleSourceOperator) arg;
        ILogicalOperator inputToCreator2 = nts.getSourceOperator();
        inputToCreator1.accept(this, inputToCreator2);
        return null;
    }

    @Override
    public Void visitOrderOperator(OrderOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitAssignOperator(AssignOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapChildren(op, arg);
        mapVariablesForAbstractAssign(op, arg);
        return null;
    }

    @Override
    public Void visitSelectOperator(SelectOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitProjectOperator(ProjectOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitPartitioningSplitOperator(PartitioningSplitOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitReplicateOperator(ReplicateOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitScriptOperator(ScriptOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitSubplanOperator(SubplanOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapChildren(op, arg);
        mapVariablesInNestedPlans(op, arg);
        return null;
    }

    @Override
    public Void visitUnionOperator(UnionAllOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapChildren(op, arg);
        mapVariablesForUnion(op, arg);
        return null;
    }

    @Override
    public Void visitUnnestOperator(UnnestOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitUnnestMapOperator(UnnestMapOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitDataScanOperator(DataSourceScanOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitDistinctOperator(DistinctOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitExchangeOperator(ExchangeOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitWriteOperator(WriteOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitWriteResultOperator(WriteResultOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitInsertDeleteOperator(InsertDeleteOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitIndexInsertDeleteOperator(IndexInsertDeleteOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    @Override
    public Void visitSinkOperator(SinkOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapVariablesStandard(op, arg);
        return null;
    }

    private void mapChildren(ILogicalOperator op, ILogicalOperator opArg) throws AlgebricksException {
        List<LogicalOperatorReference> inputs = op.getInputs();
        List<LogicalOperatorReference> inputsArg = opArg.getInputs();
        if (inputs.size() != inputsArg.size())
            throw new AlgebricksException("children are not isomoprhic");
        for (int i = 0; i < inputs.size(); i++) {
            ILogicalOperator input = inputs.get(i).getOperator();
            ILogicalOperator inputArg = inputsArg.get(i).getOperator();
            input.accept(this, inputArg);
        }
    }

    private void mapVariables(ILogicalOperator left, ILogicalOperator right) throws AlgebricksException {
        List<LogicalVariable> producedVarLeft = new ArrayList<LogicalVariable>();
        List<LogicalVariable> producedVarRight = new ArrayList<LogicalVariable>();
        VariableUtilities.getProducedVariables(left, producedVarLeft);
        VariableUtilities.getProducedVariables(right, producedVarRight);
        mapVariables(producedVarLeft, producedVarRight);
    }

    private void mapVariables(List<LogicalVariable> variablesLeft, List<LogicalVariable> variablesRight) {
        if (variablesLeft.size() != variablesRight.size())
            return;
        int size = variablesLeft.size();
        for (int i = 0; i < size; i++) {
            LogicalVariable left = variablesLeft.get(i);
            LogicalVariable right = variablesRight.get(i);
            variableMapping.put(right, left);
        }
    }

    private void mapVariablesForAbstractAssign(ILogicalOperator left, ILogicalOperator right)
            throws AlgebricksException {
        AbstractAssignOperator leftOp = (AbstractAssignOperator) left;
        AbstractAssignOperator rightOp = (AbstractAssignOperator) right;
        List<LogicalVariable> producedVarLeft = new ArrayList<LogicalVariable>();
        List<LogicalVariable> producedVarRight = new ArrayList<LogicalVariable>();
        VariableUtilities.getProducedVariables(left, producedVarLeft);
        VariableUtilities.getProducedVariables(right, producedVarRight);
        mapVariablesForAbstractAssign(producedVarLeft, leftOp.getExpressions(), producedVarRight,
                rightOp.getExpressions());
    }

    private void mapVariablesForGroupBy(ILogicalOperator left, ILogicalOperator right) throws AlgebricksException {
        GroupByOperator leftOp = (GroupByOperator) left;
        GroupByOperator rightOp = (GroupByOperator) right;
        List<Pair<LogicalVariable, LogicalExpressionReference>> leftPairs = leftOp.getGroupByList();
        List<Pair<LogicalVariable, LogicalExpressionReference>> rightPairs = rightOp.getGroupByList();
        mapVarExprPairList(leftPairs, rightPairs);
        leftPairs = leftOp.getDecorList();
        rightPairs = rightOp.getDecorList();
        mapVarExprPairList(leftPairs, rightPairs);
    }

    private void mapVarExprPairList(List<Pair<LogicalVariable, LogicalExpressionReference>> leftPairs,
            List<Pair<LogicalVariable, LogicalExpressionReference>> rightPairs) {
        if (leftPairs.size() != rightPairs.size())
            return;
        for (int i = 0; i < leftPairs.size(); i++) {
            ILogicalExpression exprLeft = leftPairs.get(i).second.getExpression();
            LogicalVariable leftVar = leftPairs.get(i).first;
            for (int j = 0; j < leftPairs.size(); j++) {
                ILogicalExpression exprRight = copyExpressionAndSubtituteVars(rightPairs.get(j).second).getExpression();
                if (exprLeft.equals(exprRight)) {
                    LogicalVariable rightVar = rightPairs.get(j).first;
                    if (rightVar != null && leftVar != null) {
                        variableMapping.put(rightVar, leftVar);
                    }
                    break;
                }
            }
        }
    }

    private void mapVariablesForAbstractAssign(List<LogicalVariable> variablesLeft,
            List<LogicalExpressionReference> exprsLeft, List<LogicalVariable> variablesRight,
            List<LogicalExpressionReference> exprsRight) {
        if (variablesLeft.size() != variablesRight.size())
            return;
        int size = variablesLeft.size();
        for (int i = 0; i < size; i++) {
            ILogicalExpression exprLeft = exprsLeft.get(i).getExpression();
            LogicalVariable left = variablesLeft.get(i);
            for (int j = 0; j < size; j++) {
                ILogicalExpression exprRight = copyExpressionAndSubtituteVars(exprsRight.get(j)).getExpression();
                if (exprLeft.equals(exprRight)) {
                    LogicalVariable right = variablesRight.get(j);
                    variableMapping.put(right, left);
                    break;
                }
            }
        }
    }

    private void mapVariablesInNestedPlans(ILogicalOperator opOrigin, ILogicalOperator arg) throws AlgebricksException {
        AbstractOperatorWithNestedPlans op = (AbstractOperatorWithNestedPlans) opOrigin;
        AbstractOperatorWithNestedPlans argOp = (AbstractOperatorWithNestedPlans) arg;
        List<ILogicalPlan> plans = op.getNestedPlans();
        List<ILogicalPlan> plansArg = argOp.getNestedPlans();
        if (plans.size() != plansArg.size())
            return;
        for (int i = 0; i < plans.size(); i++) {
            List<LogicalOperatorReference> roots = plans.get(i).getRoots();
            List<LogicalOperatorReference> rootsArg = plansArg.get(i).getRoots();
            if (roots.size() != rootsArg.size())
                return;
            for (int j = 0; j < roots.size(); j++) {
                ILogicalOperator topOp1 = roots.get(j).getOperator();
                ILogicalOperator topOp2 = rootsArg.get(j).getOperator();
                topOp1.accept(this, topOp2);
            }
        }
    }

    private void mapVariablesStandard(ILogicalOperator op, ILogicalOperator arg) throws AlgebricksException {
        mapChildren(op, arg);
        mapVariables(op, arg);
    }

    private LogicalExpressionReference copyExpressionAndSubtituteVars(LogicalExpressionReference expr) {
        ILogicalExpression copy = ((AbstractLogicalExpression) expr.getExpression()).cloneExpression();
        for (Entry<LogicalVariable, LogicalVariable> entry : variableMapping.entrySet())
            copy.substituteVar(entry.getKey(), entry.getValue());
        return new LogicalExpressionReference(copy);
    }

    private void mapVariablesForUnion(ILogicalOperator op, ILogicalOperator arg) {
        UnionAllOperator union = (UnionAllOperator) op;
        UnionAllOperator unionArg = (UnionAllOperator) arg;
        mapVarTripleList(union.getVariableMappings(), unionArg.getVariableMappings());
    }

    private void mapVarTripleList(List<Triple<LogicalVariable, LogicalVariable, LogicalVariable>> leftTriples,
            List<Triple<LogicalVariable, LogicalVariable, LogicalVariable>> rightTriples) {
        if (leftTriples.size() != rightTriples.size())
            return;
        for (int i = 0; i < leftTriples.size(); i++) {
            LogicalVariable leftFirstVar = leftTriples.get(i).first;
            LogicalVariable leftSecondVar = leftTriples.get(i).second;
            LogicalVariable leftThirdVar = leftTriples.get(i).third;
            for (int j = 0; j < rightTriples.size(); j++) {
                LogicalVariable rightFirstVar = rightTriples.get(j).first;
                LogicalVariable rightSecondVar = rightTriples.get(j).second;
                LogicalVariable rightThirdVar = rightTriples.get(j).third;
                if (varEquivalent(leftFirstVar, rightFirstVar) && varEquivalent(leftSecondVar, rightSecondVar)) {
                    variableMapping.put(rightThirdVar, leftThirdVar);
                    break;
                }
            }
        }
    }

    private boolean varEquivalent(LogicalVariable left, LogicalVariable right) {
        if (variableMapping.get(right) == null)
            return false;
        if (variableMapping.get(right).equals(left))
            return true;
        else
            return false;
    }

}

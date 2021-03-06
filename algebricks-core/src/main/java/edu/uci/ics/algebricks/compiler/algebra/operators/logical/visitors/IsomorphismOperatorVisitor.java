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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AbstractLogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
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
import edu.uci.ics.algebricks.compiler.algebra.plan.ALogicalPlanImpl;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPartitioningProperty;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPhysicalPropertiesVector;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalOperatorVisitor;
import edu.uci.ics.algebricks.utils.Pair;
import edu.uci.ics.algebricks.utils.Triple;

public class IsomorphismOperatorVisitor implements ILogicalOperatorVisitor<Boolean, ILogicalOperator> {

    private Map<LogicalVariable, LogicalVariable> variableMapping = new HashMap<LogicalVariable, LogicalVariable>();

    public IsomorphismOperatorVisitor() {
    }

    @Override
    public Boolean visitAggregateOperator(AggregateOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.AGGREGATE)
            return Boolean.FALSE;
        AggregateOperator aggOpArg = (AggregateOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(
                getPairList(op.getVariables(), op.getExpressions()),
                getPairList(aggOpArg.getVariables(), aggOpArg.getExpressions()));
        return isomorphic;
    }

    @Override
    public Boolean visitRunningAggregateOperator(RunningAggregateOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.RUNNINGAGGREGATE)
            return Boolean.FALSE;
        RunningAggregateOperator aggOpArg = (RunningAggregateOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(
                getPairList(op.getVariables(), op.getExpressions()),
                getPairList(aggOpArg.getVariables(), aggOpArg.getExpressions()));
        return isomorphic;
    }

    @Override
    public Boolean visitEmptyTupleSourceOperator(EmptyTupleSourceOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) copyAndSubstituteVar(op, arg);
        if (aop.getOperatorTag() != LogicalOperatorTag.EMPTYTUPLESOURCE)
            return Boolean.FALSE;
        return Boolean.TRUE;
    }

    @Override
    public Boolean visitGroupByOperator(GroupByOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        // require the same physical operator, otherwise delivers different data
        // properties
        if (aop.getOperatorTag() != LogicalOperatorTag.GROUP
                || aop.getPhysicalOperator().getOperatorTag() != op.getPhysicalOperator().getOperatorTag())
            return Boolean.FALSE;

        List<Pair<LogicalVariable, LogicalExpressionReference>> keyLists = op.getGroupByList();
        GroupByOperator gbyOpArg = (GroupByOperator) copyAndSubstituteVar(op, arg);
        List<Pair<LogicalVariable, LogicalExpressionReference>> keyListsArg = gbyOpArg.getGroupByList();

        List<Pair<LogicalVariable, ILogicalExpression>> listLeft = new ArrayList<Pair<LogicalVariable, ILogicalExpression>>();
        List<Pair<LogicalVariable, ILogicalExpression>> listRight = new ArrayList<Pair<LogicalVariable, ILogicalExpression>>();

        for (Pair<LogicalVariable, LogicalExpressionReference> pair : keyLists)
            listLeft.add(new Pair<LogicalVariable, ILogicalExpression>(pair.first, pair.second.getExpression()));
        for (Pair<LogicalVariable, LogicalExpressionReference> pair : keyListsArg)
            listRight.add(new Pair<LogicalVariable, ILogicalExpression>(pair.first, pair.second.getExpression()));

        boolean isomorphic = VariableUtilities.varListEqualUnordered(listLeft, listRight);

        if (!isomorphic)
            return Boolean.FALSE;
        int sizeOp = op.getNestedPlans().size();
        int sizeArg = gbyOpArg.getNestedPlans().size();
        if (sizeOp != sizeArg)
            return Boolean.FALSE;

        GroupByOperator argOp = (GroupByOperator) arg;
        List<ILogicalPlan> plans = op.getNestedPlans();
        List<ILogicalPlan> plansArg = argOp.getNestedPlans();
        for (int i = 0; i < plans.size(); i++) {
            List<LogicalOperatorReference> roots = plans.get(i).getRoots();
            List<LogicalOperatorReference> rootsArg = plansArg.get(i).getRoots();
            if (roots.size() != rootsArg.size())
                return Boolean.FALSE;
            for (int j = 0; j < roots.size(); j++) {
                ILogicalOperator topOp1 = roots.get(j).getOperator();
                ILogicalOperator topOp2 = rootsArg.get(j).getOperator();
                isomorphic = this.checkBottomUp(topOp1, topOp2);
                if (!isomorphic)
                    return Boolean.FALSE;
            }
        }
        return isomorphic;
    }

    @Override
    public Boolean visitLimitOperator(LimitOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.LIMIT)
            return Boolean.FALSE;
        LimitOperator limitOpArg = (LimitOperator) copyAndSubstituteVar(op, arg);
        if (op.getOffset() != limitOpArg.getOffset())
            return Boolean.FALSE;
        boolean isomorphic = op.getMaxObjects().getExpression().equals(limitOpArg.getMaxObjects().getExpression());
        return isomorphic;
    }

    @Override
    public Boolean visitDieOperator(DieOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.DIE)
            return Boolean.FALSE;
        DieOperator dieOpArg = (DieOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = op.getAfterObjects().getExpression().equals(dieOpArg.getAfterObjects().getExpression());
        return isomorphic;
    }

    @Override
    public Boolean visitInnerJoinOperator(InnerJoinOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.INNERJOIN)
            return Boolean.FALSE;
        InnerJoinOperator joinOpArg = (InnerJoinOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = op.getCondition().getExpression().equals(joinOpArg.getCondition().getExpression());
        return isomorphic;
    }

    @Override
    public Boolean visitLeftOuterJoinOperator(LeftOuterJoinOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.LEFTOUTERJOIN)
            return Boolean.FALSE;
        LeftOuterJoinOperator joinOpArg = (LeftOuterJoinOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = op.getCondition().getExpression().equals(joinOpArg.getCondition().getExpression());
        return isomorphic;
    }

    @Override
    public Boolean visitNestedTupleSourceOperator(NestedTupleSourceOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.NESTEDTUPLESOURCE)
            return Boolean.FALSE;
        return Boolean.TRUE;
    }

    @Override
    public Boolean visitOrderOperator(OrderOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.ORDER)
            return Boolean.FALSE;
        OrderOperator orderOpArg = (OrderOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = compareIOrderAndExpressions(op.getOrderExpressions(), orderOpArg.getOrderExpressions());
        return isomorphic;
    }

    @Override
    public Boolean visitAssignOperator(AssignOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.ASSIGN)
            return Boolean.FALSE;
        AssignOperator assignOpArg = (AssignOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(
                getPairList(op.getVariables(), op.getExpressions()),
                getPairList(assignOpArg.getVariables(), assignOpArg.getExpressions()));
        return isomorphic;
    }

    @Override
    public Boolean visitSelectOperator(SelectOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.SELECT)
            return Boolean.FALSE;
        SelectOperator selectOpArg = (SelectOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = op.getCondition().getExpression().equals(selectOpArg.getCondition().getExpression());
        return isomorphic;
    }

    @Override
    public Boolean visitProjectOperator(ProjectOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.PROJECT)
            return Boolean.FALSE;
        ProjectOperator projectOpArg = (ProjectOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(op.getVariables(), projectOpArg.getVariables());
        return isomorphic;
    }

    @Override
    public Boolean visitPartitioningSplitOperator(PartitioningSplitOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.PARTITIONINGSPLIT)
            return Boolean.FALSE;
        PartitioningSplitOperator partitionOpArg = (PartitioningSplitOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = compareExpressions(Arrays.asList(op.getExpressions()),
                Arrays.asList(partitionOpArg.getExpressions()));
        return isomorphic;
    }

    @Override
    public Boolean visitReplicateOperator(ReplicateOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.REPLICATE)
            return Boolean.FALSE;
        return Boolean.TRUE;
    }

    @Override
    public Boolean visitScriptOperator(ScriptOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.SCRIPT)
            return Boolean.FALSE;
        ScriptOperator scriptOpArg = (ScriptOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = op.getScriptDescription().equals(scriptOpArg.getScriptDescription());
        return isomorphic;
    }

    @Override
    public Boolean visitSubplanOperator(SubplanOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.SUBPLAN)
            return Boolean.FALSE;
        SubplanOperator subplanOpArg = (SubplanOperator) copyAndSubstituteVar(op, arg);
        List<ILogicalPlan> plans = op.getNestedPlans();
        List<ILogicalPlan> plansArg = subplanOpArg.getNestedPlans();
        for (int i = 0; i < plans.size(); i++) {
            List<LogicalOperatorReference> roots = plans.get(i).getRoots();
            List<LogicalOperatorReference> rootsArg = plansArg.get(i).getRoots();
            if (roots.size() == rootsArg.size())
                return Boolean.FALSE;
            for (int j = 0; j < roots.size(); j++) {
                ILogicalOperator topOp1 = roots.get(j).getOperator();
                ILogicalOperator topOp2 = rootsArg.get(j).getOperator();
                boolean isomorphic = this.checkBottomUp(topOp1, topOp2);
                if (!isomorphic)
                    return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    @Override
    public Boolean visitUnionOperator(UnionAllOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.UNIONALL)
            return Boolean.FALSE;
        UnionAllOperator unionOpArg = (UnionAllOperator) copyAndSubstituteVar(op, arg);
        List<Triple<LogicalVariable, LogicalVariable, LogicalVariable>> mapping = op.getVariableMappings();
        List<Triple<LogicalVariable, LogicalVariable, LogicalVariable>> mappingArg = unionOpArg.getVariableMappings();
        if (mapping.size() != mappingArg.size())
            return Boolean.FALSE;
        return VariableUtilities.varListEqualUnordered(mapping, mappingArg);
    }

    @Override
    public Boolean visitUnnestOperator(UnnestOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.UNNEST)
            return Boolean.FALSE;
        UnnestOperator unnestOpArg = (UnnestOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(op.getVariables(), unnestOpArg.getVariables())
                && variableEqual(op.getPositionalVariable(), unnestOpArg.getPositionalVariable());
        if (!isomorphic)
            return Boolean.FALSE;
        isomorphic = op.getExpressionRef().getExpression().equals(unnestOpArg.getExpressionRef().getExpression());
        return isomorphic;
    }

    @Override
    public Boolean visitUnnestMapOperator(UnnestMapOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.UNNEST_MAP)
            return Boolean.FALSE;
        UnnestOperator unnestOpArg = (UnnestOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(op.getVariables(), unnestOpArg.getVariables());
        if (!isomorphic)
            return Boolean.FALSE;
        isomorphic = op.getExpressionRef().getExpression().equals(unnestOpArg.getExpressionRef().getExpression());
        return isomorphic;
    }

    @Override
    public Boolean visitDataScanOperator(DataSourceScanOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.DATASOURCESCAN)
            return Boolean.FALSE;
        DataSourceScanOperator argScan = (DataSourceScanOperator) arg;
        if (!argScan.getDataSource().toString().equals(op.getDataSource().toString()))
            return Boolean.FALSE;
        DataSourceScanOperator scanOpArg = (DataSourceScanOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(op.getVariables(), scanOpArg.getVariables())
                && op.getDataSource().toString().equals(scanOpArg.getDataSource().toString());
        return isomorphic;
    }

    @Override
    public Boolean visitDistinctOperator(DistinctOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.DISTINCT)
            return Boolean.FALSE;
        DistinctOperator distinctOpArg = (DistinctOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = compareExpressions(op.getExpressions(), distinctOpArg.getExpressions());
        return isomorphic;
    }

    @Override
    public Boolean visitExchangeOperator(ExchangeOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.EXCHANGE)
            return Boolean.FALSE;
        // require the same partition property
        if (!(op.getPhysicalOperator().getOperatorTag() == aop.getPhysicalOperator().getOperatorTag()))
            return Boolean.FALSE;
        variableMapping.clear();
        IsomorphismUtilities.mapVariablesTopDown(op, arg, variableMapping);
        IPhysicalPropertiesVector properties = op.getPhysicalOperator().getDeliveredProperties();
        IPhysicalPropertiesVector propertiesArg = aop.getPhysicalOperator().getDeliveredProperties();
        if (properties == null && propertiesArg == null)
            return Boolean.TRUE;
        if (properties == null || propertiesArg == null)
            return Boolean.FALSE;
        IPartitioningProperty partProp = properties.getPartitioningProperty();
        IPartitioningProperty partPropArg = propertiesArg.getPartitioningProperty();
        if (!partProp.getPartitioningType().equals(partPropArg.getPartitioningType()))
            return Boolean.FALSE;
        List<LogicalVariable> columns = new ArrayList<LogicalVariable>();
        partProp.getColumns(columns);
        List<LogicalVariable> columnsArg = new ArrayList<LogicalVariable>();
        partPropArg.getColumns(columnsArg);
        if (columns.size() != columnsArg.size())
            return Boolean.FALSE;
        if (columns.size() == 0)
            return Boolean.TRUE;
        for (int i = 0; i < columnsArg.size(); i++) {
            LogicalVariable rightVar = columnsArg.get(i);
            LogicalVariable leftVar = variableMapping.get(rightVar);
            if (leftVar != null)
                columnsArg.set(i, leftVar);
        }
        return VariableUtilities.varListEqualUnordered(columns, columnsArg);
    }

    @Override
    public Boolean visitWriteOperator(WriteOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.WRITE)
            return Boolean.FALSE;
        WriteOperator writeOpArg = (WriteOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(op.getSchema(), writeOpArg.getSchema());
        return isomorphic;
    }

    @Override
    public Boolean visitWriteResultOperator(WriteResultOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.WRITE_RESULT)
            return Boolean.FALSE;
        WriteResultOperator writeOpArg = (WriteResultOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(op.getSchema(), writeOpArg.getSchema());
        if (!op.getDataSource().equals(writeOpArg.getDataSource()))
            isomorphic = false;
        if (!op.getPayloadExpression().equals(writeOpArg.getPayloadExpression()))
            isomorphic = false;
        return isomorphic;
    }

    @Override
    public Boolean visitInsertDeleteOperator(InsertDeleteOperator op, ILogicalOperator arg) throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.INSERT_DELETE)
            return Boolean.FALSE;
        InsertDeleteOperator insertOpArg = (InsertDeleteOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(op.getSchema(), insertOpArg.getSchema());
        if (!op.getDataSource().equals(insertOpArg.getDataSource()))
            isomorphic = false;
        if (!op.getPayloadExpression().equals(insertOpArg.getPayloadExpression()))
            isomorphic = false;
        return isomorphic;
    }

    @Override
    public Boolean visitIndexInsertDeleteOperator(IndexInsertDeleteOperator op, ILogicalOperator arg)
            throws AlgebricksException {
        AbstractLogicalOperator aop = (AbstractLogicalOperator) arg;
        if (aop.getOperatorTag() != LogicalOperatorTag.INDEX_INSERT_DELETE)
            return Boolean.FALSE;
        IndexInsertDeleteOperator insertOpArg = (IndexInsertDeleteOperator) copyAndSubstituteVar(op, arg);
        boolean isomorphic = VariableUtilities.varListEqualUnordered(op.getSchema(), insertOpArg.getSchema());
        if (!op.getDataSourceIndex().equals(insertOpArg.getDataSourceIndex()))
            isomorphic = false;
        return isomorphic;
    }

    @Override
    public Boolean visitSinkOperator(SinkOperator op, ILogicalOperator arg) throws AlgebricksException {
        return true;
    }

    private Boolean compareExpressions(List<LogicalExpressionReference> opExprs,
            List<LogicalExpressionReference> argExprs) {
        if (opExprs.size() != argExprs.size())
            return Boolean.FALSE;
        for (int i = 0; i < opExprs.size(); i++) {
            boolean isomorphic = opExprs.get(i).getExpression().equals(argExprs.get(i).getExpression());
            if (!isomorphic)
                return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    private Boolean compareIOrderAndExpressions(List<Pair<IOrder, LogicalExpressionReference>> opOrderExprs,
            List<Pair<IOrder, LogicalExpressionReference>> argOrderExprs) {
        if (opOrderExprs.size() != argOrderExprs.size())
            return Boolean.FALSE;
        for (int i = 0; i < opOrderExprs.size(); i++) {
            boolean isomorphic = opOrderExprs.get(i).first.equals(argOrderExprs.get(i).first);
            if (!isomorphic)
                return Boolean.FALSE;
            isomorphic = opOrderExprs.get(i).second.getExpression().equals(argOrderExprs.get(i).second.getExpression());
            if (!isomorphic)
                return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    private Boolean checkBottomUp(ILogicalOperator op1, ILogicalOperator op2) throws AlgebricksException {
        List<LogicalOperatorReference> inputs1 = op1.getInputs();
        List<LogicalOperatorReference> inputs2 = op2.getInputs();
        if (inputs1.size() != inputs2.size())
            return Boolean.FALSE;
        for (int i = 0; i < inputs1.size(); i++) {
            ILogicalOperator input1 = inputs1.get(i).getOperator();
            ILogicalOperator input2 = inputs2.get(i).getOperator();
            boolean isomorphic = checkBottomUp(input1, input2);
            if (!isomorphic)
                return Boolean.FALSE;
        }
        return IsomorphismUtilities.isOperatorIsomorphic(op1, op2);
    }

    private ILogicalOperator copyAndSubstituteVar(ILogicalOperator op, ILogicalOperator argOp)
            throws AlgebricksException {
        ILogicalOperator newOp = IsomorphismOperatorVisitor.deepCopy(argOp);
        variableMapping.clear();
        IsomorphismUtilities.mapVariablesTopDown(op, argOp, variableMapping);

        List<LogicalVariable> liveVars = new ArrayList<LogicalVariable>();
        if (argOp.getInputs().size() > 0)
            for (int i = 0; i < argOp.getInputs().size(); i++)
                VariableUtilities.getLiveVariables(argOp.getInputs().get(i).getOperator(), liveVars);
        List<LogicalVariable> producedVars = new ArrayList<LogicalVariable>();
        VariableUtilities.getProducedVariables(argOp, producedVars);
        List<LogicalVariable> producedVarsNew = new ArrayList<LogicalVariable>();
        VariableUtilities.getProducedVariables(op, producedVarsNew);

        if (producedVars.size() != producedVarsNew.size())
            return newOp;
        for (Entry<LogicalVariable, LogicalVariable> map : variableMapping.entrySet()) {
            if (liveVars.contains(map.getKey())) {
                VariableUtilities.substituteVariables(newOp, map.getKey(), map.getValue(), null);
            }
        }
        for (int i = 0; i < producedVars.size(); i++)
            VariableUtilities.substituteVariables(newOp, producedVars.get(i), producedVarsNew.get(i), null);
        return newOp;
    }

    public List<Pair<LogicalVariable, ILogicalExpression>> getPairList(List<LogicalVariable> vars,
            List<LogicalExpressionReference> exprs) throws AlgebricksException {
        List<Pair<LogicalVariable, ILogicalExpression>> list = new ArrayList<Pair<LogicalVariable, ILogicalExpression>>();
        if (vars.size() != exprs.size())
            throw new AlgebricksException("variable list size does not equal to expression list size ");
        for (int i = 0; i < vars.size(); i++) {
            list.add(new Pair<LogicalVariable, ILogicalExpression>(vars.get(i), exprs.get(i).getExpression()));
        }
        return list;
    }

    private static ILogicalOperator deepCopy(ILogicalOperator op) throws AlgebricksException {
        OperatorDeepCopyVisitor visitor = new OperatorDeepCopyVisitor();
        return op.accept(visitor, null);
    }

    private static ILogicalPlan deepCopy(ILogicalPlan plan) throws AlgebricksException {
        List<LogicalOperatorReference> roots = plan.getRoots();
        List<LogicalOperatorReference> newRoots = new ArrayList<LogicalOperatorReference>();
        for (LogicalOperatorReference opRef : roots)
            newRoots.add(new LogicalOperatorReference(bottomUpCopyOperators(opRef.getOperator())));
        return new ALogicalPlanImpl(newRoots);
    }

    private static ILogicalOperator bottomUpCopyOperators(ILogicalOperator op) throws AlgebricksException {
        ILogicalOperator newOp = deepCopy(op);
        newOp.getInputs().clear();
        for (LogicalOperatorReference child : op.getInputs())
            newOp.getInputs().add(new LogicalOperatorReference(bottomUpCopyOperators(child.getOperator())));
        return newOp;
    }

    private static boolean variableEqual(LogicalVariable var, LogicalVariable varArg) {
        if (var == null && varArg == null)
            return true;
        if (var.equals(varArg))
            return true;
        else
            return false;
    }

    private static class OperatorDeepCopyVisitor implements ILogicalOperatorVisitor<ILogicalOperator, Void> {

        @Override
        public ILogicalOperator visitAggregateOperator(AggregateOperator op, Void arg) throws AlgebricksException {
            ArrayList<LogicalVariable> newList = new ArrayList<LogicalVariable>();
            ArrayList<LogicalExpressionReference> newExpressions = new ArrayList<LogicalExpressionReference>();
            newList.addAll(op.getVariables());
            deepCopyExpressionRefs(newExpressions, op.getExpressions());
            return new AggregateOperator(newList, newExpressions);
        }

        @Override
        public ILogicalOperator visitRunningAggregateOperator(RunningAggregateOperator op, Void arg)
                throws AlgebricksException {
            ArrayList<LogicalVariable> newList = new ArrayList<LogicalVariable>();
            ArrayList<LogicalExpressionReference> newExpressions = new ArrayList<LogicalExpressionReference>();
            newList.addAll(op.getVariables());
            deepCopyExpressionRefs(newExpressions, op.getExpressions());
            return new RunningAggregateOperator(newList, newExpressions);
        }

        @Override
        public ILogicalOperator visitEmptyTupleSourceOperator(EmptyTupleSourceOperator op, Void arg)
                throws AlgebricksException {
            return new EmptyTupleSourceOperator();
        }

        @Override
        public ILogicalOperator visitGroupByOperator(GroupByOperator op, Void arg) throws AlgebricksException {
            List<Pair<LogicalVariable, LogicalExpressionReference>> groupByList = new ArrayList<Pair<LogicalVariable, LogicalExpressionReference>>();
            List<Pair<LogicalVariable, LogicalExpressionReference>> decoList = new ArrayList<Pair<LogicalVariable, LogicalExpressionReference>>();
            ArrayList<ILogicalPlan> newSubplans = new ArrayList<ILogicalPlan>();
            for (Pair<LogicalVariable, LogicalExpressionReference> pair : op.getGroupByList())
                groupByList.add(new Pair<LogicalVariable, LogicalExpressionReference>(pair.first,
                        deepCopyExpressionRef(pair.second)));
            for (Pair<LogicalVariable, LogicalExpressionReference> pair : op.getDecorList())
                decoList.add(new Pair<LogicalVariable, LogicalExpressionReference>(pair.first,
                        deepCopyExpressionRef(pair.second)));
            for (ILogicalPlan plan : op.getNestedPlans()) {
                newSubplans.add(IsomorphismOperatorVisitor.deepCopy(plan));
            }
            return new GroupByOperator(groupByList, decoList, newSubplans);
        }

        @Override
        public ILogicalOperator visitLimitOperator(LimitOperator op, Void arg) throws AlgebricksException {
            return new LimitOperator(deepCopyExpressionRef(op.getMaxObjects()).getExpression(), deepCopyExpressionRef(
                    op.getOffset()).getExpression(), op.isTopmostLimitOp());
        }

        @Override
        public ILogicalOperator visitDieOperator(DieOperator op, Void arg) throws AlgebricksException {
            return new DieOperator(deepCopyExpressionRef(op.getAfterObjects()).getExpression());
        }

        @Override
        public ILogicalOperator visitInnerJoinOperator(InnerJoinOperator op, Void arg) throws AlgebricksException {
            return new InnerJoinOperator(deepCopyExpressionRef(op.getCondition()), op.getInputs().get(0), op
                    .getInputs().get(1));
        }

        @Override
        public ILogicalOperator visitLeftOuterJoinOperator(LeftOuterJoinOperator op, Void arg)
                throws AlgebricksException {
            return new LeftOuterJoinOperator(deepCopyExpressionRef(op.getCondition()), op.getInputs().get(0), op
                    .getInputs().get(1));
        }

        @Override
        public ILogicalOperator visitNestedTupleSourceOperator(NestedTupleSourceOperator op, Void arg)
                throws AlgebricksException {
            return new NestedTupleSourceOperator(null);
        }

        @Override
        public ILogicalOperator visitOrderOperator(OrderOperator op, Void arg) throws AlgebricksException {
            return new OrderOperator(this.deepCopyOrderAndExpression(op.getOrderExpressions()));
        }

        @Override
        public ILogicalOperator visitAssignOperator(AssignOperator op, Void arg) throws AlgebricksException {
            ArrayList<LogicalVariable> newList = new ArrayList<LogicalVariable>();
            ArrayList<LogicalExpressionReference> newExpressions = new ArrayList<LogicalExpressionReference>();
            newList.addAll(op.getVariables());
            deepCopyExpressionRefs(newExpressions, op.getExpressions());
            return new AssignOperator(newList, newExpressions);
        }

        @Override
        public ILogicalOperator visitSelectOperator(SelectOperator op, Void arg) throws AlgebricksException {
            return new SelectOperator(deepCopyExpressionRef(op.getCondition()));
        }

        @Override
        public ILogicalOperator visitProjectOperator(ProjectOperator op, Void arg) throws AlgebricksException {
            ArrayList<LogicalVariable> newList = new ArrayList<LogicalVariable>();
            newList.addAll(op.getVariables());
            return new ProjectOperator(newList);
        }

        @Override
        public ILogicalOperator visitPartitioningSplitOperator(PartitioningSplitOperator op, Void arg)
                throws AlgebricksException {
            ArrayList<LogicalExpressionReference> newExpressions = new ArrayList<LogicalExpressionReference>();
            deepCopyExpressionRefs(newExpressions, Arrays.asList(op.getExpressions()));
            return new PartitioningSplitOperator(newExpressions.toArray(new LogicalExpressionReference[0]),
                    op.hasDefault());
        }

        @Override
        public ILogicalOperator visitReplicateOperator(ReplicateOperator op, Void arg) throws AlgebricksException {
            return new ReplicateOperator(op.getOutputArity());
        }

        @Override
        public ILogicalOperator visitScriptOperator(ScriptOperator op, Void arg) throws AlgebricksException {
            ArrayList<LogicalVariable> newInputList = new ArrayList<LogicalVariable>();
            ArrayList<LogicalVariable> newOutputList = new ArrayList<LogicalVariable>();
            newInputList.addAll(op.getInputVariables());
            newOutputList.addAll(op.getOutputVariables());
            return new ScriptOperator(op.getScriptDescription(), newInputList, newOutputList);
        }

        @Override
        public ILogicalOperator visitSubplanOperator(SubplanOperator op, Void arg) throws AlgebricksException {
            ArrayList<ILogicalPlan> newSubplans = new ArrayList<ILogicalPlan>();
            for (ILogicalPlan plan : op.getNestedPlans()) {
                newSubplans.add(IsomorphismOperatorVisitor.deepCopy(plan));
            }
            return new SubplanOperator(newSubplans);
        }

        @Override
        public ILogicalOperator visitUnionOperator(UnionAllOperator op, Void arg) throws AlgebricksException {
            List<Triple<LogicalVariable, LogicalVariable, LogicalVariable>> newVarMap = new ArrayList<Triple<LogicalVariable, LogicalVariable, LogicalVariable>>();
            List<Triple<LogicalVariable, LogicalVariable, LogicalVariable>> varMap = op.getVariableMappings();
            for (Triple<LogicalVariable, LogicalVariable, LogicalVariable> triple : varMap)
                newVarMap.add(new Triple<LogicalVariable, LogicalVariable, LogicalVariable>(triple.first,
                        triple.second, triple.third));
            return new UnionAllOperator(newVarMap);
        }

        @Override
        public ILogicalOperator visitUnnestOperator(UnnestOperator op, Void arg) throws AlgebricksException {
            return new UnnestOperator(op.getVariable(), deepCopyExpressionRef(op.getExpressionRef()),
                    op.getPositionalVariable(), op.getPositionalVariableType());
        }

        @Override
        public ILogicalOperator visitUnnestMapOperator(UnnestMapOperator op, Void arg) throws AlgebricksException {
            ArrayList<LogicalVariable> newInputList = new ArrayList<LogicalVariable>();
            newInputList.addAll(op.getVariables());
            return new UnnestMapOperator(newInputList, deepCopyExpressionRef(op.getExpressionRef()),
                    new ArrayList<Object>(op.getVariableTypes()));
        }

        @Override
        public ILogicalOperator visitDataScanOperator(DataSourceScanOperator op, Void arg) throws AlgebricksException {
            ArrayList<LogicalVariable> newInputList = new ArrayList<LogicalVariable>();
            newInputList.addAll(op.getVariables());
            return new DataSourceScanOperator(newInputList, op.getDataSource());
        }

        @Override
        public ILogicalOperator visitDistinctOperator(DistinctOperator op, Void arg) throws AlgebricksException {
            ArrayList<LogicalExpressionReference> newExpressions = new ArrayList<LogicalExpressionReference>();
            deepCopyExpressionRefs(newExpressions, op.getExpressions());
            return new DistinctOperator(newExpressions);
        }

        @Override
        public ILogicalOperator visitExchangeOperator(ExchangeOperator op, Void arg) throws AlgebricksException {
            return new ExchangeOperator();
        }

        @Override
        public ILogicalOperator visitWriteOperator(WriteOperator op, Void arg) throws AlgebricksException {
            ArrayList<LogicalExpressionReference> newExpressions = new ArrayList<LogicalExpressionReference>();
            deepCopyExpressionRefs(newExpressions, op.getExpressions());
            return new WriteOperator(newExpressions, op.getDataSink());
        }

        @Override
        public ILogicalOperator visitWriteResultOperator(WriteResultOperator op, Void arg) throws AlgebricksException {
            ArrayList<LogicalExpressionReference> newKeyExpressions = new ArrayList<LogicalExpressionReference>();
            deepCopyExpressionRefs(newKeyExpressions, op.getKeyExpressions());
            return new WriteResultOperator(op.getDataSource(), deepCopyExpressionRef(op.getPayloadExpression()),
                    newKeyExpressions);
        }

        @Override
        public ILogicalOperator visitInsertDeleteOperator(InsertDeleteOperator op, Void arg) throws AlgebricksException {
            List<LogicalExpressionReference> newKeyExpressions = new ArrayList<LogicalExpressionReference>();
            deepCopyExpressionRefs(newKeyExpressions, op.getPrimaryKeyExpressions());
            return new InsertDeleteOperator(op.getDataSource(), deepCopyExpressionRef(op.getPayloadExpression()),
                    newKeyExpressions, op.getOperation());
        }

        @Override
        public ILogicalOperator visitIndexInsertDeleteOperator(IndexInsertDeleteOperator op, Void arg)
                throws AlgebricksException {
            List<LogicalExpressionReference> newPrimaryKeyExpressions = new ArrayList<LogicalExpressionReference>();
            deepCopyExpressionRefs(newPrimaryKeyExpressions, op.getPrimaryKeyExpressions());
            List<LogicalExpressionReference> newSecondaryKeyExpressions = new ArrayList<LogicalExpressionReference>();
            deepCopyExpressionRefs(newSecondaryKeyExpressions, op.getSecondaryKeyExpressions());
            return new IndexInsertDeleteOperator(op.getDataSourceIndex(), newPrimaryKeyExpressions,
                    newSecondaryKeyExpressions, op.getOperation());
        }

        @Override
        public ILogicalOperator visitSinkOperator(SinkOperator op, Void arg) throws AlgebricksException {
            return new SinkOperator();
        }

        private void deepCopyExpressionRefs(List<LogicalExpressionReference> newExprs,
                List<LogicalExpressionReference> oldExprs) {
            for (LogicalExpressionReference oldExpr : oldExprs)
                newExprs.add(new LogicalExpressionReference(((AbstractLogicalExpression) oldExpr.getExpression())
                        .cloneExpression()));
        }

        private LogicalExpressionReference deepCopyExpressionRef(LogicalExpressionReference oldExpr) {
            return new LogicalExpressionReference(
                    ((AbstractLogicalExpression) oldExpr.getExpression()).cloneExpression());
        }

        private List<Pair<IOrder, LogicalExpressionReference>> deepCopyOrderAndExpression(
                List<Pair<IOrder, LogicalExpressionReference>> ordersAndExprs) {
            List<Pair<IOrder, LogicalExpressionReference>> newOrdersAndExprs = new ArrayList<Pair<IOrder, LogicalExpressionReference>>();
            for (Pair<IOrder, LogicalExpressionReference> pair : ordersAndExprs)
                newOrdersAndExprs.add(new Pair<IOrder, LogicalExpressionReference>(pair.first,
                        deepCopyExpressionRef(pair.second)));
            return newOrdersAndExprs;
        }
    }

}

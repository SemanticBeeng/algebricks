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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.exceptions.NotImplementedException;
import edu.uci.ics.algebricks.compiler.algebra.base.EquivalenceClass;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AbstractFunctionCallExpression.FunctionKind;
import edu.uci.ics.algebricks.compiler.algebra.expressions.UnnestingFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractUnnestOperator;
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
import edu.uci.ics.algebricks.compiler.algebra.properties.FunctionalDependency;
import edu.uci.ics.algebricks.compiler.algebra.properties.LocalGroupingProperty;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalOperatorVisitor;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.config.AlgebricksConfig;
import edu.uci.ics.algebricks.utils.Pair;

public class FDsAndEquivClassesVisitor implements ILogicalOperatorVisitor<Void, IOptimizationContext> {

    @Override
    public Void visitAggregateOperator(AggregateOperator op, IOptimizationContext ctx) throws AlgebricksException {
        ctx.putEquivalenceClassMap(op, new HashMap<LogicalVariable, EquivalenceClass>());
        ctx.putFDList(op, new ArrayList<FunctionalDependency>());
        return null;
    }

    @Override
    public Void visitAssignOperator(AssignOperator op, IOptimizationContext ctx) throws AlgebricksException {
        ILogicalOperator inp1 = op.getInputs().get(0).getOperator();
        Map<LogicalVariable, EquivalenceClass> eqClasses = getOrComputeEqClasses(inp1, ctx);
        ctx.putEquivalenceClassMap(op, eqClasses);
        List<LogicalVariable> used = new ArrayList<LogicalVariable>();
        VariableUtilities.getUsedVariables(op, used);
        List<FunctionalDependency> fds1 = getOrComputeFDs(inp1, ctx);
        List<FunctionalDependency> eFds = new ArrayList<FunctionalDependency>(fds1.size());
        for (FunctionalDependency fd : fds1) {
            if (fd.getTail().containsAll(used)) {
                List<LogicalVariable> hd = new ArrayList<LogicalVariable>(fd.getHead());
                List<LogicalVariable> tl = new ArrayList<LogicalVariable>(fd.getTail());
                tl.addAll(op.getVariables());
                FunctionalDependency fd2 = new FunctionalDependency(hd, tl);
                eFds.add(fd2);
            } else {
                eFds.add(fd);
            }
        }
        ctx.putFDList(op, eFds);
        return null;
    }

    @Override
    public Void visitDataScanOperator(DataSourceScanOperator op, IOptimizationContext ctx) throws AlgebricksException {
        ILogicalOperator inp1 = op.getInputs().get(0).getOperator();
        Map<LogicalVariable, EquivalenceClass> eqClasses = getOrComputeEqClasses(inp1, ctx);
        ctx.putEquivalenceClassMap(op, eqClasses);
        List<FunctionalDependency> fds = new ArrayList<FunctionalDependency>(getOrComputeFDs(inp1, ctx));
        ctx.putFDList(op, fds);
        op.getDataSource().computeFDs(op.getVariables(), fds);
        return null;
    }

    @Override
    public Void visitDistinctOperator(DistinctOperator op, IOptimizationContext ctx) throws AlgebricksException {
        ILogicalOperator op0 = op.getInputs().get(0).getOperator();
        List<FunctionalDependency> functionalDependencies = new ArrayList<FunctionalDependency>();
        ctx.putFDList(op, functionalDependencies);
        for (FunctionalDependency inherited : getOrComputeFDs(op0, ctx)) {
            boolean isCoveredByDistinctByVars = true;
            for (LogicalVariable v : inherited.getHead()) {
                if (!op.isDistinctByVar(v)) {
                    isCoveredByDistinctByVars = false;
                }
            }
            if (isCoveredByDistinctByVars) {
                List<LogicalVariable> newTail = new ArrayList<LogicalVariable>();
                for (LogicalVariable v2 : inherited.getTail()) {
                    if (op.isDistinctByVar(v2)) {
                        newTail.add(v2);
                    }
                }
                if (!newTail.isEmpty()) {
                    List<LogicalVariable> newHead = new ArrayList<LogicalVariable>(inherited.getHead());
                    FunctionalDependency newFd = new FunctionalDependency(newHead, newTail);
                    functionalDependencies.add(newFd);
                }
            }
        }
        Set<LogicalVariable> gbySet = new HashSet<LogicalVariable>();
        List<LogicalExpressionReference> expressions = op.getExpressions();
        for (LogicalExpressionReference pRef : expressions) {
            ILogicalExpression p = pRef.getExpression();
            if (p.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression v = (VariableReferenceExpression) p;
                gbySet.add(v.getVariableReference());
            }
        }
        LocalGroupingProperty lgp = new LocalGroupingProperty(gbySet);

        Map<LogicalVariable, EquivalenceClass> equivalenceClasses = getOrComputeEqClasses(op0, ctx);
        ctx.putEquivalenceClassMap(op, equivalenceClasses);

        lgp.normalizeGroupingColumns(equivalenceClasses, functionalDependencies);
        Set<LogicalVariable> normSet = lgp.getColumnSet();
        List<LogicalExpressionReference> newDistinctByList = new ArrayList<LogicalExpressionReference>();
        for (LogicalExpressionReference p2Ref : expressions) {
            ILogicalExpression p2 = p2Ref.getExpression();
            if (p2.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression var2 = (VariableReferenceExpression) p2;
                LogicalVariable v2 = var2.getVariableReference();
                if (normSet.contains(v2)) {
                    newDistinctByList.add(p2Ref);
                }
            } else {
                newDistinctByList.add(p2Ref);
            }
        }
        expressions.clear();
        expressions.addAll(newDistinctByList);
        return null;
    }

    @Override
    public Void visitEmptyTupleSourceOperator(EmptyTupleSourceOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        ctx.putEquivalenceClassMap(op, new HashMap<LogicalVariable, EquivalenceClass>());
        ctx.putFDList(op, new ArrayList<FunctionalDependency>());
        return null;
    }

    @Override
    public Void visitExchangeOperator(ExchangeOperator op, IOptimizationContext ctx) throws AlgebricksException {
        propagateFDsAndEquivClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitGroupByOperator(GroupByOperator op, IOptimizationContext ctx) throws AlgebricksException {
        Map<LogicalVariable, EquivalenceClass> equivalenceClasses = new HashMap<LogicalVariable, EquivalenceClass>();
        List<FunctionalDependency> functionalDependencies = new ArrayList<FunctionalDependency>();
        ctx.putEquivalenceClassMap(op, equivalenceClasses);
        ctx.putFDList(op, functionalDependencies);

        List<FunctionalDependency> inheritedFDs = new ArrayList<FunctionalDependency>();
        for (ILogicalPlan p : op.getNestedPlans()) {
            for (LogicalOperatorReference r : p.getRoots()) {
                ILogicalOperator op2 = r.getOperator();
                equivalenceClasses.putAll(getOrComputeEqClasses(op2, ctx));
                inheritedFDs.addAll(getOrComputeFDs(op2, ctx));
            }
        }

        ILogicalOperator op0 = op.getInputs().get(0).getOperator();
        inheritedFDs.addAll(getOrComputeFDs(op0, ctx));
        Map<LogicalVariable, EquivalenceClass> inheritedEcs = getOrComputeEqClasses(op0, ctx);
        for (FunctionalDependency inherited : inheritedFDs) {
            boolean isCoveredByGbyOrDecorVars = true;
            List<LogicalVariable> newHead = new ArrayList<LogicalVariable>(inherited.getHead().size());
            for (LogicalVariable v : inherited.getHead()) {
                LogicalVariable vnew = getNewGbyVar(op, v);
                if (vnew == null) {
                    vnew = getNewDecorVar(op, v);
                    if (vnew == null) {
                        isCoveredByGbyOrDecorVars = false;
                    }
                    break;
                }
                newHead.add(vnew);
            }

            if (isCoveredByGbyOrDecorVars) {
                List<LogicalVariable> newTail = new ArrayList<LogicalVariable>();
                for (LogicalVariable v2 : inherited.getTail()) {
                    LogicalVariable v3 = getNewGbyVar(op, v2);
                    if (v3 != null) {
                        newTail.add(v3);
                    }
                }
                if (!newTail.isEmpty()) {
                    FunctionalDependency newFd = new FunctionalDependency(newHead, newTail);
                    functionalDependencies.add(newFd);
                }
            }
        }

        List<LogicalVariable> premiseGby = new LinkedList<LogicalVariable>();
        List<Pair<LogicalVariable, LogicalExpressionReference>> gByList = op.getGroupByList();
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gByList) {
            premiseGby.add(p.first);
        }

        List<Pair<LogicalVariable, LogicalExpressionReference>> decorList = op.getDecorList();

        LinkedList<LogicalVariable> conclDecor = new LinkedList<LogicalVariable>();
        for (Pair<LogicalVariable, LogicalExpressionReference> p : decorList) {
            conclDecor.add(GroupByOperator.getDecorVariable(p));
        }
        if (!conclDecor.isEmpty()) {
            functionalDependencies.add(new FunctionalDependency(premiseGby, conclDecor));
        }

        Set<LogicalVariable> gbySet = new HashSet<LogicalVariable>();
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gByList) {
            ILogicalExpression expr = p.second.getExpression();
            if (expr.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression v = (VariableReferenceExpression) expr;
                gbySet.add(v.getVariableReference());
            }
        }
        LocalGroupingProperty lgp = new LocalGroupingProperty(gbySet);
        lgp.normalizeGroupingColumns(inheritedEcs, inheritedFDs);
        Set<LogicalVariable> normSet = lgp.getColumnSet();
        List<Pair<LogicalVariable, LogicalExpressionReference>> newGbyList = new ArrayList<Pair<LogicalVariable, LogicalExpressionReference>>();
        boolean changed = false;
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gByList) {
            ILogicalExpression expr = p.second.getExpression();
            if (expr.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression varRef = (VariableReferenceExpression) expr;
                LogicalVariable v2 = varRef.getVariableReference();
                EquivalenceClass ec2 = inheritedEcs.get(v2);
                LogicalVariable v3;
                if (ec2 != null && !ec2.representativeIsConst()) {
                    v3 = ec2.getVariableRepresentative();
                } else {
                    v3 = v2;
                }
                if (normSet.contains(v3)) {
                    newGbyList.add(p);
                } else {
                    changed = true;
                    decorList.add(p);
                }
            } else {
                newGbyList.add(p);
            }
        }
        if (changed) {
            AlgebricksConfig.ALGEBRICKS_LOGGER.fine(">>>> Group-by list changed from "
                    + GroupByOperator.veListToString(gByList) + " to " + GroupByOperator.veListToString(newGbyList)
                    + ".\n");
        }
        gByList.clear();
        gByList.addAll(newGbyList);
        return null;
    }

    @Override
    public Void visitInnerJoinOperator(InnerJoinOperator op, IOptimizationContext ctx) throws AlgebricksException {
        Map<LogicalVariable, EquivalenceClass> equivalenceClasses = new HashMap<LogicalVariable, EquivalenceClass>();
        List<FunctionalDependency> functionalDependencies = new ArrayList<FunctionalDependency>();
        ctx.putEquivalenceClassMap(op, equivalenceClasses);
        ctx.putFDList(op, functionalDependencies);
        ILogicalOperator op0 = op.getInputs().get(0).getOperator();
        ILogicalOperator op1 = op.getInputs().get(1).getOperator();
        functionalDependencies.addAll(getOrComputeFDs(op0, ctx));
        functionalDependencies.addAll(getOrComputeFDs(op1, ctx));
        equivalenceClasses.putAll(getOrComputeEqClasses(op0, ctx));
        equivalenceClasses.putAll(getOrComputeEqClasses(op1, ctx));
        ILogicalExpression expr = op.getCondition().getExpression();
        expr.getConstraintsAndEquivClasses(functionalDependencies, equivalenceClasses);
        return null;
    }

    @Override
    public Void visitLeftOuterJoinOperator(LeftOuterJoinOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        Map<LogicalVariable, EquivalenceClass> equivalenceClasses = new HashMap<LogicalVariable, EquivalenceClass>();
        List<FunctionalDependency> functionalDependencies = new ArrayList<FunctionalDependency>();
        ctx.putEquivalenceClassMap(op, equivalenceClasses);
        ctx.putFDList(op, functionalDependencies);
        ILogicalOperator opLeft = op.getInputs().get(0).getOperator();
        ILogicalOperator opRight = op.getInputs().get(1).getOperator();
        functionalDependencies.addAll(getOrComputeFDs(opLeft, ctx));
        functionalDependencies.addAll(getOrComputeFDs(opRight, ctx));
        equivalenceClasses.putAll(getOrComputeEqClasses(opLeft, ctx));
        equivalenceClasses.putAll(getOrComputeEqClasses(opRight, ctx));

        Collection<LogicalVariable> leftSideVars;
        if (opLeft.getSchema() == null) {
            leftSideVars = new LinkedList<LogicalVariable>();
            VariableUtilities.getLiveVariables(opLeft, leftSideVars);
            // actually, not all produced vars. are visible (due to projection)
            // so using cached schema is better and faster
        } else {
            leftSideVars = opLeft.getSchema();
        }
        ILogicalExpression expr = op.getCondition().getExpression();
        expr.getConstraintsForOuterJoin(functionalDependencies, leftSideVars);
        return null;
    }

    @Override
    public Void visitLimitOperator(LimitOperator op, IOptimizationContext ctx) throws AlgebricksException {
        propagateFDsAndEquivClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitDieOperator(DieOperator op, IOptimizationContext ctx) throws AlgebricksException {
        propagateFDsAndEquivClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitNestedTupleSourceOperator(NestedTupleSourceOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        AbstractLogicalOperator op1 = (AbstractLogicalOperator) op.getDataSourceReference().getOperator();
        ILogicalOperator inp1 = op1.getInputs().get(0).getOperator();
        Map<LogicalVariable, EquivalenceClass> eqClasses = getOrComputeEqClasses(inp1, ctx);
        ctx.putEquivalenceClassMap(op, eqClasses);
        List<FunctionalDependency> fds = new ArrayList<FunctionalDependency>(getOrComputeFDs(inp1, ctx));
        if (op1.getOperatorTag() == LogicalOperatorTag.GROUP) {
            GroupByOperator gby = (GroupByOperator) op1;
            LinkedList<LogicalVariable> tail = new LinkedList<LogicalVariable>();
            for (LogicalVariable v : gby.getGbyVarList()) {
                tail.add(v);
                // all values for gby vars. are the same
            }
            FunctionalDependency gbyfd = new FunctionalDependency(new LinkedList<LogicalVariable>(), tail);
            fds.add(gbyfd);
        }
        ctx.putFDList(op, fds);
        return null;
    }

    @Override
    public Void visitOrderOperator(OrderOperator op, IOptimizationContext ctx) throws AlgebricksException {
        propagateFDsAndEquivClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitPartitioningSplitOperator(PartitioningSplitOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        throw new NotImplementedException();
    }

    @Override
    public Void visitProjectOperator(ProjectOperator op, IOptimizationContext ctx) throws AlgebricksException {
        propagateFDsAndEquivClassesForUsedVars(op, ctx, op.getVariables());
        return null;
    }

    @Override
    public Void visitReplicateOperator(ReplicateOperator op, IOptimizationContext ctx) throws AlgebricksException {
        propagateFDsAndEquivClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitRunningAggregateOperator(RunningAggregateOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        propagateFDsAndEquivClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitScriptOperator(ScriptOperator op, IOptimizationContext ctx) throws AlgebricksException {
        propagateFDsAndEquivClassesForUsedVars(op, ctx, op.getInputVariables());
        return null;
    }

    @Override
    public Void visitSelectOperator(SelectOperator op, IOptimizationContext ctx) throws AlgebricksException {
        Map<LogicalVariable, EquivalenceClass> equivalenceClasses = new HashMap<LogicalVariable, EquivalenceClass>();
        List<FunctionalDependency> functionalDependencies = new ArrayList<FunctionalDependency>();
        ctx.putEquivalenceClassMap(op, equivalenceClasses);
        ctx.putFDList(op, functionalDependencies);
        ILogicalOperator op0 = op.getInputs().get(0).getOperator();
        functionalDependencies.addAll(getOrComputeFDs(op0, ctx));
        equivalenceClasses.putAll(getOrComputeEqClasses(op0, ctx));
        ILogicalExpression expr = op.getCondition().getExpression();
        expr.getConstraintsAndEquivClasses(functionalDependencies, equivalenceClasses);
        return null;
    }

    @Override
    public Void visitSubplanOperator(SubplanOperator op, IOptimizationContext ctx) throws AlgebricksException {
        Map<LogicalVariable, EquivalenceClass> equivalenceClasses = new HashMap<LogicalVariable, EquivalenceClass>();
        List<FunctionalDependency> functionalDependencies = new ArrayList<FunctionalDependency>();
        ctx.putEquivalenceClassMap(op, equivalenceClasses);
        ctx.putFDList(op, functionalDependencies);
        for (ILogicalPlan p : op.getNestedPlans()) {
            for (LogicalOperatorReference r : p.getRoots()) {
                ILogicalOperator op2 = r.getOperator();
                equivalenceClasses.putAll(getOrComputeEqClasses(op2, ctx));
                functionalDependencies.addAll(getOrComputeFDs(op2, ctx));
            }
        }
        return null;
    }

    @Override
    public Void visitUnionOperator(UnionAllOperator op, IOptimizationContext ctx) throws AlgebricksException {
        setEmptyFDsEqClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitUnnestMapOperator(UnnestMapOperator op, IOptimizationContext ctx) throws AlgebricksException {
        fdsEqClassesForAbstractUnnestOperator(op, ctx);
        return null;
    }

    @Override
    public Void visitUnnestOperator(UnnestOperator op, IOptimizationContext ctx) throws AlgebricksException {
        fdsEqClassesForAbstractUnnestOperator(op, ctx);
        return null;
    }

    @Override
    public Void visitWriteOperator(WriteOperator op, IOptimizationContext ctx) throws AlgebricksException {
        // propagateFDsAndEquivClasses(op, ctx);
        setEmptyFDsEqClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitWriteResultOperator(WriteResultOperator op, IOptimizationContext ctx) throws AlgebricksException {
        // propagateFDsAndEquivClasses(op, ctx);
        setEmptyFDsEqClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitInsertDeleteOperator(InsertDeleteOperator op, IOptimizationContext ctx) throws AlgebricksException {
        setEmptyFDsEqClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitIndexInsertDeleteOperator(IndexInsertDeleteOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        setEmptyFDsEqClasses(op, ctx);
        return null;
    }

    @Override
    public Void visitSinkOperator(SinkOperator op, IOptimizationContext ctx) throws AlgebricksException {
        setEmptyFDsEqClasses(op, ctx);
        return null;
    }

    private void propagateFDsAndEquivClasses(ILogicalOperator op, IOptimizationContext ctx) throws AlgebricksException {
        ILogicalOperator inp1 = op.getInputs().get(0).getOperator();
        Map<LogicalVariable, EquivalenceClass> eqClasses = getOrComputeEqClasses(inp1, ctx);
        ctx.putEquivalenceClassMap(op, eqClasses);
        List<FunctionalDependency> fds = getOrComputeFDs(inp1, ctx);
        ctx.putFDList(op, fds);
    }

    private Map<LogicalVariable, EquivalenceClass> getOrComputeEqClasses(ILogicalOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        Map<LogicalVariable, EquivalenceClass> eqClasses = ctx.getEquivalenceClassMap(op);
        if (eqClasses == null) {
            op.accept(this, ctx);
            eqClasses = ctx.getEquivalenceClassMap(op);
        }
        return eqClasses;
    }

    private List<FunctionalDependency> getOrComputeFDs(ILogicalOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        List<FunctionalDependency> fds = ctx.getFDList(op);
        if (fds == null) {
            op.accept(this, ctx);
            fds = ctx.getFDList(op);
        }
        return fds;
    }

    private void propagateFDsAndEquivClassesForUsedVars(ILogicalOperator op, IOptimizationContext ctx,
            List<LogicalVariable> usedVariables) throws AlgebricksException {
        ILogicalOperator op2 = op.getInputs().get(0).getOperator();
        Map<LogicalVariable, EquivalenceClass> eqClasses = new HashMap<LogicalVariable, EquivalenceClass>();
        ctx.putEquivalenceClassMap(op, eqClasses);
        List<FunctionalDependency> fds = new ArrayList<FunctionalDependency>();
        ctx.putFDList(op, fds);

        Map<LogicalVariable, EquivalenceClass> chldClasses = getOrComputeEqClasses(op2, ctx);
        for (LogicalVariable v : usedVariables) {
            EquivalenceClass ec = eqClasses.get(v);
            if (ec == null) {
                EquivalenceClass oc = chldClasses.get(v);
                if (oc == null) {
                    continue;
                }
                List<LogicalVariable> m = new LinkedList<LogicalVariable>();
                for (LogicalVariable v2 : oc.getMembers()) {
                    if (usedVariables.contains(v2)) {
                        m.add(v2);
                    }
                }
                EquivalenceClass nc;
                if (oc.representativeIsConst()) {
                    nc = new EquivalenceClass(m, oc.getConstRepresentative());
                } else if (m.contains(oc.getVariableRepresentative())) {
                    nc = new EquivalenceClass(m, oc.getVariableRepresentative());
                } else {
                    nc = new EquivalenceClass(m, v);
                }
                for (LogicalVariable v3 : m) {
                    eqClasses.put(v3, nc);
                }
            }
        }

        List<FunctionalDependency> chldFds = getOrComputeFDs(op2, ctx);
        for (FunctionalDependency fd : chldFds) {
            if (!usedVariables.containsAll(fd.getHead())) {
                continue;
            }
            List<LogicalVariable> tl = new LinkedList<LogicalVariable>();
            for (LogicalVariable v : fd.getTail()) {
                if (usedVariables.contains(v)) {
                    tl.add(v);
                }
            }
            if (!tl.isEmpty()) {
                FunctionalDependency newFd = new FunctionalDependency(fd.getHead(), tl);
                fds.add(newFd);
            }
        }
    }

    private void fdsEqClassesForAbstractUnnestOperator(AbstractUnnestOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        ILogicalOperator inp1 = op.getInputs().get(0).getOperator();
        Map<LogicalVariable, EquivalenceClass> eqClasses = getOrComputeEqClasses(inp1, ctx);
        ctx.putEquivalenceClassMap(op, eqClasses);
        List<FunctionalDependency> fds = getOrComputeFDs(inp1, ctx);
        ctx.putFDList(op, fds);

        ILogicalExpression expr = op.getExpressionRef().getExpression();
        if (expr.getExpressionTag() == LogicalExpressionTag.FUNCTION_CALL) {
            AbstractFunctionCallExpression afe = (AbstractFunctionCallExpression) expr;
            if (afe.getKind() == FunctionKind.UNNEST && ((UnnestingFunctionCallExpression) afe).returnsUniqueValues()) {
                List<LogicalVariable> vars = new ArrayList<LogicalVariable>();
                VariableUtilities.getLiveVariables(op, vars);
                ArrayList<LogicalVariable> h = new ArrayList<LogicalVariable>();
                h.addAll(op.getVariables());
                FunctionalDependency fd = new FunctionalDependency(h, vars);
                fds.add(fd);
            }
        }
    }

    public static void setEmptyFDsEqClasses(ILogicalOperator op, IOptimizationContext ctx) {
        Map<LogicalVariable, EquivalenceClass> eqClasses = new HashMap<LogicalVariable, EquivalenceClass>();
        ctx.putEquivalenceClassMap(op, eqClasses);
        List<FunctionalDependency> fds = new ArrayList<FunctionalDependency>();
        ctx.putFDList(op, fds);
    }

    private LogicalVariable getNewGbyVar(GroupByOperator g, LogicalVariable v) {
        for (Pair<LogicalVariable, LogicalExpressionReference> p : g.getGroupByList()) {
            ILogicalExpression e = p.second.getExpression();
            if (e.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                LogicalVariable v2 = ((VariableReferenceExpression) e).getVariableReference();
                if (v2 == v) {
                    return p.first;
                }
            }
        }
        return null;
    }

    private LogicalVariable getNewDecorVar(GroupByOperator g, LogicalVariable v) {
        for (Pair<LogicalVariable, LogicalExpressionReference> p : g.getDecorList()) {
            ILogicalExpression e = p.second.getExpression();
            if (e.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                LogicalVariable v2 = ((VariableReferenceExpression) e).getVariableReference();
                if (v2 == v) {
                    return (p.first != null) ? p.first : v2;
                }
            }
        }
        return null;
    }

}

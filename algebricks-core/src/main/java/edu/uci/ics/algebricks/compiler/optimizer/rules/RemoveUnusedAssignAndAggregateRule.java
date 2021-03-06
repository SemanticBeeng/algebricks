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
package edu.uci.ics.algebricks.compiler.optimizer.rules;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractOperatorWithNestedPlans;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AggregateOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AssignOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;

public class RemoveUnusedAssignAndAggregateRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) {
        return false;
    }

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        if (context.checkIfInDontApplySet(this, opRef.getOperator())) {
            return false;
        }
        Set<LogicalVariable> toRemove = new HashSet<LogicalVariable>();
        collectUnusedAssignedVars((AbstractLogicalOperator) opRef.getOperator(), toRemove, true, context);
        boolean smthToRemove = !toRemove.isEmpty();
        if (smthToRemove) {
            removeUnusedAssigns(opRef, toRemove, context);
        }
        return smthToRemove;
    }

    private void removeUnusedAssigns(LogicalOperatorReference opRef, Set<LogicalVariable> toRemove,
            IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        while (removeFromAssigns(op, toRemove, context) == 0) {
            if (op.getOperatorTag() == LogicalOperatorTag.AGGREGATE) {
                break;
            }
            op = (AbstractLogicalOperator) op.getInputs().get(0).getOperator();
            opRef.setOperator(op);
        }
        Iterator<LogicalOperatorReference> childIter = op.getInputs().iterator();
        while (childIter.hasNext()) {
            LogicalOperatorReference cRef = childIter.next();
            removeUnusedAssigns(cRef, toRemove, context);
        }
        if (op.hasNestedPlans()) {
            AbstractOperatorWithNestedPlans opWithNest = (AbstractOperatorWithNestedPlans) op;
            Iterator<ILogicalPlan> planIter = opWithNest.getNestedPlans().iterator();
            while (planIter.hasNext()) {
                ILogicalPlan p = planIter.next();
                for (LogicalOperatorReference r : p.getRoots()) {
                    removeUnusedAssigns(r, toRemove, context);
                }
            }
        }
    }

    private int removeFromAssigns(AbstractLogicalOperator op, Set<LogicalVariable> toRemove,
            IOptimizationContext context) throws AlgebricksException {
        if (op.getOperatorTag() == LogicalOperatorTag.ASSIGN) {
            AssignOperator assign = (AssignOperator) op;
            if (removeUnusedVarsAndExprs(toRemove, assign.getVariables(), assign.getExpressions())) {
                context.computeAndSetTypeEnvironmentForOperator(assign);
            }
            return assign.getVariables().size();
        } else if (op.getOperatorTag() == LogicalOperatorTag.AGGREGATE) {
            AggregateOperator agg = (AggregateOperator) op;
            if (removeUnusedVarsAndExprs(toRemove, agg.getVariables(), agg.getExpressions())) {
                context.computeAndSetTypeEnvironmentForOperator(agg);
            }
            return agg.getVariables().size();
        }
        return -1;
    }

    private boolean removeUnusedVarsAndExprs(Set<LogicalVariable> toRemove, List<LogicalVariable> varList,
            List<LogicalExpressionReference> exprList) {
        boolean changed = false;
        Iterator<LogicalVariable> varIter = varList.iterator();
        Iterator<LogicalExpressionReference> exprIter = exprList.iterator();
        while (varIter.hasNext()) {
            LogicalVariable v = varIter.next();
            exprIter.next();
            if (toRemove.contains(v)) {
                varIter.remove();
                exprIter.remove();
                changed = true;
            }
        }
        return changed;
    }

    private void collectUnusedAssignedVars(AbstractLogicalOperator op, Set<LogicalVariable> toRemove, boolean first,
            IOptimizationContext context) throws AlgebricksException {
        if (!first) {
            context.addToDontApplySet(this, op);
        }
        for (LogicalOperatorReference c : op.getInputs()) {
            collectUnusedAssignedVars((AbstractLogicalOperator) c.getOperator(), toRemove, false, context);
        }
        if (op.hasNestedPlans()) {
            AbstractOperatorWithNestedPlans opWithNested = (AbstractOperatorWithNestedPlans) op;
            for (ILogicalPlan plan : opWithNested.getNestedPlans()) {
                for (LogicalOperatorReference r : plan.getRoots()) {
                    collectUnusedAssignedVars((AbstractLogicalOperator) r.getOperator(), toRemove, false, context);
                }
            }
        }
        if (op.getOperatorTag() == LogicalOperatorTag.ASSIGN) {
            AssignOperator assign = (AssignOperator) op;
            toRemove.addAll(assign.getVariables());
        } else if (op.getOperatorTag() == LogicalOperatorTag.AGGREGATE) {
            AggregateOperator agg = (AggregateOperator) op;
            toRemove.addAll(agg.getVariables());
        }
        List<LogicalVariable> used = new LinkedList<LogicalVariable>();
        VariableUtilities.getUsedVariables(op, used);
        toRemove.removeAll(used);
    }

}

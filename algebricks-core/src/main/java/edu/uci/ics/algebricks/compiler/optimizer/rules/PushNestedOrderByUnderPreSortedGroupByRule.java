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
import java.util.Set;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.OrderOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.OrderOperator.IOrder;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.AbstractPhysicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.StableSortPOperator;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.compiler.optimizer.base.OptimizationUtil;
import edu.uci.ics.algebricks.utils.Pair;

public class PushNestedOrderByUnderPreSortedGroupByRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        if (op.getOperatorTag() != LogicalOperatorTag.GROUP) {
            return false;
        }
        if (op.getPhysicalOperator() == null) {
            return false;
        }
        AbstractPhysicalOperator pOp = (AbstractPhysicalOperator) op.getPhysicalOperator();
        if (pOp.getOperatorTag() != PhysicalOperatorTag.PRE_CLUSTERED_GROUP_BY) {
            return false;
        }
        GroupByOperator gby = (GroupByOperator) op;
        ILogicalPlan plan = gby.getNestedPlans().get(0);
        AbstractLogicalOperator op1 = (AbstractLogicalOperator) plan.getRoots().get(0).getOperator();
        if (op1.getOperatorTag() != LogicalOperatorTag.AGGREGATE) {
            return false;
        }
        LogicalOperatorReference opRef2 = op1.getInputs().get(0);
        AbstractLogicalOperator op2 = (AbstractLogicalOperator) opRef2.getOperator();
        if (op2.getOperatorTag() != LogicalOperatorTag.ORDER) {
            return false;
        }
        OrderOperator order1 = (OrderOperator) op2;
        if (!isIndependentFromChildren(order1)) {
            return false;
        }
        AbstractPhysicalOperator pOrder1 = (AbstractPhysicalOperator) op2.getPhysicalOperator();
        if (pOrder1.getOperatorTag() != PhysicalOperatorTag.STABLE_SORT
                && pOrder1.getOperatorTag() != PhysicalOperatorTag.IN_MEMORY_STABLE_SORT) {
            return false;
        }
        // StableSortPOperator sort1 = (StableSortPOperator) pOrder1;
        AbstractLogicalOperator op3 = (AbstractLogicalOperator) op.getInputs().get(0).getOperator();
        if (op3.getOperatorTag() != LogicalOperatorTag.ORDER) {
            return false;
        }
        AbstractPhysicalOperator pOp3 = (AbstractPhysicalOperator) op3.getPhysicalOperator();
        if (pOp3.getOperatorTag() != PhysicalOperatorTag.STABLE_SORT) {
            return false;
        }
        OrderOperator order2 = (OrderOperator) op3;
        StableSortPOperator sort2 = (StableSortPOperator) pOp3;
        // int n1 = sort1.getSortColumns().length;
        // int n2 = sort2.getSortColumns().length;
        // OrderColumn[] sortColumns = new OrderColumn[n2 + n1];
        // System.arraycopy(sort2.getSortColumns(), 0, sortColumns, 0, n2);
        // int k = 0;
        for (Pair<IOrder, LogicalExpressionReference> oe : order1.getOrderExpressions()) {
            order2.getOrderExpressions().add(oe);
            // sortColumns[n2 + k] = sort1.getSortColumns()[k];
            // ++k;
        }
        // sort2.setSortColumns(sortColumns);
        sort2.computeDeliveredProperties(order2, null);
        // remove order1
        ILogicalOperator underOrder1 = order1.getInputs().get(0).getOperator();
        opRef2.setOperator(underOrder1);
        return true;
    }

    private boolean isIndependentFromChildren(OrderOperator order1) throws AlgebricksException {
        Set<LogicalVariable> free = new HashSet<LogicalVariable>();
        OptimizationUtil.getFreeVariablesInSelfOrDesc(order1, free);
        Set<LogicalVariable> usedInOrder = new HashSet<LogicalVariable>();
        VariableUtilities.getUsedVariables(order1, usedInOrder);
        return free.containsAll(usedInOrder);
    }

}

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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.algebricks.compiler.algebra.properties.FunctionalDependency;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.compiler.optimizer.base.OperatorManipulationUtil;
import edu.uci.ics.algebricks.compiler.optimizer.base.PhysicalOptimizationsUtil;
import edu.uci.ics.algebricks.utils.Pair;

public abstract class AbstractDecorrelationRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) {
        return false;
    }

    protected boolean descOrSelfIsScanOrJoin(AbstractLogicalOperator op2) {
        LogicalOperatorTag t = op2.getOperatorTag();
        if (t == LogicalOperatorTag.DATASOURCESCAN || t == LogicalOperatorTag.INNERJOIN
                || t == LogicalOperatorTag.LEFTOUTERJOIN) {
            return true;
        }
        if (op2.getInputs().size() != 1) {
            return false;
        }
        AbstractLogicalOperator alo = (AbstractLogicalOperator) op2.getInputs().get(0).getOperator();
        if (descOrSelfIsScanOrJoin(alo)) {
            return true;
        }
        return false;
    }

    protected Set<LogicalVariable> computeGbyVarsUsingPksOnly(Set<LogicalVariable> varSet, AbstractLogicalOperator op,
            IOptimizationContext context) throws AlgebricksException {
        PhysicalOptimizationsUtil.computeFDsAndEquivalenceClasses(op, context);
        List<FunctionalDependency> fdList = context.getFDList(op);
        if (fdList == null) {
            return null;
        }
        // check if any of the FDs is a key
        for (FunctionalDependency fd : fdList) {
            if (fd.getTail().containsAll(varSet)) {
                return new HashSet<LogicalVariable>(fd.getHead());
            }
        }
        return null;
    }

    protected void buildVarExprList(Collection<LogicalVariable> vars, IOptimizationContext context, GroupByOperator g,
            List<Pair<LogicalVariable, LogicalExpressionReference>> outVeList) throws AlgebricksException {
        for (LogicalVariable ov : vars) {
            LogicalVariable newVar = context.newVar();
            ILogicalExpression varExpr = new VariableReferenceExpression(newVar);
            outVeList.add(new Pair<LogicalVariable, LogicalExpressionReference>(ov, new LogicalExpressionReference(
                    varExpr)));
            for (ILogicalPlan p : g.getNestedPlans()) {
                for (LogicalOperatorReference r : p.getRoots()) {
                    OperatorManipulationUtil.substituteVarRec((AbstractLogicalOperator) r.getOperator(), ov, newVar,
                            true, context);
                }
            }
            // g.substituteVarInNestedPlans(ov, newVar);
            // OperatorManipulationUtil.substituteVarRec(lojoin, ov, newVar);
        }
    }

}

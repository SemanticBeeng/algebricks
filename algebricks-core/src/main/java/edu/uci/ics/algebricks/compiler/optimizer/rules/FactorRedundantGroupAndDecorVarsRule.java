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

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AssignOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.hyracks.api.util.Pair;

public class FactorRedundantGroupAndDecorVarsRule implements IAlgebraicRewriteRule {

    private Map<LogicalVariable, LogicalVariable> decorVarRhsToLhs = new HashMap<LogicalVariable, LogicalVariable>();

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
        GroupByOperator gby = (GroupByOperator) op;
        boolean gvChanged = factorRedundantRhsVars(gby.getGroupByList(), opRef);
        boolean dvChanged = factorRedundantRhsVars(gby.getDecorList(), opRef);

        return gvChanged || dvChanged;
    }

    private boolean factorRedundantRhsVars(List<Pair<LogicalVariable, LogicalExpressionReference>> veList,
            LogicalOperatorReference opRef) {
        decorVarRhsToLhs.clear();
        ListIterator<Pair<LogicalVariable, LogicalExpressionReference>> iter = veList.listIterator();
        boolean changed = false;
        while (iter.hasNext()) {
            Pair<LogicalVariable, LogicalExpressionReference> p = iter.next();
            ILogicalExpression expr = p.second.getExpression();
            if (expr.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
                continue;
            }
            VariableReferenceExpression varRef = (VariableReferenceExpression) expr;
            LogicalVariable v = varRef.getVariableReference();
            LogicalVariable lhs = decorVarRhsToLhs.get(v);
            if (lhs != null) {
                AssignOperator assign = new AssignOperator(p.first, new LogicalExpressionReference(
                        new VariableReferenceExpression(lhs)));
                ILogicalOperator op = opRef.getOperator();
                assign.getInputs().add(new LogicalOperatorReference(op));
                opRef.setOperator(assign);
                iter.remove();
                changed = true;
            } else {
                decorVarRhsToLhs.put(v, p.first);
            }
        }
        return changed;
    }

}

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

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AssignOperator;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;

public abstract class AbstractExtractExprRule implements IAlgebraicRewriteRule {

    protected LogicalVariable extractExprIntoAssignOpRef(ILogicalExpression gExpr, LogicalOperatorReference opRef2,
            IOptimizationContext context) throws AlgebricksException {
        LogicalVariable v = context.newVar();
        AssignOperator a = new AssignOperator(v, new LogicalExpressionReference(gExpr));
        a.getInputs().add(new LogicalOperatorReference(opRef2.getOperator()));
        opRef2.setOperator(a);
        if (gExpr.getExpressionTag() == LogicalExpressionTag.CONSTANT) {
            context.addNotToBeInlinedVar(v);
        }
        context.computeAndSetTypeEnvironmentForOperator(a);
        return v;
    }

}

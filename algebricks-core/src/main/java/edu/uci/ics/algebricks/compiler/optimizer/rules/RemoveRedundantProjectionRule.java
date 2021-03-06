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

import java.util.ArrayList;
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ProjectOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;

/*
 *  project [var-list1]
 *   project [var-list2]
 *     P
 * 
 *  if var-list1.equals(var-list2) becomes
 * 
 *  project [var-list1]
 *    P
 *  
 */

public class RemoveRedundantProjectionRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) {
        return false;
    }

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op1 = (AbstractLogicalOperator) opRef.getOperator();
        if (op1.getOperatorTag() == LogicalOperatorTag.PROJECT) {
            LogicalOperatorReference opRef2 = op1.getInputs().get(0);
            AbstractLogicalOperator op2 = (AbstractLogicalOperator) opRef2.getOperator();
            if (op2.getOperatorTag() != LogicalOperatorTag.PROJECT) {
                return false;
            }
            ProjectOperator pi2 = (ProjectOperator) op2;
            opRef2.setOperator(pi2.getInputs().get(0).getOperator());
        } else {
            if (op1.getInputs().size() <= 0)
                return false;
            LogicalOperatorReference opRef2 = op1.getInputs().get(0);
            AbstractLogicalOperator op2 = (AbstractLogicalOperator) opRef2.getOperator();
            if (op2.getOperatorTag() != LogicalOperatorTag.PROJECT) {
                return false;
            }
            if (op2.getInputs().size() <= 0)
                return false;
            LogicalOperatorReference opRef3 = op2.getInputs().get(0);
            AbstractLogicalOperator op3 = (AbstractLogicalOperator) opRef3.getOperator();

            List<LogicalVariable> liveVars2 = new ArrayList<LogicalVariable>();
            List<LogicalVariable> liveVars3 = new ArrayList<LogicalVariable>();

            VariableUtilities.getLiveVariables(op2, liveVars2);
            VariableUtilities.getLiveVariables(op3, liveVars3);

            if (!VariableUtilities.varListEqualUnordered(liveVars2, liveVars3))
                return false;
            opRef2.setOperator(op3);
        }

        return true;
    }

}

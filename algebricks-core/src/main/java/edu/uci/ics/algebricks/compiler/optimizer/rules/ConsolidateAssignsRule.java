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
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AssignOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;

public class ConsolidateAssignsRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) {
        return false;
    }

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        if (op.getOperatorTag() != LogicalOperatorTag.ASSIGN) {
            return false;
        }
        AssignOperator assign1 = (AssignOperator) op;

        AbstractLogicalOperator op2 = (AbstractLogicalOperator) assign1.getInputs().get(0).getOperator();
        if (op2.getOperatorTag() != LogicalOperatorTag.ASSIGN) {
            return false;
        }

        AssignOperator assign2 = (AssignOperator) op2;

        HashSet<LogicalVariable> used1 = new HashSet<LogicalVariable>();
        VariableUtilities.getUsedVariables(assign1, used1);
        for (LogicalVariable v2 : assign2.getVariables()) {
            if (used1.contains(v2)) {
                return false;
            }
        }

        assign1.getVariables().addAll(assign2.getVariables());
        assign1.getExpressions().addAll(assign2.getExpressions());

        LogicalOperatorReference botOpRef = assign2.getInputs().get(0);
        List<LogicalOperatorReference> asgnInpList = assign1.getInputs();
        asgnInpList.clear();
        asgnInpList.add(botOpRef);
        context.computeAndSetTypeEnvironmentForOperator(assign1);
        return true;
    }

}

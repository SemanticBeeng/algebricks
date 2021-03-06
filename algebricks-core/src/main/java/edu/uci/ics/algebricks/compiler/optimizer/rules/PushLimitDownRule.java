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

import java.util.LinkedList;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.expressions.ScalarFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.functions.AlgebricksBuiltinFunctions;
import edu.uci.ics.algebricks.compiler.algebra.functions.IFunctionInfo;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.LimitOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.StreamLimitPOperator;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.compiler.optimizer.base.OptimizationUtil;

public class PushLimitDownRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) {
        return false;
    }

    /**
     * When a global Limit over a merge-exchange is found, a local Limit is
     * pushed down.
     * 
     */

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        if (op.getOperatorTag() != LogicalOperatorTag.LIMIT) {
            return false;
        }
        LimitOperator opLim = (LimitOperator) op;
        if (!opLim.isTopmostLimitOp()) {
            return false;
        }

        LogicalOperatorReference opRef2 = opLim.getInputs().get(0);
        AbstractLogicalOperator op2 = (AbstractLogicalOperator) opRef2.getOperator();

        if (context.checkAndAddToAlreadyCompared(op, op2)) {
            return false;
        }
        if (op2.getOperatorTag() != LogicalOperatorTag.EXCHANGE) {
            return false;
        }
        PhysicalOperatorTag op2PTag = op2.getPhysicalOperator().getOperatorTag();
        // we should test for any kind of merge
        if (op2PTag != PhysicalOperatorTag.RANDOM_MERGE_EXCHANGE && op2PTag != PhysicalOperatorTag.SORT_MERGE_EXCHANGE) {
            return false;
        }

        LinkedList<LogicalVariable> usedVars1 = new LinkedList<LogicalVariable>();
        VariableUtilities.getUsedVariables(opLim, usedVars1);

        do {
            if (op2.getOperatorTag() == LogicalOperatorTag.EMPTYTUPLESOURCE
                    || op2.getOperatorTag() == LogicalOperatorTag.NESTEDTUPLESOURCE
                    || op2.getOperatorTag() == LogicalOperatorTag.LIMIT) {
                return false;
            }
            if (op2.getInputs().size() > 1 || !op2.isMap()) {
                break;
            }
            LinkedList<LogicalVariable> vars2 = new LinkedList<LogicalVariable>();
            VariableUtilities.getProducedVariables(op2, vars2);
            if (!OptimizationUtil.disjoint(vars2, usedVars1)) {
                return false;
            }
            // we assume pipelineable ops. have only one input
            opRef2 = op2.getInputs().get(0);
            op2 = (AbstractLogicalOperator) opRef2.getOperator();
        } while (true);

        LimitOperator clone2 = null;
        if (opLim.getOffset().getExpression() == null) {
            clone2 = new LimitOperator(opLim.getMaxObjects().getExpression(), false);
        } else {
            // push limit (max+offset)
            IFunctionInfo finfoAdd = AlgebricksBuiltinFunctions
                    .getBuiltinFunctionInfo(AlgebricksBuiltinFunctions.NUMERIC_ADD);
            ScalarFunctionCallExpression maxPlusOffset = new ScalarFunctionCallExpression(finfoAdd, opLim
                    .getMaxObjects(), opLim.getOffset());
            clone2 = new LimitOperator(maxPlusOffset, false);
        }
        clone2.setPhysicalOperator(new StreamLimitPOperator(false));
        clone2.getInputs().add(new LogicalOperatorReference(op2));
        clone2.setExecutionMode(op2.getExecutionMode());
        clone2.recomputeSchema();
        opRef2.setOperator(clone2);
        context.computeAndSetTypeEnvironmentForOperator(clone2);
        return true;
    }

}

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
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.ScalarFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.functions.AlgebricksBuiltinFunctions;
import edu.uci.ics.algebricks.compiler.algebra.functions.FunctionIdentifier;
import edu.uci.ics.algebricks.compiler.algebra.functions.IFunctionInfo;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractBinaryJoinOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.SelectOperator;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;

public class PullSelectOutOfEqJoin implements IAlgebraicRewriteRule {

    private List<LogicalExpressionReference> eqVarVarComps = new ArrayList<LogicalExpressionReference>();
    private List<LogicalExpressionReference> otherPredicates = new ArrayList<LogicalExpressionReference>();

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();

        if (op.getOperatorTag() != LogicalOperatorTag.INNERJOIN
                && op.getOperatorTag() != LogicalOperatorTag.LEFTOUTERJOIN) {
            return false;
        }
        AbstractBinaryJoinOperator join = (AbstractBinaryJoinOperator) op;

        ILogicalExpression expr = join.getCondition().getExpression();
        if (expr.getExpressionTag() != LogicalExpressionTag.FUNCTION_CALL) {
            return false;
        }
        AbstractFunctionCallExpression fexp = (AbstractFunctionCallExpression) expr;
        FunctionIdentifier fi = fexp.getFunctionIdentifier();
        if (fi != AlgebricksBuiltinFunctions.AND) {
            return false;
        }
        eqVarVarComps.clear();
        otherPredicates.clear();
        for (LogicalExpressionReference arg : fexp.getArguments()) {
            if (isEqVarVar(arg.getExpression())) {
                eqVarVarComps.add(arg);
            } else {
                otherPredicates.add(arg);
            }
        }
        if (eqVarVarComps.isEmpty() || otherPredicates.isEmpty()) {
            return false;
        }
        // pull up
        ILogicalExpression pulledCond = makeCondition(otherPredicates);
        SelectOperator select = new SelectOperator(new LogicalExpressionReference(pulledCond));
        ILogicalExpression newJoinCond = makeCondition(eqVarVarComps);
        join.getCondition().setExpression(newJoinCond);
        select.getInputs().add(new LogicalOperatorReference(join));
        opRef.setOperator(select);
        context.computeAndSetTypeEnvironmentForOperator(select);
        return true;
    }

    private ILogicalExpression makeCondition(List<LogicalExpressionReference> predList) {
        if (predList.size() > 1) {
            IFunctionInfo finfo = AlgebricksBuiltinFunctions.getBuiltinFunctionInfo(AlgebricksBuiltinFunctions.AND);
            return new ScalarFunctionCallExpression(finfo, predList);
        } else {
            return predList.get(0).getExpression();
        }
    }

    private boolean isEqVarVar(ILogicalExpression expr) {
        if (expr.getExpressionTag() != LogicalExpressionTag.FUNCTION_CALL) {
            return false;
        }
        AbstractFunctionCallExpression f = (AbstractFunctionCallExpression) expr;
        if (f.getFunctionIdentifier() != AlgebricksBuiltinFunctions.EQ) {
            return false;
        }
        ILogicalExpression e1 = f.getArguments().get(0).getExpression();
        if (e1.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
            return false;
        } else {
            ILogicalExpression e2 = f.getArguments().get(1).getExpression();
            return e2.getExpressionTag() == LogicalExpressionTag.VARIABLE;
        }
    }

}

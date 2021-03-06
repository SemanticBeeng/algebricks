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
package edu.uci.ics.algebricks.compiler.algebra.operators.logical;

import java.util.ArrayList;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.IVariableTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.functions.AlgebricksBuiltinFunctions;
import edu.uci.ics.algebricks.compiler.algebra.properties.TypePropagationPolicy;
import edu.uci.ics.algebricks.compiler.algebra.properties.VariablePropagationPolicy;
import edu.uci.ics.algebricks.compiler.algebra.typing.ITypeEnvPointer;
import edu.uci.ics.algebricks.compiler.algebra.typing.ITypingContext;
import edu.uci.ics.algebricks.compiler.algebra.typing.OpRefTypeEnvPointer;
import edu.uci.ics.algebricks.compiler.algebra.typing.PropagatingTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalExpressionReferenceTransform;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalOperatorVisitor;

public class SelectOperator extends AbstractLogicalOperator {
    private final LogicalExpressionReference condition;

    public SelectOperator(LogicalExpressionReference condition) {
        this.condition = condition;
    }

    @Override
    public LogicalOperatorTag getOperatorTag() {
        return LogicalOperatorTag.SELECT;
    }

    public LogicalExpressionReference getCondition() {
        return condition;
    }

    @Override
    public void recomputeSchema() {
        schema = new ArrayList<LogicalVariable>(inputs.get(0).getOperator().getSchema());
    }

    @Override
    public VariablePropagationPolicy getVariablePropagationPolicy() {
        return VariablePropagationPolicy.ALL;
    }

    @Override
    public boolean acceptExpressionTransform(ILogicalExpressionReferenceTransform visitor) throws AlgebricksException {
        return visitor.transform(condition);
    }

    @Override
    public <R, T> R accept(ILogicalOperatorVisitor<R, T> visitor, T arg) throws AlgebricksException {
        return visitor.visitSelectOperator(this, arg);
    }

    @Override
    public boolean isMap() {
        return true;
    }

    @Override
    public IVariableTypeEnvironment computeOutputTypeEnvironment(ITypingContext ctx) throws AlgebricksException {
        ITypeEnvPointer[] envPointers = new ITypeEnvPointer[1];
        envPointers[0] = new OpRefTypeEnvPointer(inputs.get(0), ctx);
        PropagatingTypeEnvironment env = new PropagatingTypeEnvironment(ctx.getExpressionTypeComputer(), ctx
                .getNullableTypeComputer(), ctx.getMetadataProvider(), TypePropagationPolicy.ALL, envPointers);
        if (condition.getExpression().getExpressionTag() == LogicalExpressionTag.FUNCTION_CALL) {
            AbstractFunctionCallExpression f1 = (AbstractFunctionCallExpression) condition.getExpression();
            if (f1.getFunctionIdentifier() == AlgebricksBuiltinFunctions.NOT) {
                ILogicalExpression a1 = f1.getArguments().get(0).getExpression();
                if (a1.getExpressionTag() == LogicalExpressionTag.FUNCTION_CALL) {
                    AbstractFunctionCallExpression f2 = (AbstractFunctionCallExpression) a1;
                    if (f2.getFunctionIdentifier() == AlgebricksBuiltinFunctions.IS_NULL) {
                        ILogicalExpression a2 = f2.getArguments().get(0).getExpression();
                        if (a2.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                            LogicalVariable var = ((VariableReferenceExpression) a2).getVariableReference();
                            env.getNonNullVariables().add(var);
                        }
                    }
                }
            }
        }
        return env;
    }

}
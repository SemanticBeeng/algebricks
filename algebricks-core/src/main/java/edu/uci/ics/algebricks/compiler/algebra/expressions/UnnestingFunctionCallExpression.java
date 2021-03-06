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
package edu.uci.ics.algebricks.compiler.algebra.expressions;

import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.functions.IFunctionInfo;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalExpressionVisitor;

public class UnnestingFunctionCallExpression extends AbstractFunctionCallExpression {

    private boolean returnsUniqueValues;

    public UnnestingFunctionCallExpression(IFunctionInfo finfo) {
        super(FunctionKind.UNNEST, finfo);
    }

    public UnnestingFunctionCallExpression(IFunctionInfo finfo, List<LogicalExpressionReference> arguments) {
        super(FunctionKind.UNNEST, finfo, arguments);
    }

    public UnnestingFunctionCallExpression(IFunctionInfo finfo, LogicalExpressionReference... expressions) {
        super(FunctionKind.UNNEST, finfo, expressions);
    }

    @Override
    public UnnestingFunctionCallExpression cloneExpression() {
        cloneAnnotations();
        List<LogicalExpressionReference> clonedArgs = cloneArguments();
        UnnestingFunctionCallExpression ufce = new UnnestingFunctionCallExpression(finfo, clonedArgs);
        ufce.setReturnsUniqueValues(returnsUniqueValues);
        return ufce;
    }

    @Override
    public <R, T> R accept(ILogicalExpressionVisitor<R, T> visitor, T arg) throws AlgebricksException {
        return visitor.visitUnnestingFunctionCallExpression(this, arg);
    }

    public void setReturnsUniqueValues(boolean returnsUniqueValues) {
        this.returnsUniqueValues = returnsUniqueValues;
    }

    public boolean returnsUniqueValues() {
        return returnsUniqueValues;
    }

}

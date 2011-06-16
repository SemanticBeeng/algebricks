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
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalExpressionReferenceTransform;

/**
 * @author Nicola
 * 
 */
public abstract class AbstractAssignOperator extends AbstractLogicalOperator {
    protected final ArrayList<LogicalVariable> variables;
    protected final ArrayList<LogicalExpressionReference> expressions;

    public AbstractAssignOperator() {
        this.variables = new ArrayList<LogicalVariable>();
        this.expressions = new ArrayList<LogicalExpressionReference>();
    }

    public AbstractAssignOperator(ArrayList<LogicalVariable> variables,
            ArrayList<LogicalExpressionReference> expressions) {
        this.variables = variables;
        this.expressions = expressions;
    }

    public ArrayList<LogicalVariable> getVariables() {
        return variables;
    }

    public ArrayList<LogicalExpressionReference> getExpressions() {
        return expressions;
    }

    @Override
    public void recomputeSchema() {
        schema = new ArrayList<LogicalVariable>();
        schema.addAll(inputs.get(0).getOperator().getSchema());
        schema.addAll(variables);
    }

    @Override
    public boolean acceptExpressionTransform(ILogicalExpressionReferenceTransform visitor) throws AlgebricksException {
        boolean modif = false;
        for (int i = 0; i < expressions.size(); i++) {
            if (visitor.transform(expressions.get(i))) {
                modif = true;
            }
        }
        return modif;
    }

}

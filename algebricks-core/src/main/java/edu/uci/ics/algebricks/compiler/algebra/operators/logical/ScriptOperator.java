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
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.properties.VariablePropagationPolicy;
import edu.uci.ics.algebricks.compiler.algebra.scripting.IScriptDescription;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalExpressionReferenceTransform;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalOperatorVisitor;

public class ScriptOperator extends AbstractLogicalOperator {

    private ArrayList<LogicalVariable> inputVariables;
    private ArrayList<LogicalVariable> outputVariables;
    private IScriptDescription scriptDesc;

    public ScriptOperator(IScriptDescription scriptDesc, ArrayList<LogicalVariable> inputVariables,
            ArrayList<LogicalVariable> outputVariables) {
        this.inputVariables = inputVariables;
        this.outputVariables = outputVariables;
        this.scriptDesc = scriptDesc;
    }

    public ArrayList<LogicalVariable> getInputVariables() {
        return inputVariables;
    }

    public ArrayList<LogicalVariable> getOutputVariables() {
        return outputVariables;
    }

    public IScriptDescription getScriptDescription() {
        return scriptDesc;
    }

    @Override
    public LogicalOperatorTag getOperatorTag() {
        return LogicalOperatorTag.SCRIPT;
    }

    @Override
    public <R, T> R accept(ILogicalOperatorVisitor<R, T> visitor, T arg) throws AlgebricksException {
        return visitor.visitScriptOperator(this, arg);
    }

    @Override
    public boolean acceptExpressionTransform(ILogicalExpressionReferenceTransform visitor) throws AlgebricksException {
        return false;
    }

    @Override
    public VariablePropagationPolicy getVariablePropagationPolicy() {
        return new VariablePropagationPolicy() {

            @Override
            public void propagateVariables(IOperatorSchema target, IOperatorSchema... sources) throws AlgebricksException {
                for (LogicalVariable v : outputVariables) {
                    target.addVariable(v);
                }
            }
        };

    }

    @Override
    public boolean isMap() {
        return false;
    }

    @Override
    public void recomputeSchema() {
        this.schema = outputVariables;
    }

}

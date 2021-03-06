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
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ProjectOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.UnionAllOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.StreamProjectPOperator;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.utils.Triple;

public class InsertProjectBeforeUnionRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    /**
     * When the input schema to WriteOperator is different from the output
     * schema in terms of variable order, add a project operator to get the
     * write order
     */
    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        if (op.getOperatorTag() != LogicalOperatorTag.UNIONALL) {
            return false;
        }
        UnionAllOperator opUnion = (UnionAllOperator) op;
        List<Triple<LogicalVariable, LogicalVariable, LogicalVariable>> varMap = opUnion.getVariableMappings();
        ArrayList<LogicalVariable> usedVariablesFromOne = new ArrayList<LogicalVariable>();
        ArrayList<LogicalVariable> usedVariablesFromTwo = new ArrayList<LogicalVariable>();

        for (Triple<LogicalVariable, LogicalVariable, LogicalVariable> triple : varMap) {
            usedVariablesFromOne.add(triple.first);
            usedVariablesFromTwo.add(triple.second);
        }

        ArrayList<LogicalVariable> inputSchemaOne = new ArrayList<LogicalVariable>();
        VariableUtilities.getLiveVariables(opUnion.getInputs().get(0).getOperator(), inputSchemaOne);

        ArrayList<LogicalVariable> inputSchemaTwo = new ArrayList<LogicalVariable>();
        VariableUtilities.getLiveVariables(opUnion.getInputs().get(1).getOperator(), inputSchemaTwo);

        boolean rewritten = false;
        if (!isIdentical(usedVariablesFromOne, inputSchemaOne)) {
            insertProjectOperator(opUnion, 0, usedVariablesFromOne, context);
            rewritten = true;
        }
        if (!isIdentical(usedVariablesFromTwo, inputSchemaTwo)) {
            insertProjectOperator(opUnion, 1, usedVariablesFromTwo, context);
            rewritten = true;
        }
        return rewritten;
    }

    private void insertProjectOperator(UnionAllOperator opUnion, int branch, ArrayList<LogicalVariable> usedVariables,
            IOptimizationContext context) throws AlgebricksException {
        ProjectOperator projectOp = new ProjectOperator(usedVariables);
        ILogicalOperator parentOp = opUnion.getInputs().get(branch).getOperator();
        projectOp.getInputs().add(new LogicalOperatorReference(parentOp));
        opUnion.getInputs().get(branch).setOperator(projectOp);
        projectOp.setPhysicalOperator(new StreamProjectPOperator());
        context.computeAndSetTypeEnvironmentForOperator(parentOp);
    }

    private boolean isIdentical(List<LogicalVariable> finalSchema, List<LogicalVariable> inputSchema)
            throws AlgebricksException {
        int finalSchemaSize = finalSchema.size();
        int inputSchemaSize = inputSchema.size();
        if (finalSchemaSize != inputSchemaSize) {
            return false;
        }
        for (int i = 0; i < finalSchemaSize; i++) {
            LogicalVariable var1 = finalSchema.get(i);
            LogicalVariable var2 = inputSchema.get(i);
            if (!var1.equals(var2))
                return false;
        }
        return true;
    }

}

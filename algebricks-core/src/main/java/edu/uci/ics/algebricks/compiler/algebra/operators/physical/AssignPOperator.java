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
package edu.uci.ics.algebricks.compiler.algebra.operators.physical;

import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.ILogicalExpressionJobGen;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AssignOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPhysicalPropertiesVector;
import edu.uci.ics.algebricks.compiler.algebra.properties.PhysicalRequirements;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.runtime.hyracks.base.IEvaluatorFactory;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.base.IHyracksJobBuilder;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenContext;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenHelper;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.AssignRuntimeFactory;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;

public class AssignPOperator extends AbstractPhysicalOperator {

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.ASSIGN;
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator op, IOptimizationContext context) {
        ILogicalOperator op2 = op.getInputs().get(0).getOperator();
        deliveredProperties = op2.getDeliveredPhysicalProperties().clone();
    }

    @Override
    public PhysicalRequirements getRequiredPropertiesForChildren(ILogicalOperator op,
            IPhysicalPropertiesVector reqdByParent) {
        return emptyUnaryRequirements();
    }

    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema opSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
            throws AlgebricksException {
        AssignOperator assign = (AssignOperator) op;
        List<LogicalVariable> variables = assign.getVariables();
        List<LogicalExpressionReference> expressions = assign.getExpressions();
        int[] outColumns = new int[variables.size()];
        for (int i = 0; i < outColumns.length; i++) {
            outColumns[i] = opSchema.findVariable(variables.get(i));
        }
        IEvaluatorFactory[] evalFactories = new IEvaluatorFactory[expressions.size()];
        ILogicalExpressionJobGen exprJobGen = context.getExpressionJobGen();
        for (int i = 0; i < evalFactories.length; i++) {
            evalFactories[i] = exprJobGen.createEvaluatorFactory(expressions.get(i).getExpression(), context
                    .getTypeEnvironment(op.getInputs().get(0).getOperator()), inputSchemas, context);
        }

        // TODO push projections into the operator
        int[] projectionList = JobGenHelper.projectAllVariables(opSchema);

        AssignRuntimeFactory runtime = new AssignRuntimeFactory(outColumns, evalFactories, projectionList);

        // contribute one Asterix framewriter
        RecordDescriptor recDesc = JobGenHelper.mkRecordDescriptor(op, opSchema, context);
        builder.contributeMicroOperator(assign, runtime, recDesc);
        // and contribute one edge from its child
        ILogicalOperator src = assign.getInputs().get(0).getOperator();
        builder.contributeGraphEdge(src, 0, assign, 0);

    }

    @Override
    public boolean isMicroOperator() {
        return true;
    }

}

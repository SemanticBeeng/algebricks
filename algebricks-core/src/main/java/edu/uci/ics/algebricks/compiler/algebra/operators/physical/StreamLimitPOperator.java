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

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.ILogicalExpressionJobGen;
import edu.uci.ics.algebricks.api.expr.IVariableTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.LimitOperator;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPartitioningProperty;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPartitioningRequirementsCoordinator;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPhysicalPropertiesVector;
import edu.uci.ics.algebricks.compiler.algebra.properties.PhysicalRequirements;
import edu.uci.ics.algebricks.compiler.algebra.properties.StructuralPropertiesVector;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.runtime.hyracks.base.IEvaluatorFactory;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.base.IHyracksJobBuilder;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenContext;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenHelper;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.StreamLimitRuntimeFactory;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;

public class StreamLimitPOperator extends AbstractPhysicalOperator {

    private boolean global;

    public StreamLimitPOperator(boolean global) {
        this.global = global;
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.STREAM_LIMIT;
    }

    @Override
    public boolean isMicroOperator() {
        return true;
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator op, IOptimizationContext context) {
        ILogicalOperator op2 = op.getInputs().get(0).getOperator();
        deliveredProperties = op2.getDeliveredPhysicalProperties().clone();
    }

    @Override
    public PhysicalRequirements getRequiredPropertiesForChildren(ILogicalOperator op,
            IPhysicalPropertiesVector reqdByParent) {
        if (global) {
            StructuralPropertiesVector[] pv = new StructuralPropertiesVector[1];
            pv[0] = new StructuralPropertiesVector(IPartitioningProperty.UNPARTITIONED, null);
            return new PhysicalRequirements(pv, IPartitioningRequirementsCoordinator.NO_COORDINATION);
        } else {
            return emptyUnaryRequirements();
        }
    }

    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema propagatedSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
            throws AlgebricksException {
        LimitOperator limit = (LimitOperator) op;
        ILogicalExpressionJobGen exprJobGen = context.getExpressionJobGen();
        IVariableTypeEnvironment env = context.getTypeEnvironment(op);
        IEvaluatorFactory maxObjectsFact = exprJobGen.createEvaluatorFactory(limit.getMaxObjects().getExpression(),
                env, inputSchemas, context);
        ILogicalExpression offsetExpr = limit.getOffset().getExpression();
        IEvaluatorFactory offsetFact = (offsetExpr == null) ? null : exprJobGen.createEvaluatorFactory(offsetExpr, env,
                inputSchemas, context);
        RecordDescriptor recDesc = JobGenHelper.mkRecordDescriptor(op, propagatedSchema, context);
        StreamLimitRuntimeFactory runtime = new StreamLimitRuntimeFactory(maxObjectsFact, offsetFact, null, context
                .getBinaryIntegerInspector());
        builder.contributeMicroOperator(limit, runtime, recDesc);
        // and contribute one edge from its child
        ILogicalOperator src = limit.getInputs().get(0).getOperator();
        builder.contributeGraphEdge(src, 0, limit, 0);
    }

}

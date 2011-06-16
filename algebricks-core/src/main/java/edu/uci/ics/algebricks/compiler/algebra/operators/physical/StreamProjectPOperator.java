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
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ProjectOperator;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPhysicalPropertiesVector;
import edu.uci.ics.algebricks.compiler.algebra.properties.PhysicalRequirements;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.base.IHyracksJobBuilder;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenContext;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenHelper;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.StreamProjectRuntimeFactory;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;

public class StreamProjectPOperator extends AbstractPropagatePropertiesForUsedVariablesPOperator {

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.STREAM_PROJECT;
    }

    @Override
    public PhysicalRequirements getRequiredPropertiesForChildren(ILogicalOperator op, IPhysicalPropertiesVector reqdByParent) {
        return emptyUnaryRequirements();
    }

    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema propagatedSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
            throws AlgebricksException {
        ProjectOperator project = (ProjectOperator) op;
        int[] projectionList = new int[project.getVariables().size()];
        int i = 0;
        for (LogicalVariable v : project.getVariables()) {
            int pos = inputSchemas[0].findVariable(v);
            if (pos < 0) {
                throw new AlgebricksException("Could not find variable " + v + ".");
            }
            projectionList[i++] = pos;
        }
        StreamProjectRuntimeFactory runtime = new StreamProjectRuntimeFactory(projectionList);
        RecordDescriptor recDesc = JobGenHelper.mkRecordDescriptor(propagatedSchema, context);
        builder.contributeMicroOperator(project, runtime, recDesc);
        ILogicalOperator src = project.getInputs().get(0).getOperator();
        builder.contributeGraphEdge(src, 0, project, 0);
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator op, IOptimizationContext context) {
        ProjectOperator p = (ProjectOperator) op;
        computeDeliveredPropertiesForUsedVariables(p, p.getVariables());
    }

}

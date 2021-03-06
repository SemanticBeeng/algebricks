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
package edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.base.IHyracksJobBuilder;
import edu.uci.ics.hyracks.api.job.JobSpecification;

public class PlanCompiler {
    private JobGenContext context;
    private Map<LogicalOperatorReference, List<LogicalOperatorReference>> operatorVisitedToParents = new HashMap<LogicalOperatorReference, List<LogicalOperatorReference>>();

    public PlanCompiler(JobGenContext context) {
        this.context = context;
    }

    public JobGenContext getContext() {
        return context;
    }

    public JobSpecification compilePlan(ILogicalPlan plan, IOperatorSchema outerPlanSchema) throws AlgebricksException {
        JobSpecification spec = new JobSpecification();
        List<ILogicalOperator> rootOps = new ArrayList<ILogicalOperator>();
        IHyracksJobBuilder builder = new JobBuilder(spec, context.getClusterLocations());
        for (LogicalOperatorReference opRef : plan.getRoots()) {
            compileOpRef(opRef, spec, builder, outerPlanSchema);
            rootOps.add(opRef.getOperator());
        }
        reviseEdges(builder);
        operatorVisitedToParents.clear();
        builder.buildSpec(rootOps);
        return spec;
    }

    private void compileOpRef(LogicalOperatorReference opRef, JobSpecification spec, IHyracksJobBuilder builder,
            IOperatorSchema outerPlanSchema) throws AlgebricksException {
        ILogicalOperator op = opRef.getOperator();
        int n = op.getInputs().size();
        IOperatorSchema[] schemas = new IOperatorSchema[n];
        int i = 0;
        for (LogicalOperatorReference opRef2 : op.getInputs()) {
            List<LogicalOperatorReference> parents = operatorVisitedToParents.get(opRef2);
            if (parents == null) {
                parents = new ArrayList<LogicalOperatorReference>();
                operatorVisitedToParents.put(opRef2, parents);
                parents.add(opRef);
                compileOpRef(opRef2, spec, builder, outerPlanSchema);
                schemas[i++] = context.getSchema(opRef2.getOperator());
            } else {
                if (!parents.contains(opRef))
                    parents.add(opRef);
                schemas[i++] = context.getSchema(opRef2.getOperator());
                continue;
            }
        }

        IOperatorSchema opSchema = new OperatorSchemaImpl();
        context.putSchema(op, opSchema);
        op.getVariablePropagationPolicy().propagateVariables(opSchema, schemas);
        op.contributeRuntimeOperator(builder, context, opSchema, schemas, outerPlanSchema);
    }

    private void reviseEdges(IHyracksJobBuilder builder) {
        /**
         * revise the edges for the case of replicate operator
         */
        for (Entry<LogicalOperatorReference, List<LogicalOperatorReference>> entry : operatorVisitedToParents
                .entrySet()) {
            LogicalOperatorReference child = entry.getKey();
            List<LogicalOperatorReference> parents = entry.getValue();
            if (parents.size() > 1) {
                int i = 0;
                for (LogicalOperatorReference parent : parents) {
                    builder.contributeGraphEdge(child.getOperator(), i, parent.getOperator(), 0);
                    i++;
                }
            }
        }
    }
}

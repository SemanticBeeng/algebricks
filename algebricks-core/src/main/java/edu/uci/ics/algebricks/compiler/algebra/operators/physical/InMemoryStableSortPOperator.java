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

import edu.uci.ics.algebricks.api.data.IBinaryComparatorFactoryProvider;
import edu.uci.ics.algebricks.api.data.INormalizedKeyComputerFactoryProvider;
import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.IVariableTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.OrderOperator.IOrder.OrderKind;
import edu.uci.ics.algebricks.compiler.algebra.properties.OrderColumn;
import edu.uci.ics.algebricks.runtime.hyracks.base.IPushRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.base.IHyracksJobBuilder;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenContext;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenHelper;
import edu.uci.ics.algebricks.runtime.hyracks.operators.sort.InMemorySortRuntimeFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.INormalizedKeyComputerFactory;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;

public class InMemoryStableSortPOperator extends AbstractStableSortPOperator {

    public InMemoryStableSortPOperator() {
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.IN_MEMORY_STABLE_SORT;
    }

    @Override
    public boolean isMicroOperator() {
        return true;
    }

    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema opSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
            throws AlgebricksException {
        RecordDescriptor recDescriptor = JobGenHelper.mkRecordDescriptor(op, opSchema, context);
        int n = sortColumns.length;
        int[] sortFields = new int[n];
        IBinaryComparatorFactory[] comps = new IBinaryComparatorFactory[n];
        int i = 0;
        INormalizedKeyComputerFactoryProvider nkcfProvider = context.getNormalizedKeyComputerFactoryProvider();
        INormalizedKeyComputerFactory nkcf = null;
        IVariableTypeEnvironment env = context.getTypeEnvironment(op);
        for (OrderColumn oc : sortColumns) {
            LogicalVariable var = oc.getColumn();
            sortFields[i] = opSchema.findVariable(var);
            Object type = env.getVarType(var);
            OrderKind order = oc.getOrder();
            if (i == 0 && nkcfProvider != null && type != null) {
                nkcf = nkcfProvider.getNormalizedKeyComputerFactory(type, order);
            }

            IBinaryComparatorFactoryProvider bcfp = context.getBinaryComparatorFactoryProvider();
            comps[i] = bcfp.getBinaryComparatorFactory(type, oc.getOrder());
            i++;
        }

        IPushRuntimeFactory runtime = new InMemorySortRuntimeFactory(sortFields, nkcf, comps, null);
        builder.contributeMicroOperator(op, runtime, recDescriptor);
        ILogicalOperator src = op.getInputs().get(0).getOperator();
        builder.contributeGraphEdge(src, 0, op, 0);
    }
}

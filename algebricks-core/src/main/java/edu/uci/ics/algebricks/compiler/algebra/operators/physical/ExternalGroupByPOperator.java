package edu.uci.ics.algebricks.compiler.algebra.operators.physical;

import java.util.ArrayList;
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.ILogicalExpressionJobGen;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.base.OperatorAnnotations;
import edu.uci.ics.algebricks.compiler.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AggregateFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AggregateOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.algebricks.compiler.optimizer.base.PhysicalOptimizationsUtil;
import edu.uci.ics.algebricks.runtime.hyracks.base.IAggregateFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.base.IHyracksJobBuilder;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenContext;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenHelper;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.OperatorSchemaImpl;
import edu.uci.ics.algebricks.runtime.hyracks.operators.aggreg.SimpleAggregatorDescriptorFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.aggreg.SimpleMergeDescriptorFactory;
import edu.uci.ics.algebricks.utils.Pair;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ITuplePartitionComputerFactory;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.common.data.partition.FieldHashPartitionComputerFactory;
import edu.uci.ics.hyracks.dataflow.std.aggregators.IAggregatorDescriptorFactory;
import edu.uci.ics.hyracks.dataflow.std.group.ExternalGroupOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.group.HashSpillableGroupingTableFactory;

public class ExternalGroupByPOperator extends HashGroupByPOperator {

    private int tableSize = 0;
    private boolean isLocal = true;

    public ExternalGroupByPOperator(List<Pair<LogicalVariable, LogicalExpressionReference>> gbyList, int tableSize,
            boolean isLocal) {
        super(gbyList, tableSize);
        this.tableSize = tableSize;
        this.isLocal = isLocal;
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.EXTERNAL_GROUP_BY;
    }

    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema opSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
            throws AlgebricksException {
        if (!isLocal && op.getAnnotations().get(OperatorAnnotations.LOCAL_GBY) != null)
            isLocal = (Boolean) op.getAnnotations().get(OperatorAnnotations.LOCAL_GBY);

        List<LogicalVariable> columnSet = getGbyColumns();
        int keys[] = JobGenHelper.variablesToFieldIndexes(columnSet, inputSchemas[0]);
        GroupByOperator gby = (GroupByOperator) op;
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gby.getGroupByList()) {
            ILogicalExpression expr = p.second.getExpression();
            if (p.first != null) {
                context.setVarType(p.first, context.getType(expr));
            }
        }

        int numFds = gby.getDecorList().size();
        int fdColumns[] = new int[numFds];
        int j = 0;
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gby.getDecorList()) {
            ILogicalExpression expr = p.second.getExpression();
            if (expr.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
                throw new AlgebricksException("pre-sorted group-by expects variable references.");
            }
            if (p.first != null) {
                context.setVarType(p.first, context.getType(p.second.getExpression()));
            }
            VariableReferenceExpression v = (VariableReferenceExpression) expr;
            LogicalVariable decor = v.getVariableReference();
            fdColumns[j++] = inputSchemas[0].findVariable(decor);
        }

        if (gby.getNestedPlans().size() != 1) {
            throw new AlgebricksException(
                    "External group-by currently works only for one nested plan with one root containing"
                            + "an aggregate and a nested-tuple-source.");
        }
        ILogicalPlan p0 = gby.getNestedPlans().get(0);
        if (p0.getRoots().size() != 1) {
            throw new AlgebricksException(
                    "External group-by currently works only for one nested plan with one root containing"
                            + "an aggregate and a nested-tuple-source.");
        }
        LogicalOperatorReference r0 = p0.getRoots().get(0);
        AggregateOperator aggOp = (AggregateOperator) r0.getOperator();

        int n = aggOp.getExpressions().size();
        IAggregateFunctionFactory[] aff = new IAggregateFunctionFactory[n];
        int i = 0;
        ILogicalExpressionJobGen exprJobGen = context.getExpressionJobGen();
        for (LogicalExpressionReference exprRef : aggOp.getExpressions()) {
            AggregateFunctionCallExpression aggFun = (AggregateFunctionCallExpression) exprRef.getExpression();
            aff[i++] = exprJobGen.createAggregateFunctionFactory(aggFun, inputSchemas, context);
        }

        int[] keyAndDecFields = new int[keys.length + fdColumns.length];
        for (i = 0; i < keys.length; ++i) {
            keyAndDecFields[i] = keys[i];
        }
        for (i = 0; i < fdColumns.length; i++) {
            keyAndDecFields[keys.length + i] = fdColumns[i];
        }

        List<LogicalVariable> keyAndDecVariables = new ArrayList<LogicalVariable>();
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gby.getGroupByList())
            keyAndDecVariables.add(p.first);
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gby.getDecorList())
            keyAndDecVariables.add(p.first);

        compileSubplans(inputSchemas[0], gby, opSchema, context);
        JobSpecification spec = builder.getJobSpec();
        IBinaryComparatorFactory[] comparatorFactories = JobGenHelper.variablesToAscBinaryComparatorFactories(
                columnSet, context);
        RecordDescriptor recordDescriptor = JobGenHelper.mkRecordDescriptor(opSchema, context);
        IBinaryHashFunctionFactory[] hashFunctionFactories = JobGenHelper.variablesToBinaryHashFunctionFactories(
                columnSet, context);

        IAggregateFunctionFactory[] merges = new IAggregateFunctionFactory[n];
        List<LogicalVariable> usedVars = new ArrayList<LogicalVariable>();
        IOperatorSchema[] localInputSchemas = new IOperatorSchema[1];
        localInputSchemas[0] = new OperatorSchemaImpl();
        for (i = 0; i < n; i++) {
            AggregateFunctionCallExpression aggFun = (AggregateFunctionCallExpression) aggOp.getMergeExpressions()
                    .get(i).getExpression();
            aggFun.getUsedVariables(usedVars);
        }
        for (LogicalVariable keyVar : keyAndDecVariables)
            localInputSchemas[0].addVariable(keyVar);
        for (LogicalVariable usedVar : usedVars)
            localInputSchemas[0].addVariable(usedVar);
        Object typeAnnotation = op.getAnnotations().get(OperatorAnnotations.EXTERNAL_GROUP_BY_INTERMEDIATE_INPUT_TYPES);
        i = 0;
        if (typeAnnotation instanceof List) {
            List<?> types = (List<?>) typeAnnotation;
            for (Object type : types)
                context.setVarType(usedVars.get(i++), type);
        }

        for (i = 0; i < n; i++) {
            AggregateFunctionCallExpression aggFun = (AggregateFunctionCallExpression) aggOp.getMergeExpressions()
                    .get(i).getExpression();
            merges[i] = exprJobGen.createAggregateFunctionFactory(aggFun, localInputSchemas, context);
        }
        IAggregatorDescriptorFactory aggregatorFactory = new SimpleAggregatorDescriptorFactory(aff);
        IAggregatorDescriptorFactory mergeFactory = new SimpleMergeDescriptorFactory(merges);

        ITuplePartitionComputerFactory tpcf = new FieldHashPartitionComputerFactory(keys, hashFunctionFactories);
        ExternalGroupOperatorDescriptor gbyOpDesc = new ExternalGroupOperatorDescriptor(spec, keyAndDecFields,
                PhysicalOptimizationsUtil.MAX_FRAMES_EXTERNAL_GROUP_BY, comparatorFactories, aggregatorFactory,
                mergeFactory, recordDescriptor, new HashSpillableGroupingTableFactory(tpcf, tableSize), false);

        contributeOpDesc(builder, gby, gbyOpDesc);
        ILogicalOperator src = op.getInputs().get(0).getOperator();
        builder.contributeGraphEdge(src, 0, op, 0);
    }
}
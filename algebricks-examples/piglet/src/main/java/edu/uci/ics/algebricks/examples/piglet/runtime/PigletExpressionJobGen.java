package edu.uci.ics.algebricks.examples.piglet.runtime;

import java.io.DataOutput;
import java.util.Arrays;
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.ILogicalExpressionJobGen;
import edu.uci.ics.algebricks.api.expr.IVariableTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AggregateFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.ConstantExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.ScalarFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.StatefulFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.UnnestingFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.algebricks.examples.piglet.compiler.ConstantValue;
import edu.uci.ics.algebricks.examples.piglet.exceptions.PigletException;
import edu.uci.ics.algebricks.examples.piglet.runtime.functions.PigletFunctionRegistry;
import edu.uci.ics.algebricks.examples.piglet.types.Type;
import edu.uci.ics.algebricks.runtime.hyracks.base.IAggregateFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.base.IEvaluatorFactory;
import edu.uci.ics.algebricks.runtime.hyracks.base.IRunningAggregateFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.base.ISerializableAggregateFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.base.IUnnestingFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.evaluators.ColumnAccessEvalFactory;
import edu.uci.ics.algebricks.runtime.hyracks.evaluators.ConstantEvalFactory;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.impl.JobGenContext;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;

public class PigletExpressionJobGen implements ILogicalExpressionJobGen {
    @Override
    public IEvaluatorFactory createEvaluatorFactory(ILogicalExpression expr, IVariableTypeEnvironment env,
            IOperatorSchema[] inputSchemas, JobGenContext context) throws AlgebricksException {
        switch (expr.getExpressionTag()) {
            case CONSTANT: {
                ConstantValue cv = (ConstantValue) ((ConstantExpression) expr).getValue();
                Type type = cv.getType();
                String image = cv.getImage();
                ArrayBackedValueStorage abvs = new ArrayBackedValueStorage();
                DataOutput dos = abvs.getDataOutput();
                switch (type.getTag()) {
                    case INTEGER:
                        try {
                            IntegerSerializerDeserializer.INSTANCE.serialize(Integer.valueOf(image), dos);
                        } catch (Exception e) {
                            throw new AlgebricksException(e);
                        }
                        break;

                    case CHAR_ARRAY:
                        try {
                            UTF8StringSerializerDeserializer.INSTANCE.serialize(image, dos);
                        } catch (Exception e) {
                            throw new AlgebricksException(e);
                        }
                        break;

                    default:
                        throw new UnsupportedOperationException("Unsupported constant type: " + type.getTag());
                }
                return new ConstantEvalFactory(Arrays.copyOf(abvs.getBytes(), abvs.getLength()));
            }

            case FUNCTION_CALL: {
                ScalarFunctionCallExpression sfce = (ScalarFunctionCallExpression) expr;

                List<LogicalExpressionReference> argExprs = sfce.getArguments();
                IEvaluatorFactory argEvalFactories[] = new IEvaluatorFactory[argExprs.size()];
                for (int i = 0; i < argEvalFactories.length; ++i) {
                    LogicalExpressionReference er = argExprs.get(i);
                    argEvalFactories[i] = createEvaluatorFactory(er.getExpression(), env, inputSchemas, context);
                }
                IEvaluatorFactory funcEvalFactory;
                try {
                    funcEvalFactory = PigletFunctionRegistry.createFunctionEvaluatorFactory(sfce
                            .getFunctionIdentifier(), argEvalFactories);
                } catch (PigletException e) {
                    throw new AlgebricksException(e);
                }
                return funcEvalFactory;
            }

            case VARIABLE: {
                LogicalVariable var = ((VariableReferenceExpression) expr).getVariableReference();
                int index = inputSchemas[0].findVariable(var);
                return new ColumnAccessEvalFactory(index);
            }
        }
        throw new IllegalArgumentException("Unknown expression type: " + expr.getExpressionTag());
    }

    @Override
    public IAggregateFunctionFactory createAggregateFunctionFactory(AggregateFunctionCallExpression expr,
            IVariableTypeEnvironment env, IOperatorSchema[] inputSchemas, JobGenContext context)
            throws AlgebricksException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ISerializableAggregateFunctionFactory createSerializableAggregateFunctionFactory(
            AggregateFunctionCallExpression expr, IVariableTypeEnvironment env, IOperatorSchema[] inputSchemas,
            JobGenContext context) throws AlgebricksException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IRunningAggregateFunctionFactory createRunningAggregateFunctionFactory(StatefulFunctionCallExpression expr,
            IVariableTypeEnvironment env, IOperatorSchema[] inputSchemas, JobGenContext context)
            throws AlgebricksException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IUnnestingFunctionFactory createUnnestingFunctionFactory(UnnestingFunctionCallExpression expr,
            IVariableTypeEnvironment env, IOperatorSchema[] inputSchemas, JobGenContext context)
            throws AlgebricksException {
        throw new UnsupportedOperationException();
    }
}
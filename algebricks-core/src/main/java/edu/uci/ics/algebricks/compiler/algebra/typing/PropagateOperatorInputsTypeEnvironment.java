package edu.uci.ics.algebricks.compiler.algebra.typing;

import java.util.ArrayList;
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.IExpressionTypeComputer;
import edu.uci.ics.algebricks.api.expr.IVariableTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.metadata.IMetadataProvider;

public class PropagateOperatorInputsTypeEnvironment extends AbstractTypeEnvironment {

    private final List<LogicalVariable> nonNullVariables = new ArrayList<LogicalVariable>();
    private final ILogicalOperator op;
    private final ITypingContext ctx;

    public PropagateOperatorInputsTypeEnvironment(ILogicalOperator op, ITypingContext ctx,
            IExpressionTypeComputer expressionTypeComputer, IMetadataProvider<?, ?> metadataProvider) {
        super(expressionTypeComputer, metadataProvider);
        this.op = op;
        this.ctx = ctx;
    }

    public List<LogicalVariable> getNonNullVariables() {
        return nonNullVariables;
    }

    @Override
    public Object getVarType(LogicalVariable var, List<LogicalVariable> nonNullVariableList) throws AlgebricksException {
        nonNullVariableList.addAll(nonNullVariables);
        return getVarTypeFullList(var, nonNullVariableList);
    }

    private Object getVarTypeFullList(LogicalVariable var, List<LogicalVariable> nonNullVariableList)
            throws AlgebricksException {
        Object t = varTypeMap.get(var);
        if (t != null) {
            return t;
        }
        for (LogicalOperatorReference r : op.getInputs()) {
            ILogicalOperator c = r.getOperator();
            IVariableTypeEnvironment env = ctx.getOutputTypeEnvironment(c);
            Object t2 = env.getVarType(var, nonNullVariableList);
            if (t2 != null) {
                return t2;
            }
        }
        return null;
    }

    @Override
    public Object getVarType(LogicalVariable var) throws AlgebricksException {
        return getVarTypeFullList(var, nonNullVariables);
    }

}

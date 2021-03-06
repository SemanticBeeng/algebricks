package edu.uci.ics.algebricks.compiler.optimizer.rules;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.DataSourceScanOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ProjectOperator;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;

public class PushProjectIntoDataSourceScanRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        if (op.getInputs().size() <= 0)
            return false;
        AbstractLogicalOperator project = (AbstractLogicalOperator) op.getInputs().get(0).getOperator();
        if (project.getOperatorTag() != LogicalOperatorTag.PROJECT)
            return false;
        AbstractLogicalOperator exchange = (AbstractLogicalOperator) project.getInputs().get(0).getOperator();
        if (exchange.getOperatorTag() != LogicalOperatorTag.EXCHANGE)
            return false;
        AbstractLogicalOperator inputOp = (AbstractLogicalOperator) exchange.getInputs().get(0).getOperator();
        if (inputOp.getOperatorTag() != LogicalOperatorTag.DATASOURCESCAN)
            return false;
        DataSourceScanOperator scanOp = (DataSourceScanOperator) inputOp;
        ProjectOperator projectOp = (ProjectOperator) project;
        scanOp.addProjectVariables(projectOp.getVariables());
        if (op.getOperatorTag() != LogicalOperatorTag.EXCHANGE) {
            op.getInputs().set(0, project.getInputs().get(0));
        } else {
            op.getInputs().set(0, exchange.getInputs().get(0));
        }
        return true;
    }
}

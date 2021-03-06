package edu.uci.ics.algebricks.compiler.optimizer.base;

import java.util.HashSet;
import java.util.Set;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractOperatorWithNestedPlans;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.NestedTupleSourceOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors.FDsAndEquivClassesVisitor;
import edu.uci.ics.algebricks.config.AlgebricksConfig;

public class PhysicalOptimizationsUtil {

    public static void computeFDsAndEquivalenceClasses(AbstractLogicalOperator op, IOptimizationContext ctx)
            throws AlgebricksException {
        FDsAndEquivClassesVisitor visitor = new FDsAndEquivClassesVisitor();
        Set<ILogicalOperator> visitSet = new HashSet<ILogicalOperator>();
        computeFDsAndEqClassesWithVisitorRec(op, ctx, visitor, visitSet);
    }

    private static void computeFDsAndEqClassesWithVisitorRec(AbstractLogicalOperator op, IOptimizationContext ctx,
            FDsAndEquivClassesVisitor visitor, Set<ILogicalOperator> visitSet) throws AlgebricksException {
        visitSet.add(op);
        for (LogicalOperatorReference i : op.getInputs()) {
            computeFDsAndEqClassesWithVisitorRec((AbstractLogicalOperator) i.getOperator(), ctx, visitor, visitSet);
        }
        if (op.hasNestedPlans()) {
            for (ILogicalPlan p : ((AbstractOperatorWithNestedPlans) op).getNestedPlans()) {
                for (LogicalOperatorReference r : p.getRoots()) {
                    AbstractLogicalOperator rootOp = (AbstractLogicalOperator) r.getOperator();
                    computeFDsAndEqClassesWithVisitorRec(rootOp, ctx, visitor, visitSet);
                }
            }
        }
        if (op.getOperatorTag() == LogicalOperatorTag.NESTEDTUPLESOURCE) {
            NestedTupleSourceOperator nts = (NestedTupleSourceOperator) op;
            ILogicalOperator source = nts.getDataSourceReference().getOperator().getInputs().get(0).getOperator();
            if (!visitSet.contains(source)) {
                computeFDsAndEqClassesWithVisitorRec((AbstractLogicalOperator) source, ctx, visitor, visitSet);
            }
        }
        op.accept(visitor, ctx);
        if (AlgebricksConfig.DEBUG) {
            AlgebricksConfig.ALGEBRICKS_LOGGER.fine("--> op. type = " + op.getOperatorTag() + "\n"
                    + "    equiv. classes = " + ctx.getEquivalenceClassMap(op) + "\n" + "    FDs = "
                    + ctx.getFDList(op) + "\n");
        }
    }

}

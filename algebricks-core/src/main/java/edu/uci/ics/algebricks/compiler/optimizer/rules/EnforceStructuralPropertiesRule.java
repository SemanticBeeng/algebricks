package edu.uci.ics.algebricks.compiler.optimizer.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.exceptions.NotImplementedException;
import edu.uci.ics.algebricks.compiler.algebra.base.EquivalenceClass;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.IPhysicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractOperatorWithNestedPlans;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.DistinctOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ExchangeOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.OrderOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.OrderOperator.IOrder;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.OrderOperator.IOrder.OrderKind;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.visitors.FDsAndEquivClassesVisitor;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.AbstractStableSortPOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.BroadcastPOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.ExternalGroupByPOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.HashPartitionExchangePOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.HashPartitionMergeExchangePOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.InMemoryStableSortPOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.PreSortedDistinctByPOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.PreclusteredGroupByPOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.RandomMergeExchangePOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.RangePartitionPOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.SortMergeExchangePOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.physical.StableSortPOperator;
import edu.uci.ics.algebricks.compiler.algebra.prettyprint.LogicalOperatorPrettyPrintVisitor;
import edu.uci.ics.algebricks.compiler.algebra.prettyprint.PlanPrettyPrinter;
import edu.uci.ics.algebricks.compiler.algebra.properties.AsterixNodeGroupDomain;
import edu.uci.ics.algebricks.compiler.algebra.properties.FunctionalDependency;
import edu.uci.ics.algebricks.compiler.algebra.properties.ILocalStructuralProperty;
import edu.uci.ics.algebricks.compiler.algebra.properties.INodeDomain;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPartitioningProperty;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPartitioningRequirementsCoordinator;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPhysicalPropertiesVector;
import edu.uci.ics.algebricks.compiler.algebra.properties.LocalGroupingProperty;
import edu.uci.ics.algebricks.compiler.algebra.properties.LocalOrderProperty;
import edu.uci.ics.algebricks.compiler.algebra.properties.OrderColumn;
import edu.uci.ics.algebricks.compiler.algebra.properties.OrderedPartitionedProperty;
import edu.uci.ics.algebricks.compiler.algebra.properties.PhysicalRequirements;
import edu.uci.ics.algebricks.compiler.algebra.properties.PropertiesUtil;
import edu.uci.ics.algebricks.compiler.algebra.properties.RandomPartitioningProperty;
import edu.uci.ics.algebricks.compiler.algebra.properties.StructuralPropertiesVector;
import edu.uci.ics.algebricks.compiler.algebra.properties.UnorderedPartitionedProperty;
import edu.uci.ics.algebricks.compiler.algebra.properties.ILocalStructuralProperty.PropertyType;
import edu.uci.ics.algebricks.compiler.algebra.properties.IPartitioningProperty.PartitioningType;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.compiler.optimizer.base.OptimizationUtil;
import edu.uci.ics.algebricks.compiler.optimizer.base.PhysicalOptimizationConfig;
import edu.uci.ics.algebricks.compiler.optimizer.base.PhysicalOptimizationsUtil;
import edu.uci.ics.algebricks.config.AlgebricksConfig;
import edu.uci.ics.algebricks.utils.Pair;

public class EnforceStructuralPropertiesRule implements IAlgebraicRewriteRule {

    private static final INodeDomain DEFAULT_DOMAIN = new AsterixNodeGroupDomain("__DEFAULT");

    private PhysicalOptimizationConfig physicalOptimizationConfig;

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        // wait for the physical operators to be set first
        if (op.getPhysicalOperator() == null) {
            return false;
        }
        if (context.checkIfInDontApplySet(this, op)) {
            return false;
        }

        List<FunctionalDependency> fds = context.getFDList(op);
        if (fds != null && !fds.isEmpty()) {
            return false;
        }
        // These are actually logical constraints, so they could be pre-computed
        // somewhere else, too.

        physicalOptimizationConfig = context.getPhysicalOptimizationConfig();
        AlgebricksConfig.ALGEBRICKS_LOGGER.fine(">>>> Optimizing operator " + op.getPhysicalOperator() + ".\n");

        PhysicalOptimizationsUtil.computeFDsAndEquivalenceClasses(op, context);

        StructuralPropertiesVector pvector = new StructuralPropertiesVector(new RandomPartitioningProperty(null),
                new LinkedList<ILocalStructuralProperty>());
        boolean changed = physOptimizeOp(opRef, pvector, false, context);
        op.computeDeliveredPhysicalProperties(context);
        AlgebricksConfig.ALGEBRICKS_LOGGER.finest(">>>> Structural properties for " + op.getPhysicalOperator() + ": "
                + op.getDeliveredPhysicalProperties() + "\n");

        context.addToDontApplySet(this, opRef.getOperator());

        return changed;
    }

    private boolean physOptimizePlan(ILogicalPlan plan, IPhysicalPropertiesVector pvector, boolean nestedPlan,
            IOptimizationContext context) throws AlgebricksException {
        boolean changed = false;
        for (LogicalOperatorReference root : plan.getRoots()) {
            if (physOptimizeOp(root, pvector, nestedPlan, context)) {
                changed = true;
            }
            AbstractLogicalOperator op = (AbstractLogicalOperator) root.getOperator();
            op.computeDeliveredPhysicalProperties(context);
            AlgebricksConfig.ALGEBRICKS_LOGGER.finest(">>>> Structural properties for " + op.getPhysicalOperator()
                    + ": " + op.getDeliveredPhysicalProperties() + "\n");
        }
        return changed;
    }

    private boolean physOptimizeOp(LogicalOperatorReference opRef, IPhysicalPropertiesVector required,
            boolean nestedPlan, IOptimizationContext context) throws AlgebricksException {

        boolean changed = false;
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        optimizeUsingConstraintsAndEquivClasses(op);
        PhysicalRequirements pr = op.getRequiredPhysicalPropertiesForChildren(required);
        IPhysicalPropertiesVector[] reqdProperties = null;
        if (pr != null) {
            reqdProperties = pr.getRequiredProperties();
        }
        boolean opIsRedundantSort = false;

        // compute properties and figure out the domain
        INodeDomain childrenDomain = null;
        {
            int j = 0;
            for (LogicalOperatorReference childRef : op.getInputs()) {
                AbstractLogicalOperator child = (AbstractLogicalOperator) childRef.getOperator();
                // recursive call
                if (physOptimizeOp(childRef, reqdProperties[j], nestedPlan, context)) {
                    changed = true;
                }
                child.computeDeliveredPhysicalProperties(context);
                IPhysicalPropertiesVector delivered = child.getDeliveredPhysicalProperties();
                if (childrenDomain == null) {
                    childrenDomain = delivered.getPartitioningProperty().getNodeDomain();
                } else {
                    INodeDomain dom2 = delivered.getPartitioningProperty().getNodeDomain();
                    if (!childrenDomain.sameAs(dom2)) {
                        childrenDomain = DEFAULT_DOMAIN;
                    }
                }
                j++;
            }
        }

        if (reqdProperties != null) {
            for (int k = 0; k < reqdProperties.length; k++) {
                IPhysicalPropertiesVector pv = reqdProperties[k];
                IPartitioningProperty pp = pv.getPartitioningProperty();
                if (pp != null && pp.getNodeDomain() == null) {
                    pp.setNodeDomain(childrenDomain);
                }
            }
        }

        IPartitioningProperty firstDeliveredPartitioning = null;
        int i = 0;
        for (LogicalOperatorReference childRef : op.getInputs()) {
            AbstractLogicalOperator child = (AbstractLogicalOperator) childRef.getOperator();
            IPhysicalPropertiesVector delivered = child.getDeliveredPhysicalProperties();

            AlgebricksConfig.ALGEBRICKS_LOGGER.finest(">>>> Properties delivered by " + child.getPhysicalOperator()
                    + ": " + delivered + "\n");
            IPartitioningRequirementsCoordinator prc = pr.getPartitioningCoordinator();
            Pair<Boolean, IPartitioningProperty> pbpp = prc.coordinateRequirements(reqdProperties[i]
                    .getPartitioningProperty(), firstDeliveredPartitioning, op, context);
            boolean mayExpandPartitioningProperties = pbpp.first;
            IPhysicalPropertiesVector rqd = new StructuralPropertiesVector(pbpp.second, reqdProperties[i]
                    .getLocalProperties());

            AlgebricksConfig.ALGEBRICKS_LOGGER.finest(">>>> Required properties for " + child.getPhysicalOperator()
                    + ": " + rqd + "\n");
            IPhysicalPropertiesVector diff = delivered.getUnsatisfiedPropertiesFrom(rqd,
                    mayExpandPartitioningProperties, context.getEquivalenceClassMap(child), context.getFDList(child));

            if (isRedundantSort(opRef, delivered, diff, context)) {
                opIsRedundantSort = true;
            }

            if (diff != null) {
                changed = true;
                addEnforcers(op, i, diff, rqd, delivered, childrenDomain, nestedPlan, context);

                AbstractLogicalOperator newChild = ((AbstractLogicalOperator) op.getInputs().get(i).getOperator());

                if (newChild != child) {
                    delivered = newChild.getDeliveredPhysicalProperties();
                    IPhysicalPropertiesVector newDiff = newPropertiesDiff(newChild, rqd,
                            mayExpandPartitioningProperties, context);
                    AlgebricksConfig.ALGEBRICKS_LOGGER.finest(">>>> New properties diff: " + newDiff + "\n");

                    if (isRedundantSort(opRef, delivered, newDiff, context)) {
                        opIsRedundantSort = true;
                        break;
                    }
                }

            }
            if (firstDeliveredPartitioning == null) {
                IPartitioningProperty dpp = delivered.getPartitioningProperty();
                if (dpp.getPartitioningType() == PartitioningType.ORDERED_PARTITIONED
                        || dpp.getPartitioningType() == PartitioningType.UNORDERED_PARTITIONED) {
                    firstDeliveredPartitioning = dpp;
                }
            }

            i++;
        }

        if (op.hasNestedPlans()) {
            AbstractOperatorWithNestedPlans nested = (AbstractOperatorWithNestedPlans) op;
            for (ILogicalPlan p : nested.getNestedPlans()) {
                if (physOptimizePlan(p, required, true, context)) {
                    changed = true;
                }
            }
        }

        if (opIsRedundantSort) {
            if (AlgebricksConfig.DEBUG) {
                AlgebricksConfig.ALGEBRICKS_LOGGER.fine(">>>> Removing redundant SORT operator "
                        + op.getPhysicalOperator() + "\n");
                printOp((AbstractLogicalOperator) op);
            }
            changed = true;
            AbstractLogicalOperator nextOp = (AbstractLogicalOperator) op.getInputs().get(0).getOperator();
            if (nextOp.getOperatorTag() == LogicalOperatorTag.PROJECT) {
                nextOp = (AbstractLogicalOperator) nextOp.getInputs().get(0).getOperator();
            }
            opRef.setOperator(nextOp);
            // Now, transfer annotations from the original sort op. to this one.
            AbstractLogicalOperator transferTo = nextOp;
            if (transferTo.getOperatorTag() == LogicalOperatorTag.EXCHANGE) {
                transferTo = (AbstractLogicalOperator) transferTo.getInputs().get(0).getOperator();
            }
            transferTo.getAnnotations().putAll(op.getAnnotations());
            physOptimizeOp(opRef, required, nestedPlan, context);
        }
        return changed;
    }

    private IPhysicalPropertiesVector newPropertiesDiff(AbstractLogicalOperator newChild,
            IPhysicalPropertiesVector required, boolean mayExpandPartitioningProperties, IOptimizationContext context)
            throws AlgebricksException {
        IPhysicalPropertiesVector newDelivered = newChild.getDeliveredPhysicalProperties();

        Map<LogicalVariable, EquivalenceClass> newChildEqClasses = context.getEquivalenceClassMap(newChild);
        List<FunctionalDependency> newChildFDs = context.getFDList(newChild);
        if (newChildEqClasses == null || newChildFDs == null) {
            FDsAndEquivClassesVisitor fdsVisitor = new FDsAndEquivClassesVisitor();
            newChild.accept(fdsVisitor, context);
            newChildEqClasses = context.getEquivalenceClassMap(newChild);
            newChildFDs = context.getFDList(newChild);
        }
        AlgebricksConfig.ALGEBRICKS_LOGGER.finest(">>>> Required properties for new op. "
                + newChild.getPhysicalOperator() + ": " + required + "\n");

        return newDelivered.getUnsatisfiedPropertiesFrom(required, mayExpandPartitioningProperties, newChildEqClasses,
                newChildFDs);
    }

    private void optimizeUsingConstraintsAndEquivClasses(AbstractLogicalOperator op) {
        IPhysicalOperator pOp = op.getPhysicalOperator();
        switch (pOp.getOperatorTag()) {
            case HASH_GROUP_BY:
            case EXTERNAL_GROUP_BY: {
                GroupByOperator gby = (GroupByOperator) op;
                ExternalGroupByPOperator hgbyOp = (ExternalGroupByPOperator) pOp;
                hgbyOp.computeColumnSet(gby.getGroupByList());
                break;
            }
            case PRE_CLUSTERED_GROUP_BY: {
                GroupByOperator gby = (GroupByOperator) op;
                PreclusteredGroupByPOperator preSortedGby = (PreclusteredGroupByPOperator) pOp;
                preSortedGby.setGbyColumns(gby.getGbyVarList());
                break;
            }
            case PRE_SORTED_DISTINCT_BY: {
                DistinctOperator d = (DistinctOperator) op;
                PreSortedDistinctByPOperator preSortedDistinct = (PreSortedDistinctByPOperator) pOp;
                preSortedDistinct.setDistinctByColumns(d.getDistinctByVarList());
                break;
            }
        }
    }

    private List<OrderColumn> getOrderColumnsFromGroupingProperties(List<ILocalStructuralProperty> reqd,
            List<ILocalStructuralProperty> dlvd) {
        List<OrderColumn> returnedProperties = new ArrayList<OrderColumn>();
        List<LogicalVariable> rqdCols = new ArrayList<LogicalVariable>();
        List<LogicalVariable> dlvdCols = new ArrayList<LogicalVariable>();
        for (ILocalStructuralProperty r : reqd) {
            r.getVariables(rqdCols);
        }
        for (ILocalStructuralProperty d : dlvd) {
            d.getVariables(dlvdCols);
        }

        int prefix = dlvdCols.size() - 1;
        for (; prefix >= 0;)
            if (!rqdCols.contains(dlvdCols.get(prefix)))
                prefix--;
            else
                break;
        for (int j = 0; j <= prefix; j++) {
            LocalOrderProperty orderProp = (LocalOrderProperty) dlvd.get(j);
            returnedProperties.add(new OrderColumn(orderProp.getColumn(), orderProp.getOrder()));
        }
        return returnedProperties;
    }

    /*
     * We assume delivered to be already normalized.
     */
    private boolean isRedundantSort(LogicalOperatorReference opRef, IPhysicalPropertiesVector delivered,
            IPhysicalPropertiesVector diffOfProperties, IOptimizationContext context) {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        if (op.getOperatorTag() != LogicalOperatorTag.ORDER
                || (op.getPhysicalOperator().getOperatorTag() != PhysicalOperatorTag.STABLE_SORT && op
                        .getPhysicalOperator().getOperatorTag() != PhysicalOperatorTag.IN_MEMORY_STABLE_SORT)
                || delivered.getLocalProperties() == null) {
            return false;
        }
        AbstractStableSortPOperator sortOp = (AbstractStableSortPOperator) op.getPhysicalOperator();
        sortOp.computeLocalProperties(op);
        List<ILocalStructuralProperty> orderProps = sortOp.getOrderProperties();
        return PropertiesUtil.matchLocalProperties(orderProps, delivered.getLocalProperties(), context
                .getEquivalenceClassMap(op), context.getFDList(op));
    }

    private void addEnforcers(AbstractLogicalOperator op, int childIndex,
            IPhysicalPropertiesVector diffPropertiesVector, IPhysicalPropertiesVector required,
            IPhysicalPropertiesVector deliveredByChild, INodeDomain domain, boolean nestedPlan,
            IOptimizationContext context) throws AlgebricksException {

        IPartitioningProperty pp = diffPropertiesVector.getPartitioningProperty();
        if (pp == null || pp.getPartitioningType() == PartitioningType.UNPARTITIONED) {
            addLocalEnforcers(op, childIndex, diffPropertiesVector.getLocalProperties(), nestedPlan, context);
            IPhysicalPropertiesVector deliveredByNewChild = ((AbstractLogicalOperator) op.getInputs().get(0)
                    .getOperator()).getDeliveredPhysicalProperties();
            addPartitioningEnforcers(op, childIndex, pp, required, deliveredByNewChild, domain, context);
        } else {
            addPartitioningEnforcers(op, childIndex, pp, required, deliveredByChild, domain, context);
            AbstractLogicalOperator newChild = (AbstractLogicalOperator) op.getInputs().get(childIndex).getOperator();
            IPhysicalPropertiesVector newDiff = newPropertiesDiff(newChild, required, true, context);
            AlgebricksConfig.ALGEBRICKS_LOGGER.finest(">>>> New properties diff: " + newDiff + "\n");
            if (newDiff != null) {
                addLocalEnforcers(op, childIndex, newDiff.getLocalProperties(), nestedPlan, context);
            }
        }
    }

    private void addLocalEnforcers(AbstractLogicalOperator op, int i, List<ILocalStructuralProperty> localProperties,
            boolean nestedPlan, IOptimizationContext context) throws AlgebricksException {
        if (AlgebricksConfig.DEBUG) {
            AlgebricksConfig.ALGEBRICKS_LOGGER.fine(">>>> Adding local enforcers for local props = " + localProperties
                    + "\n");
        }

        if (localProperties == null || localProperties.isEmpty()) {
            return;
        }

        LogicalOperatorReference topOp = new LogicalOperatorReference();
        topOp.setOperator(op.getInputs().get(i).getOperator());
        LinkedList<LocalOrderProperty> oList = new LinkedList<LocalOrderProperty>();

        for (ILocalStructuralProperty prop : localProperties) {
            switch (prop.getPropertyType()) {
                case LOCAL_ORDER_PROPERTY: {
                    oList.add((LocalOrderProperty) prop);
                    break;
                }
                case LOCAL_GROUPING_PROPERTY: {
                    LocalGroupingProperty g = (LocalGroupingProperty) prop;
                    Collection<LogicalVariable> vars = (g.getPreferredOrderEnforcer() != null) ? g
                            .getPreferredOrderEnforcer() : g.getColumnSet();
                    for (LogicalVariable v : vars) {
                        OrderColumn oc = new OrderColumn(v, OrderKind.ASC);
                        LocalOrderProperty lop = new LocalOrderProperty(oc);
                        oList.add(lop);
                    }
                    break;
                }
                default: {
                    throw new IllegalStateException();
                }
            }
        }
        if (!oList.isEmpty()) {
            topOp = enforceOrderProperties(oList, topOp, nestedPlan, context);
        }

        op.getInputs().set(i, topOp);
        OptimizationUtil.computeSchemaAndPropertiesRecIfNull((AbstractLogicalOperator) topOp.getOperator(), context);
        printOp((AbstractLogicalOperator) topOp.getOperator());
    }

    private LogicalOperatorReference enforceOrderProperties(List<LocalOrderProperty> oList,
            LogicalOperatorReference topOp, boolean isMicroOp, IOptimizationContext context) throws AlgebricksException {
        List<Pair<IOrder, LogicalExpressionReference>> oe = new LinkedList<Pair<IOrder, LogicalExpressionReference>>();
        for (LocalOrderProperty o : oList) {
            IOrder ordType = (o.getOrder() == OrderKind.ASC) ? OrderOperator.ASC_ORDER : OrderOperator.DESC_ORDER;
            Pair<IOrder, LogicalExpressionReference> pair = new Pair<IOrder, LogicalExpressionReference>(ordType,
                    new LogicalExpressionReference(new VariableReferenceExpression(o.getColumn())));
            oe.add(pair);
        }
        OrderOperator oo = new OrderOperator(oe);
        oo.setExecutionMode(AbstractLogicalOperator.ExecutionMode.LOCAL);
        if (isMicroOp) {
            oo.setPhysicalOperator(new InMemoryStableSortPOperator());
        } else {
            oo.setPhysicalOperator(new StableSortPOperator(physicalOptimizationConfig.getMaxFramesExternalSort()));
        }
        oo.getInputs().add(topOp);
        context.computeAndSetTypeEnvironmentForOperator(oo);
        if (AlgebricksConfig.DEBUG) {
            AlgebricksConfig.ALGEBRICKS_LOGGER.fine(">>>> Added sort enforcer " + oo.getPhysicalOperator() + ".\n");
        }
        return new LogicalOperatorReference(oo);
    }

    private void addPartitioningEnforcers(ILogicalOperator op, int i, IPartitioningProperty pp,
            IPhysicalPropertiesVector required, IPhysicalPropertiesVector deliveredByChild, INodeDomain domain,
            IOptimizationContext context) throws AlgebricksException {
        if (pp != null) {
            IPhysicalOperator pop;
            switch (pp.getPartitioningType()) {
                case UNPARTITIONED: {
                    List<OrderColumn> ordCols = computeOrderColumns(deliveredByChild);
                    if (ordCols == null || ordCols.size() == 0) {
                        pop = new RandomMergeExchangePOperator();
                    } else {
                        OrderColumn[] sortColumns = new OrderColumn[ordCols.size()];
                        sortColumns = ordCols.toArray(sortColumns);
                        pop = new SortMergeExchangePOperator(sortColumns);
                    }
                    break;
                }
                case UNORDERED_PARTITIONED: {
                    List<LogicalVariable> varList = new ArrayList<LogicalVariable>(((UnorderedPartitionedProperty) pp)
                            .getColumnSet());
                    List<ILocalStructuralProperty> cldLocals = deliveredByChild.getLocalProperties();
                    List<ILocalStructuralProperty> reqdLocals = required.getLocalProperties();
                    boolean propWasSet = false;
                    pop = null;
                    if (reqdLocals != null && cldLocals != null && allAreOrderProps(cldLocals)) {
                        AbstractLogicalOperator c = (AbstractLogicalOperator) op.getInputs().get(i).getOperator();
                        Map<LogicalVariable, EquivalenceClass> ecs = context.getEquivalenceClassMap(c);
                        List<FunctionalDependency> fds = context.getFDList(c);
                        if (PropertiesUtil.matchLocalProperties(reqdLocals, cldLocals, ecs, fds)) {
                            List<OrderColumn> orderColumns = getOrderColumnsFromGroupingProperties(reqdLocals,
                                    cldLocals);
                            pop = new HashPartitionMergeExchangePOperator(orderColumns, varList, domain);
                            propWasSet = true;
                        }
                    }
                    if (!propWasSet) {
                        pop = new HashPartitionExchangePOperator(varList, domain);
                    }
                    break;
                }
                case ORDERED_PARTITIONED: {
                    pop = new RangePartitionPOperator(((OrderedPartitionedProperty) pp).getOrderColumns(), domain);
                    break;
                }
                case BROADCAST: {
                    pop = new BroadcastPOperator(domain);
                    break;
                }
                case RANDOM: {
                    RandomPartitioningProperty rpp = (RandomPartitioningProperty) pp;
                    INodeDomain nd = rpp.getNodeDomain();
                    if (nd == null) {
                        throw new AlgebricksException("Unknown node domain for " + rpp);
                    }
                    if (nd.cardinality() == null) {
                        throw new AlgebricksException("Unknown cardinality for node domain " + nd);
                    }
                    if (nd.cardinality() != 1) {
                        throw new NotImplementedException(
                                "Random repartitioning is only implemented for target domains of"
                                        + "cardinality equal to 1.");
                    }
                    pop = new BroadcastPOperator(nd);
                    break;
                }
                default: {
                    throw new NotImplementedException("Enforcer for " + pp.getPartitioningType()
                            + " partitioning type has not been implemented.");
                }
            }
            LogicalOperatorReference ci = op.getInputs().get(i);
            ExchangeOperator exchg = new ExchangeOperator();
            exchg.setPhysicalOperator(pop);
            setNewOp(ci, exchg, context);
            exchg.setExecutionMode(AbstractLogicalOperator.ExecutionMode.PARTITIONED);
            OptimizationUtil.computeSchemaAndPropertiesRecIfNull(exchg, context);
            context.computeAndSetTypeEnvironmentForOperator(exchg);
            if (AlgebricksConfig.DEBUG) {
                AlgebricksConfig.ALGEBRICKS_LOGGER.fine(">>>> Added partitioning enforcer "
                        + exchg.getPhysicalOperator() + ".\n");
                printOp((AbstractLogicalOperator) op);
            }
        }
    }

    private boolean allAreOrderProps(List<ILocalStructuralProperty> cldLocals) {
        for (ILocalStructuralProperty lsp : cldLocals) {
            if (lsp.getPropertyType() != PropertyType.LOCAL_ORDER_PROPERTY) {
                return false;
            }
        }
        return !cldLocals.isEmpty();
    }

    private void printOp(AbstractLogicalOperator op) throws AlgebricksException {
        StringBuilder sb = new StringBuilder();
        LogicalOperatorPrettyPrintVisitor pvisitor = new LogicalOperatorPrettyPrintVisitor();
        PlanPrettyPrinter.printOperator(op, sb, pvisitor, 0);
        AlgebricksConfig.ALGEBRICKS_LOGGER.fine(sb.toString());
    }

    private List<OrderColumn> computeOrderColumns(IPhysicalPropertiesVector pv) {
        List<OrderColumn> ordCols = new ArrayList<OrderColumn>();
        List<ILocalStructuralProperty> localProps = pv.getLocalProperties();
        if (localProps == null || localProps.size() == 0) {
            return null;
        } else {
            for (ILocalStructuralProperty p : localProps) {
                if (p.getPropertyType() == PropertyType.LOCAL_ORDER_PROPERTY) {
                    LocalOrderProperty lop = (LocalOrderProperty) p;
                    ordCols.add(lop.getOrderColumn());
                } else {
                    return null;
                }
            }
            return ordCols;
        }

    }

    private void setNewOp(LogicalOperatorReference opRef, AbstractLogicalOperator newOp, IOptimizationContext context)
            throws AlgebricksException {
        ILogicalOperator oldOp = opRef.getOperator();
        opRef.setOperator(newOp);
        newOp.getInputs().add(new LogicalOperatorReference(oldOp));
        newOp.recomputeSchema();
        newOp.computeDeliveredPhysicalProperties(context);
        context.computeAndSetTypeEnvironmentForOperator(newOp);
        AlgebricksConfig.ALGEBRICKS_LOGGER.finest(">>>> Structural properties for " + newOp.getPhysicalOperator()
                + ": " + newOp.getDeliveredPhysicalProperties() + "\n");

        PhysicalOptimizationsUtil.computeFDsAndEquivalenceClasses(newOp, context);
    }

}

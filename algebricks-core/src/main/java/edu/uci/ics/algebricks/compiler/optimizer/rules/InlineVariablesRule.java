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
package edu.uci.ics.algebricks.compiler.optimizer.rules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.EquivalenceClass;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.AbstractLogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.ConstantExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractOperatorWithNestedPlans;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AssignOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.ProjectOperator;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalExpressionReferenceTransform;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.config.AlgebricksConfig;
import edu.uci.ics.algebricks.utils.Pair;

public class InlineVariablesRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) {
        return false;
    }

    @Override
    /**
     * 
     * Does one big DFS sweep over the plan.
     * 
     */
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        if (context.checkIfInDontApplySet(this, opRef.getOperator())) {
            return false;
        }
        VariableSubstitutionVisitor substVisitor = new VariableSubstitutionVisitor(false);
        VariableSubstitutionVisitor substVisitorForWrites = new VariableSubstitutionVisitor(true);
        substVisitor.setContext(context);
        substVisitorForWrites.setContext(context);
        Pair<Boolean, Boolean> bb = collectEqClassesAndRemoveRedundantOps(opRef, context, true,
                new LinkedList<EquivalenceClass>(), substVisitor, substVisitorForWrites);
        return bb.first;
    }

    private Pair<Boolean, Boolean> collectEqClassesAndRemoveRedundantOps(LogicalOperatorReference opRef,
            IOptimizationContext context, boolean first, List<EquivalenceClass> equivClasses,
            VariableSubstitutionVisitor substVisitor, VariableSubstitutionVisitor substVisitorForWrites)
            throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        // if (context.checkIfInDontApplySet(this, opRef.getOperator())) {
        // return false;
        // }
        if (op.getOperatorTag() == LogicalOperatorTag.UNNEST_MAP) {
            return new Pair<Boolean, Boolean>(false, false);
        }
        boolean modified = false;
        boolean ecChange = false;
        int cnt = 0;
        for (LogicalOperatorReference i : op.getInputs()) {
            boolean isOuterInputBranch = op.getOperatorTag() == LogicalOperatorTag.LEFTOUTERJOIN && cnt == 1;
            List<EquivalenceClass> eqc = isOuterInputBranch ? new LinkedList<EquivalenceClass>() : equivClasses;

            Pair<Boolean, Boolean> bb = (collectEqClassesAndRemoveRedundantOps(i, context, false, eqc, substVisitor,
                    substVisitorForWrites));

            if (bb.first) {
                modified = true;
            }
            if (bb.second) {
                ecChange = true;
            }

            if (isOuterInputBranch) {
                if (AlgebricksConfig.DEBUG) {
                    AlgebricksConfig.ALGEBRICKS_LOGGER.finest("--- Equivalence classes for inner branch of outer op.: "
                            + eqc + "\n");
                }
                for (EquivalenceClass ec : eqc) {
                    if (!ec.representativeIsConst()) {
                        equivClasses.add(ec);
                    }
                }
            }

            ++cnt;
        }
        if (op.hasNestedPlans()) {
            AbstractOperatorWithNestedPlans n = (AbstractOperatorWithNestedPlans) op;
            List<EquivalenceClass> eqc = equivClasses;
            if (n.getOperatorTag() == LogicalOperatorTag.SUBPLAN) {
                eqc = new LinkedList<EquivalenceClass>();
            } else {
                eqc = equivClasses;
            }
            for (ILogicalPlan p : n.getNestedPlans()) {
                for (LogicalOperatorReference r : p.getRoots()) {
                    Pair<Boolean, Boolean> bb = collectEqClassesAndRemoveRedundantOps(r, context, false, eqc,
                            substVisitor, substVisitorForWrites);
                    if (bb.first) {
                        modified = true;
                    }
                    if (bb.second) {
                        ecChange = true;
                    }
                }
            }
        }
        // we assume a variable is assigned a value only once
        if (op.getOperatorTag() == LogicalOperatorTag.ASSIGN) {
            AssignOperator a = (AssignOperator) op;
            ILogicalExpression rhs = a.getExpressions().get(0).getExpression();
            if (rhs.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                LogicalVariable varLeft = a.getVariables().get(0);
                VariableReferenceExpression varRef = (VariableReferenceExpression) rhs;
                LogicalVariable varRight = varRef.getVariableReference();

                EquivalenceClass ecRight = findEquivClass(varRight, equivClasses);
                if (ecRight != null) {
                    ecRight.addMember(varLeft);
                } else {
                    List<LogicalVariable> m = new LinkedList<LogicalVariable>();
                    m.add(varRight);
                    m.add(varLeft);
                    EquivalenceClass ec = new EquivalenceClass(m, varRight);
                    equivClasses.add(ec);
                    if (AlgebricksConfig.DEBUG) {
                        AlgebricksConfig.ALGEBRICKS_LOGGER.finest("--- New equivalence class: " + ec + "\n");
                    }
                }
                ecChange = true;
            } else if (((AbstractLogicalExpression) rhs).getExpressionTag() == LogicalExpressionTag.CONSTANT) {
                LogicalVariable varLeft = a.getVariables().get(0);
                List<LogicalVariable> m = new LinkedList<LogicalVariable>();
                m.add(varLeft);
                EquivalenceClass ec = new EquivalenceClass(m, (ConstantExpression) rhs);
                // equivClassesForParent.add(ec);
                equivClasses.add(ec);
                ecChange = true;
            }
        } else if (op.getOperatorTag() == LogicalOperatorTag.GROUP && !(context.checkIfInDontApplySet(this, op))) {
            GroupByOperator group = (GroupByOperator) op;
            Pair<Boolean, Boolean> r1 = processVarExprPairs(group.getGroupByList(), equivClasses);
            Pair<Boolean, Boolean> r2 = processVarExprPairs(group.getDecorList(), equivClasses);
            modified = modified || r1.first || r2.first;
            ecChange = r1.second || r2.second;
        }
        if (op.getOperatorTag() == LogicalOperatorTag.PROJECT) {
            assignVarsNeededByProject((ProjectOperator) op, equivClasses, context);
        } else {
            if (op.getOperatorTag() == LogicalOperatorTag.WRITE) {
                substVisitorForWrites.setEquivalenceClasses(equivClasses);
                if (op.acceptExpressionTransform(substVisitorForWrites)) {
                    modified = true;
                }
            } else {
                substVisitor.setEquivalenceClasses(equivClasses);
                if (op.acceptExpressionTransform(substVisitor)) {
                    modified = true;
                    if (op.getOperatorTag() == LogicalOperatorTag.GROUP) {
                        GroupByOperator group = (GroupByOperator) op;
                        for (Pair<LogicalVariable, LogicalExpressionReference> gp : group.getGroupByList()) {
                            if (gp.first != null
                                    && gp.second.getExpression().getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                                LogicalVariable gv = ((VariableReferenceExpression) gp.second.getExpression())
                                        .getVariableReference();
                                Iterator<Pair<LogicalVariable, LogicalExpressionReference>> iter = group.getDecorList()
                                        .iterator();
                                while (iter.hasNext()) {
                                    Pair<LogicalVariable, LogicalExpressionReference> dp = iter.next();
                                    if (dp.first == null
                                            && dp.second.getExpression().getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                                        LogicalVariable dv = ((VariableReferenceExpression) dp.second.getExpression())
                                                .getVariableReference();
                                        if (dv == gv) {
                                            // The decor variable is redundant,
                                            // since it is
                                            // propagated as a grouping
                                            // variable.
                                            EquivalenceClass ec1 = findEquivClass(gv, equivClasses);
                                            if (ec1 != null) {
                                                ec1.addMember(gp.first);
                                                ec1.setVariableRepresentative(gp.first);
                                            } else {
                                                List<LogicalVariable> varList = new ArrayList<LogicalVariable>();
                                                varList.add(gp.first);
                                                varList.add(gv);
                                                ec1 = new EquivalenceClass(varList, gp.first);
                                            }
                                            iter.remove();
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return new Pair<Boolean, Boolean>(modified, ecChange);
    }

    private Pair<Boolean, Boolean> processVarExprPairs(List<Pair<LogicalVariable, LogicalExpressionReference>> vePairs,
            List<EquivalenceClass> equivClasses) {
        boolean ecFromGroup = false;
        boolean modified = false;
        for (Pair<LogicalVariable, LogicalExpressionReference> p : vePairs) {
            ILogicalExpression expr = p.second.getExpression();
            if (p.first != null && expr.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression varRef = (VariableReferenceExpression) expr;
                LogicalVariable rhsVar = varRef.getVariableReference();
                ecFromGroup = true;
                EquivalenceClass ecRight = findEquivClass(rhsVar, equivClasses);
                if (ecRight != null) {
                    LogicalVariable replacingVar = ecRight.getVariableRepresentative();
                    if (replacingVar != null && replacingVar != rhsVar) {
                        varRef.setVariable(replacingVar);
                        modified = true;
                    }
                }
            }
        }
        return new Pair<Boolean, Boolean>(modified, ecFromGroup);
    }

    // Instead of doing this, we could make Projection to be more expressive and
    // also take constants (or even expression), at the expense of a more
    // complex project push down.
    private void assignVarsNeededByProject(ProjectOperator op, List<EquivalenceClass> equivClasses,
            IOptimizationContext context) throws AlgebricksException {
        List<LogicalVariable> prVars = op.getVariables();
        int sz = prVars.size();
        for (int i = 0; i < sz; i++) {
            EquivalenceClass ec = findEquivClass(prVars.get(i), equivClasses);
            if (ec != null) {
                if (!ec.representativeIsConst()) {
                    prVars.set(i, ec.getVariableRepresentative());
                }
            }
        }
    }

    private final static EquivalenceClass findEquivClass(LogicalVariable var, List<EquivalenceClass> equivClasses) {
        for (EquivalenceClass ec : equivClasses) {
            if (ec.contains(var)) {
                return ec;
            }
        }
        return null;
    }

    private class VariableSubstitutionVisitor implements ILogicalExpressionReferenceTransform {
        private List<EquivalenceClass> equivClasses;
        private IOptimizationContext context;
        private final boolean doNotSubstWithConst;

        public VariableSubstitutionVisitor(boolean doNotSubstWithConst) {
            this.doNotSubstWithConst = doNotSubstWithConst;
        }

        public void setContext(IOptimizationContext context) {
            this.context = context;
        }

        public void setEquivalenceClasses(List<EquivalenceClass> equivClasses) {
            this.equivClasses = equivClasses;
        }

        @Override
        public boolean transform(LogicalExpressionReference exprRef) {
            ILogicalExpression e = exprRef.getExpression();
            switch (((AbstractLogicalExpression) e).getExpressionTag()) {
                case VARIABLE: {
                    // look for a required substitution
                    LogicalVariable var = ((VariableReferenceExpression) e).getVariableReference();
                    if (context.shouldNotBeInlined(var)) {
                        return false;
                    }
                    EquivalenceClass ec = findEquivClass(var, equivClasses);
                    if (ec == null) {
                        return false;
                    }
                    if (ec.representativeIsConst()) {
                        if (doNotSubstWithConst) {
                            return false;
                        }
                        exprRef.setExpression(ec.getConstRepresentative());
                        return true;
                    } else {
                        LogicalVariable r = ec.getVariableRepresentative();
                        if (!r.equals(var)) {
                            exprRef.setExpression(new VariableReferenceExpression(r));
                            return true;
                        } else {
                            return false;
                        }
                    }
                }
                case FUNCTION_CALL: {
                    AbstractFunctionCallExpression fce = (AbstractFunctionCallExpression) e;
                    boolean m = false;
                    for (LogicalExpressionReference arg : fce.getArguments()) {
                        if (transform(arg)) {
                            m = true;
                        }
                    }
                    return m;
                }
                default: {
                    return false;
                }
            }
        }

    }
}

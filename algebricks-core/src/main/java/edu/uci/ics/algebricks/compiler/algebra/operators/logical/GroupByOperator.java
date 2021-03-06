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
package edu.uci.ics.algebricks.compiler.algebra.operators.logical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.IVariableTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.properties.TypePropagationPolicy;
import edu.uci.ics.algebricks.compiler.algebra.properties.VariablePropagationPolicy;
import edu.uci.ics.algebricks.compiler.algebra.typing.ITypeEnvPointer;
import edu.uci.ics.algebricks.compiler.algebra.typing.ITypingContext;
import edu.uci.ics.algebricks.compiler.algebra.typing.OpRefTypeEnvPointer;
import edu.uci.ics.algebricks.compiler.algebra.typing.PropagatingTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalExpressionReferenceTransform;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalOperatorVisitor;
import edu.uci.ics.algebricks.utils.Pair;

public class GroupByOperator extends AbstractOperatorWithNestedPlans {
    // If the LogicalVariable in a pair is null, it means that the GroupBy is
    // only grouping by the expression, without producing a new variable.
    private final List<Pair<LogicalVariable, LogicalExpressionReference>> gByList;
    private final List<Pair<LogicalVariable, LogicalExpressionReference>> decorList;

    // In decorList, if the variable (first member of the pair) is null, the
    // second member of the pair is variable reference which is propagated.

    public GroupByOperator() {
        super();
        gByList = new ArrayList<Pair<LogicalVariable, LogicalExpressionReference>>();
        decorList = new ArrayList<Pair<LogicalVariable, LogicalExpressionReference>>();
    }

    public GroupByOperator(List<Pair<LogicalVariable, LogicalExpressionReference>> groupByList,
            List<Pair<LogicalVariable, LogicalExpressionReference>> decorList, List<ILogicalPlan> nestedPlans) {
        super(nestedPlans);
        this.decorList = decorList;
        this.gByList = groupByList;
    }

    public void addGbyExpression(LogicalVariable variable, ILogicalExpression expression) {
        this.gByList.add(new Pair<LogicalVariable, LogicalExpressionReference>(variable,
                new LogicalExpressionReference(expression)));
    }

    public void addDecorExpression(LogicalVariable variable, ILogicalExpression expression) {
        this.decorList.add(new Pair<LogicalVariable, LogicalExpressionReference>(variable,
                new LogicalExpressionReference(expression)));
    }

    @Override
    public LogicalOperatorTag getOperatorTag() {
        return LogicalOperatorTag.GROUP;
    }

    public List<Pair<LogicalVariable, LogicalExpressionReference>> getGroupByList() {
        return gByList;
    }

    public String gByListToString() {
        return veListToString(gByList);
    }

    public String decorListToString() {
        return veListToString(decorList);
    }

    public List<LogicalVariable> getGbyVarList() {
        List<LogicalVariable> varList = new ArrayList<LogicalVariable>(gByList.size());
        for (Pair<LogicalVariable, LogicalExpressionReference> ve : gByList) {
            ILogicalExpression expr = ve.second.getExpression();
            if (expr.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression v = (VariableReferenceExpression) expr;
                varList.add(v.getVariableReference());
            }
        }
        return varList;
    }

    public static String veListToString(List<Pair<LogicalVariable, LogicalExpressionReference>> vePairList) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean fst = true;
        for (Pair<LogicalVariable, LogicalExpressionReference> ve : vePairList) {
            if (fst) {
                fst = false;
            } else {
                sb.append("; ");
            }
            if (ve.first != null) {
                sb.append(ve.first + " := " + ve.second);
            } else {
                sb.append(ve.second.getExpression());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void recomputeSchema() {
        super.recomputeSchema();
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gByList) {
            schema.add(p.first);
        }
        for (Pair<LogicalVariable, LogicalExpressionReference> p : decorList) {
            schema.add(getDecorVariable(p));
        }
    }

    @Override
    public void getProducedVariablesExceptNestedPlans(Collection<LogicalVariable> vars) {
        // super.getProducedVariables(vars);
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gByList) {
            if (p.first != null) {
                vars.add(p.first);
            }
        }
        for (Pair<LogicalVariable, LogicalExpressionReference> p : decorList) {
            if (p.first != null) {
                vars.add(p.first);
            }
        }
    }

    @Override
    public void getUsedVariablesExceptNestedPlans(Collection<LogicalVariable> vars) {
        for (Pair<LogicalVariable, LogicalExpressionReference> g : gByList) {
            g.second.getExpression().getUsedVariables(vars);
        }
        for (Pair<LogicalVariable, LogicalExpressionReference> g : decorList) {
            g.second.getExpression().getUsedVariables(vars);
        }
        // super.getUsedVariables(vars);
    }

    @Override
    public VariablePropagationPolicy getVariablePropagationPolicy() {
        return new VariablePropagationPolicy() {

            @Override
            public void propagateVariables(IOperatorSchema target, IOperatorSchema... sources)
                    throws AlgebricksException {
                for (Pair<LogicalVariable, LogicalExpressionReference> p : gByList) {
                    ILogicalExpression expr = p.second.getExpression();
                    if (p.first != null) {
                        target.addVariable(p.first);
                    } else {
                        if (expr.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
                            throw new AlgebricksException("hash group-by expects variable references.");
                        }
                        VariableReferenceExpression v = (VariableReferenceExpression) expr;
                        target.addVariable(v.getVariableReference());
                    }
                }
                for (Pair<LogicalVariable, LogicalExpressionReference> p : decorList) {
                    ILogicalExpression expr = p.second.getExpression();
                    if (expr.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
                        throw new AlgebricksException("pre-sorted group-by expects variable references.");
                    }
                    VariableReferenceExpression v = (VariableReferenceExpression) expr;
                    LogicalVariable decor = v.getVariableReference();
                    if (p.first != null) {
                        target.addVariable(p.first);
                    } else {
                        target.addVariable(decor);
                    }
                }

            }
        };
    }

    @Override
    public boolean acceptExpressionTransform(ILogicalExpressionReferenceTransform visitor) throws AlgebricksException {
        boolean b = false;
        for (Pair<LogicalVariable, LogicalExpressionReference> p : gByList) {
            if (visitor.transform(p.second)) {
                b = true;
            }
        }
        for (Pair<LogicalVariable, LogicalExpressionReference> p : decorList) {
            if (visitor.transform(p.second)) {
                b = true;
            }
        }
        return b;
    }

    @Override
    public <R, T> R accept(ILogicalOperatorVisitor<R, T> visitor, T arg) throws AlgebricksException {
        return visitor.visitGroupByOperator(this, arg);
    }

    public static LogicalVariable getDecorVariable(Pair<LogicalVariable, LogicalExpressionReference> p) {
        if (p.first != null) {
            return p.first;
        } else {
            VariableReferenceExpression e = (VariableReferenceExpression) p.second.getExpression();
            return e.getVariableReference();
        }
    }

    public List<Pair<LogicalVariable, LogicalExpressionReference>> getDecorList() {
        return decorList;
    }

    @Override
    public IVariableTypeEnvironment computeOutputTypeEnvironment(ITypingContext ctx) throws AlgebricksException {
        int n = 0;
        for (ILogicalPlan p : nestedPlans) {
            n += p.getRoots().size();
        }
        ITypeEnvPointer[] envPointers = new ITypeEnvPointer[n];
        int i = 0;
        for (ILogicalPlan p : nestedPlans) {
            for (LogicalOperatorReference r : p.getRoots()) {
                envPointers[i] = new OpRefTypeEnvPointer(r, ctx);
                i++;
            }
        }
        IVariableTypeEnvironment env = new PropagatingTypeEnvironment(ctx.getExpressionTypeComputer(), ctx
                .getNullableTypeComputer(), ctx.getMetadataProvider(), TypePropagationPolicy.ALL, envPointers);
        ILogicalOperator child = inputs.get(0).getOperator();
        IVariableTypeEnvironment env2 = ctx.getOutputTypeEnvironment(child);
        for (Pair<LogicalVariable, LogicalExpressionReference> p : getGroupByList()) {
            ILogicalExpression expr = p.second.getExpression();
            if (p.first != null) {
                env.setVarType(p.first, env2.getType(expr));
                if (expr.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                    LogicalVariable v1 = ((VariableReferenceExpression) expr).getVariableReference();
                    env.setVarType(v1, env2.getVarType(v1));
                }
            } else {
                VariableReferenceExpression vre = (VariableReferenceExpression) p.second.getExpression();
                LogicalVariable v2 = vre.getVariableReference();
                env.setVarType(v2, env2.getVarType(v2));
            }
        }
        for (Pair<LogicalVariable, LogicalExpressionReference> p : getDecorList()) {
            ILogicalExpression expr = p.second.getExpression();
            if (p.first != null) {
                env.setVarType(p.first, env2.getType(expr));
            } else {
                VariableReferenceExpression vre = (VariableReferenceExpression) p.second.getExpression();
                LogicalVariable v2 = vre.getVariableReference();
                env.setVarType(v2, env2.getVarType(v2));
            }
        }
        return env;
    }

}

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
package edu.uci.ics.algebricks.compiler.algebra.expressions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.uci.ics.algebricks.compiler.algebra.base.EquivalenceClass;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.functions.AlgebricksBuiltinFunctions;
import edu.uci.ics.algebricks.compiler.algebra.functions.FunctionIdentifier;
import edu.uci.ics.algebricks.compiler.algebra.functions.IFunctionInfo;
import edu.uci.ics.algebricks.compiler.algebra.properties.FunctionalDependency;

public abstract class AbstractFunctionCallExpression extends AbstractLogicalExpression {

    public enum FunctionKind {
        SCALAR, STATEFUL, AGGREGATE, UNNEST
    }

    protected IFunctionInfo finfo;
    final private List<LogicalExpressionReference> arguments;
    private Object[] opaqueParameters;
    private final FunctionKind kind;
    private Map<Object, IExpressionAnnotation> annotationMap = new HashMap<Object, IExpressionAnnotation>();

    public AbstractFunctionCallExpression(FunctionKind kind, IFunctionInfo finfo,
            List<LogicalExpressionReference> arguments) {
        this.kind = kind;
        this.finfo = finfo;
        this.arguments = arguments;
    }

    public AbstractFunctionCallExpression(FunctionKind kind, IFunctionInfo finfo) {
        this.kind = kind;
        this.finfo = finfo;
        this.arguments = new ArrayList<LogicalExpressionReference>();
    }

    public AbstractFunctionCallExpression(FunctionKind kind, IFunctionInfo finfo,
            LogicalExpressionReference... expressions) {
        this(kind, finfo);
        for (LogicalExpressionReference e : expressions) {
            this.arguments.add(e);
        }
    }

    public void setOpaqueParameters(Object[] opaqueParameters) {
        this.opaqueParameters = opaqueParameters;
    }

    public Object[] getOpaqueParameters() {
        return opaqueParameters;
    }

    public FunctionKind getKind() {
        return kind;
    }

    protected List<LogicalExpressionReference> cloneArguments() {
        List<LogicalExpressionReference> clonedArgs = new ArrayList<LogicalExpressionReference>(arguments.size());
        for (LogicalExpressionReference e : arguments) {
            ILogicalExpression e2 = ((AbstractLogicalExpression) e.getExpression()).cloneExpression();
            clonedArgs.add(new LogicalExpressionReference(e2));
        }
        return clonedArgs;
    }

    public FunctionIdentifier getFunctionIdentifier() {
        return finfo.getFunctionIdentifier();
    }

    public IFunctionInfo getFunctionInfo() {
        return finfo;
    }

    public void setFunctionInfo(IFunctionInfo finfo) {
        this.finfo = finfo;
    }

    public List<LogicalExpressionReference> getArguments() {
        return arguments;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("function-call: " + finfo.getFunctionIdentifier() + ", Args:[");
        // + arguments;
        boolean first = true;
        for (LogicalExpressionReference ref : arguments) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(ref.getExpression());
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public LogicalExpressionTag getExpressionTag() {
        return LogicalExpressionTag.FUNCTION_CALL;
    }

    @Override
    public void getUsedVariables(Collection<LogicalVariable> vars) {
        for (LogicalExpressionReference arg : arguments) {
            arg.getExpression().getUsedVariables(vars);
        }
    }

    @Override
    public void substituteVar(LogicalVariable v1, LogicalVariable v2) {
        for (LogicalExpressionReference arg : arguments) {
            arg.getExpression().substituteVar(v1, v2);
        }
    }

    @Override
    public void getConstraintsAndEquivClasses(Collection<FunctionalDependency> fds,
            Map<LogicalVariable, EquivalenceClass> equivClasses) {
        FunctionIdentifier funId = getFunctionIdentifier();
        if (funId == AlgebricksBuiltinFunctions.AND) {
            for (LogicalExpressionReference a : arguments) {
                a.getExpression().getConstraintsAndEquivClasses(fds, equivClasses);
            }
        } else if (funId == AlgebricksBuiltinFunctions.EQ) {
            ILogicalExpression opLeft = arguments.get(0).getExpression();
            ILogicalExpression opRight = arguments.get(1).getExpression();
            if (opLeft.getExpressionTag() == LogicalExpressionTag.CONSTANT
                    && opRight.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                ConstantExpression op1 = (ConstantExpression) opLeft;
                VariableReferenceExpression op2 = (VariableReferenceExpression) opRight;
                getFDsAndEquivClassesForEqWithConstant(op1, op2, fds, equivClasses);
            } else if (opLeft.getExpressionTag() == LogicalExpressionTag.VARIABLE
                    && opRight.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                VariableReferenceExpression op1 = (VariableReferenceExpression) opLeft;
                VariableReferenceExpression op2 = (VariableReferenceExpression) opRight;
                getFDsAndEquivClassesForColumnEq(op1, op2, fds, equivClasses);
            }
        }
    }

    @Override
    public void getConstraintsForOuterJoin(Collection<FunctionalDependency> fds, Collection<LogicalVariable> outerVars) {
        FunctionIdentifier funId = getFunctionIdentifier();
        if (funId == AlgebricksBuiltinFunctions.AND) {
            for (LogicalExpressionReference a : arguments) {
                a.getExpression().getConstraintsForOuterJoin(fds, outerVars);
            }
        } else if (funId == AlgebricksBuiltinFunctions.EQ) {
            ILogicalExpression opLeft = arguments.get(0).getExpression();
            ILogicalExpression opRight = arguments.get(1).getExpression();
            if (opLeft.getExpressionTag() == LogicalExpressionTag.VARIABLE
                    && opRight.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                LogicalVariable var1 = ((VariableReferenceExpression) opLeft).getVariableReference();
                LogicalVariable var2 = ((VariableReferenceExpression) opRight).getVariableReference();
                if (outerVars.contains(var1)) {
                    addFD(fds, var1, var2);
                }
                if (outerVars.contains(var2)) {
                    addFD(fds, var2, var1);
                }
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AbstractFunctionCallExpression)) {
            return false;
        } else {
            AbstractFunctionCallExpression fce = (AbstractFunctionCallExpression) obj;
            boolean equal = getFunctionIdentifier().equals(fce.getFunctionIdentifier());
            if (!equal)
                return false;
            for (int i = 0; i < arguments.size(); i++) {
                ILogicalExpression argument = arguments.get(i).getExpression();
                ILogicalExpression fceArgument = fce.getArguments().get(i).getExpression();
                if (!argument.equals(fceArgument))
                    return false;
            }
            return true;
        }
    }

    @Override
    public int hashCode() {
        int h = finfo.hashCode();
        for (LogicalExpressionReference e : arguments) {
            h = h * 41 + e.getExpression().hashCode();
        }
        return h;
    }

    @Override
    public boolean splitIntoConjuncts(List<LogicalExpressionReference> conjs) {
        if (getFunctionIdentifier() != AlgebricksBuiltinFunctions.AND || arguments.size() <= 1) {
            return false;
        } else {
            conjs.addAll(arguments);
            return true;
        }
    }

    public Map<Object, IExpressionAnnotation> getAnnotations() {
        return annotationMap;
    }

    protected Map<Object, IExpressionAnnotation> cloneAnnotations() {
        Map<Object, IExpressionAnnotation> m = new HashMap<Object, IExpressionAnnotation>();
        for (Object k : annotationMap.keySet()) {
            IExpressionAnnotation annot2 = annotationMap.get(k).copy();
            m.put(k, annot2);
        }
        return m;
    }

    private final static void addFD(Collection<FunctionalDependency> fds, LogicalVariable var1, LogicalVariable var2) {
        LinkedList<LogicalVariable> set1 = new LinkedList<LogicalVariable>();
        set1.add(var1);
        LinkedList<LogicalVariable> set2 = new LinkedList<LogicalVariable>();
        set2.add(var2);
        FunctionalDependency fd1 = new FunctionalDependency(set1, set2);
        fds.add(fd1);
    }

    private final static void getFDsAndEquivClassesForEqWithConstant(ConstantExpression c,
            VariableReferenceExpression v, Collection<FunctionalDependency> fds,
            Map<LogicalVariable, EquivalenceClass> equivClasses) {
        LogicalVariable var = v.getVariableReference();
        LinkedList<LogicalVariable> head = new LinkedList<LogicalVariable>();
        // empty set in the head
        LinkedList<LogicalVariable> tail = new LinkedList<LogicalVariable>();
        tail.add(var);
        FunctionalDependency fd = new FunctionalDependency(head, tail);
        fds.add(fd);

        EquivalenceClass ec = equivClasses.get(var);
        if (ec == null) {
            LinkedList<LogicalVariable> members = new LinkedList<LogicalVariable>();
            members.add(var);
            EquivalenceClass eclass = new EquivalenceClass(members, c);
            equivClasses.put(var, eclass);
        } else {
            if (ec.representativeIsConst()) {
                ILogicalExpression c1 = ec.getConstRepresentative();
                if (!c1.equals(c)) {
                    // here I could also rewrite to FALSE
                    return;
                }
            }
            ec.setConstRepresentative(c);
        }
    }

    /*
     * Obs.: mgmt. of equiv. classes should use a more efficient data
     * structure,if we are to implem. cost-bazed optim.
     */
    private final static void getFDsAndEquivClassesForColumnEq(VariableReferenceExpression v1,
            VariableReferenceExpression v2, Collection<FunctionalDependency> fds,
            Map<LogicalVariable, EquivalenceClass> equivClasses) {
        LogicalVariable var1 = v1.getVariableReference();
        LogicalVariable var2 = v2.getVariableReference();
        LinkedList<LogicalVariable> set1 = new LinkedList<LogicalVariable>();
        set1.add(var1);
        LinkedList<LogicalVariable> set2 = new LinkedList<LogicalVariable>();
        set2.add(var2);
        FunctionalDependency fd1 = new FunctionalDependency(set1, set2);
        FunctionalDependency fd2 = new FunctionalDependency(set2, set1);
        fds.add(fd1);
        fds.add(fd2);

        EquivalenceClass ec1 = equivClasses.get(var1);
        EquivalenceClass ec2 = equivClasses.get(var2);
        if (ec1 == null && ec2 == null) {
            LinkedList<LogicalVariable> members = new LinkedList<LogicalVariable>();
            members.add(var1);
            members.add(var2);
            EquivalenceClass ec = new EquivalenceClass(members, var1);
            equivClasses.put(var1, ec);
            equivClasses.put(var2, ec);
        } else if (ec1 == null && ec2 != null) {
            ec2.addMember(var1);
            equivClasses.put(var1, ec2);
        } else if (ec2 == null && ec1 != null) {
            ec1.addMember(var2);
            equivClasses.put(var2, ec1);
        } else {
            ec1.merge(ec2);
            for (LogicalVariable w : equivClasses.keySet()) {
                if (ec2.getMembers().contains(w)) {
                    equivClasses.put(w, ec1);
                }
            }
        }
    }

}
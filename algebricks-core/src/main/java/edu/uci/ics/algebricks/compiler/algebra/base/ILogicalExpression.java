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
package edu.uci.ics.algebricks.compiler.algebra.base;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.properties.FunctionalDependency;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalExpressionVisitor;

public interface ILogicalExpression {

    public LogicalExpressionTag getExpressionTag();

    public abstract <R, T> R accept(ILogicalExpressionVisitor<R, T> visitor, T arg) throws AlgebricksException;

    public void getUsedVariables(Collection<LogicalVariable> vars);

    public void substituteVar(LogicalVariable v1, LogicalVariable v2);

    // constraints (e.g., FDs, equivalences)

    /**
     * @param fds
     *            Output argument: functional dependencies that can be inferred
     *            from this expression.
     * @param equivClasses
     *            Output argument: Equivalence classes that can be inferred from
     *            this expression.
     */
    public void getConstraintsAndEquivClasses(Collection<FunctionalDependency> fds,
            Map<LogicalVariable, EquivalenceClass> equivClasses);

    /**
     * @param fds
     *            Output argument: functional dependencies that can be inferred
     *            from this expression.
     * @param outerVars
     *            Input argument: variables coming from outer branch(es), e.g.,
     *            the left branch of a left outer join.
     */
    public void getConstraintsForOuterJoin(Collection<FunctionalDependency> fds, Collection<LogicalVariable> outerVars);

    /**
     * @param conjs
     *            Output argument: a list of expression whose conjunction, in
     *            any order, can replace the current expression.
     * @return true if the expression can be broken in at least two conjuncts,
     *         false otherwise.
     */
    public boolean splitIntoConjuncts(List<LogicalExpressionReference> conjs);

}
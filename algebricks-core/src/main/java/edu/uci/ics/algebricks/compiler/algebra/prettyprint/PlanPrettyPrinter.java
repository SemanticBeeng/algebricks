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
package edu.uci.ics.algebricks.compiler.algebra.prettyprint;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.IPhysicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AbstractOperatorWithNestedPlans;

public class PlanPrettyPrinter {
    public static void printPlan(ILogicalPlan plan, StringBuilder out, LogicalOperatorPrettyPrintVisitor pvisitor,
            int indent) throws AlgebricksException {
        for (LogicalOperatorReference root : plan.getRoots()) {
            printOperator((AbstractLogicalOperator) root.getOperator(), out, pvisitor, indent);
        }
    }

    public static void printPhysicalOps(ILogicalPlan plan, StringBuilder out, int indent) {
        for (LogicalOperatorReference root : plan.getRoots()) {
            printPhysicalOperator((AbstractLogicalOperator) root.getOperator(), indent, out);
        }
    }

    public static void printOperator(AbstractLogicalOperator op, StringBuilder out,
            LogicalOperatorPrettyPrintVisitor pvisitor, int indent) throws AlgebricksException {
        out.append(op.accept(pvisitor, indent));
        IPhysicalOperator pOp = op.getPhysicalOperator();

        if (pOp != null) {
            out.append("\n");
            pad(out, indent);
            appendln(out, "-- " + pOp.toString() + "  |" + op.getExecutionMode() + "|");
        } else {
            appendln(out, " -- |" + op.getExecutionMode() + "|");
        }

        for (LogicalOperatorReference i : op.getInputs()) {
            printOperator((AbstractLogicalOperator) i.getOperator(), out, pvisitor, indent + 2);
        }

    }

    public static void printPhysicalOperator(AbstractLogicalOperator op, int indent, StringBuilder out) {
        IPhysicalOperator pOp = op.getPhysicalOperator();
        pad(out, indent);
        appendln(out, "-- " + pOp.toString() + "  |" + op.getExecutionMode() + "|");
        if (op.hasNestedPlans()) {
            AbstractOperatorWithNestedPlans opNest = (AbstractOperatorWithNestedPlans) op;
            for (ILogicalPlan p : opNest.getNestedPlans()) {
                pad(out, indent + 8);
                appendln(out, "{");
                printPhysicalOps(p, out, indent + 10);
                pad(out, indent + 8);
                appendln(out, "}");
            }
        }

        for (LogicalOperatorReference i : op.getInputs()) {
            printPhysicalOperator((AbstractLogicalOperator) i.getOperator(), indent + 2, out);
        }

    }

    private static void appendln(StringBuilder buf, String s) {
        buf.append(s);
        buf.append("\n");
    }

    private static void pad(StringBuilder buf, int indent) {
        for (int i = 0; i < indent; ++i) {
            buf.append(' ');
        }
    }
}

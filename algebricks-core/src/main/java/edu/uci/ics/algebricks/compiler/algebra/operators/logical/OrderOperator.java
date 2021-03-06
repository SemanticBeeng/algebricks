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
import java.util.List;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.IVariableTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.OrderOperator.IOrder.OrderKind;
import edu.uci.ics.algebricks.compiler.algebra.properties.VariablePropagationPolicy;
import edu.uci.ics.algebricks.compiler.algebra.typing.ITypingContext;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalExpressionReferenceTransform;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalOperatorVisitor;
import edu.uci.ics.algebricks.utils.Pair;

public class OrderOperator extends AbstractLogicalOperator {

    public interface IOrder {
        public enum OrderKind {
            FUNCTIONCALL, ASC, DESC
        };

        public LogicalExpressionReference getExpressionRef();

        public OrderKind getKind();
    }

    public static IOrder ASC_ORDER = new IOrder() {

        @Override
        public LogicalExpressionReference getExpressionRef() {
            return null;
        }

        @Override
        public OrderKind getKind() {
            return OrderKind.ASC;
        }

    };

    public static IOrder DESC_ORDER = new IOrder() {

        @Override
        public LogicalExpressionReference getExpressionRef() {
            return null;
        }

        @Override
        public OrderKind getKind() {
            return OrderKind.DESC;
        }
    };

    public class FunOrder implements IOrder {
        private final LogicalExpressionReference f;

        public FunOrder(LogicalExpressionReference f) {
            this.f = f;
        }

        @Override
        public LogicalExpressionReference getExpressionRef() {
            return f;
        }

        @Override
        public OrderKind getKind() {
            return OrderKind.FUNCTIONCALL;
        }

    };

    private final List<Pair<IOrder, LogicalExpressionReference>> orderExpressions;

    // These are pairs of type (comparison, expr) where comparison is
    // ASC or DESC or a boolean function of arity 2 that can take as
    // arguments results of expr.

    public OrderOperator() {
        orderExpressions = new ArrayList<Pair<IOrder, LogicalExpressionReference>>();
    }

    public OrderOperator(List<Pair<IOrder, LogicalExpressionReference>> orderExpressions) {
        this.orderExpressions = orderExpressions;
    }

    @Override
    public LogicalOperatorTag getOperatorTag() {
        return LogicalOperatorTag.ORDER;
    }

    public List<Pair<IOrder, LogicalExpressionReference>> getOrderExpressions() {
        return orderExpressions;
    }

    @Override
    public void recomputeSchema() {
        schema = new ArrayList<LogicalVariable>(inputs.get(0).getOperator().getSchema());
    }

    @Override
    public VariablePropagationPolicy getVariablePropagationPolicy() {
        return VariablePropagationPolicy.ALL;
    }

    @Override
    public boolean acceptExpressionTransform(ILogicalExpressionReferenceTransform visitor) throws AlgebricksException {
        boolean b = false;
        for (Pair<IOrder, LogicalExpressionReference> p : orderExpressions) {
            if (p.first.getKind() == OrderKind.FUNCTIONCALL) {
                FunOrder fo = (FunOrder) p.first;
                LogicalExpressionReference r1 = fo.getExpressionRef();
                if (visitor.transform(r1)) {
                    b = true;
                }
            }
            if (visitor.transform(p.second)) {
                b = true;
            }
        }
        return b;
    }

    @Override
    public <R, T> R accept(ILogicalOperatorVisitor<R, T> visitor, T arg) throws AlgebricksException {
        return visitor.visitOrderOperator(this, arg);
    }

    @Override
    public boolean isMap() {
        return false;
    }

    @Override
    public IVariableTypeEnvironment computeOutputTypeEnvironment(ITypingContext ctx) throws AlgebricksException {
        return createPropagatingAllInputsTypeEnvironment(ctx);
    }
}
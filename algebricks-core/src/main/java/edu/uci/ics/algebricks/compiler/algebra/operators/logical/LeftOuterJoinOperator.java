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

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorTag;
import edu.uci.ics.algebricks.compiler.algebra.visitors.ILogicalOperatorVisitor;

public class LeftOuterJoinOperator extends AbstractBinaryJoin {

    public LeftOuterJoinOperator(LogicalExpressionReference condition) {
        super(JoinKind.LEFT_OUTER, condition);
    }

    public LeftOuterJoinOperator(LogicalExpressionReference condition, LogicalOperatorReference input1,
            LogicalOperatorReference input2) {
        super(JoinKind.LEFT_OUTER, condition, input1, input2);
    }

    @Override
    public LogicalOperatorTag getOperatorTag() {
        return LogicalOperatorTag.LEFTOUTERJOIN;
    }

    @Override
    public <R, T> R accept(ILogicalOperatorVisitor<R, T> visitor, T arg) throws AlgebricksException {
        return visitor.visitLeftOuterJoinOperator(this, arg);
    }

}

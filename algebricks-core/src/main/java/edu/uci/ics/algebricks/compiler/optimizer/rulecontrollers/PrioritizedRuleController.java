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
package edu.uci.ics.algebricks.compiler.optimizer.rulecontrollers;

import java.util.Collection;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.optimizer.base.AbstractRuleController;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;

/**
 * 
 * Runs each rule until it produces no changes. Then the whole collection of
 * rules is run again until no change is made.
 * 
 * 
 * @author Nicola
 * 
 */

public class PrioritizedRuleController extends AbstractRuleController {

    public PrioritizedRuleController() {
        super();
    }

    @Override
    public boolean rewriteWithRuleCollection(LogicalOperatorReference root, Collection<IAlgebraicRewriteRule> rules)
            throws AlgebricksException {
        boolean anyRuleFired = false;
        boolean anyChange = false;
        do {
            anyChange = false;
            for (IAlgebraicRewriteRule r : rules) {
                while (true) {
                    boolean ruleFired = rewriteOperatorRef(root, r);
                    if (ruleFired) {
                        anyChange = true;
                        anyRuleFired = true;
                    } else {
                        break; // go to next rule
                    }
                }
            }
        } while (anyChange);
        return anyRuleFired;
    }
}

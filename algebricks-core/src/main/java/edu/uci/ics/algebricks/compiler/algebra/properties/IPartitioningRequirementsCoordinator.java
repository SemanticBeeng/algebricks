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
package edu.uci.ics.algebricks.compiler.algebra.properties;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.exceptions.NotImplementedException;
import edu.uci.ics.algebricks.compiler.algebra.base.EquivalenceClass;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.optimizer.base.IOptimizationContext;
import edu.uci.ics.algebricks.utils.Pair;

/**
 * 
 * Implements constraints in between requirements for the children of the same
 * operator.
 * 
 */

public interface IPartitioningRequirementsCoordinator {

    public static IPartitioningRequirementsCoordinator NO_COORDINATION = new IPartitioningRequirementsCoordinator() {

        @Override
        public Pair<Boolean, IPartitioningProperty> coordinateRequirements(IPartitioningProperty requirements,
                IPartitioningProperty firstDeliveredPartitioning, ILogicalOperator op, IOptimizationContext context) {
            return new Pair<Boolean, IPartitioningProperty>(true, requirements);
        }
    };

    public static IPartitioningRequirementsCoordinator EQCLASS_PARTITIONING_COORDINATOR = new IPartitioningRequirementsCoordinator() {

        @Override
        public Pair<Boolean, IPartitioningProperty> coordinateRequirements(IPartitioningProperty rqdpp,
                IPartitioningProperty firstDeliveredPartitioning, ILogicalOperator op, IOptimizationContext context)
                throws AlgebricksException {
            if (firstDeliveredPartitioning != null
                    && firstDeliveredPartitioning.getPartitioningType() == rqdpp.getPartitioningType()) {
                switch (rqdpp.getPartitioningType()) {
                    case UNORDERED_PARTITIONED: {
                        UnorderedPartitionedProperty upp1 = (UnorderedPartitionedProperty) firstDeliveredPartitioning;
                        Set<LogicalVariable> set1 = upp1.getColumnSet();
                        UnorderedPartitionedProperty uppreq = (UnorderedPartitionedProperty) rqdpp;
                        Set<LogicalVariable> modifuppreq = new HashSet<LogicalVariable>();
                        Map<LogicalVariable, EquivalenceClass> eqmap = context.getEquivalenceClassMap(op);
                        Set<LogicalVariable> covered = new HashSet<LogicalVariable>();
                        for (LogicalVariable r : uppreq.getColumnSet()) {
                            EquivalenceClass ec = eqmap.get(r);
                            for (LogicalVariable v : set1) {
                                EquivalenceClass ecFirst = eqmap.get(v);
                                if (ecFirst == ec) {
                                    covered.add(v);
                                    modifuppreq.add(r);
                                    break;
                                }
                            }
                        }
                        if (!covered.equals(set1)) {
                            throw new AlgebricksException("Could not modify " + rqdpp
                                    + " to agree with partitioning property " + firstDeliveredPartitioning
                                    + " delivered by previous input operator.");
                        }
                        UnorderedPartitionedProperty upp2 = new UnorderedPartitionedProperty(modifuppreq,
                                rqdpp.getNodeDomain());
                        return new Pair<Boolean, IPartitioningProperty>(false, upp2);
                    }
                    case ORDERED_PARTITIONED: {
                        throw new NotImplementedException();
                    }
                }
            }
            return new Pair<Boolean, IPartitioningProperty>(true, rqdpp);
        }

    };

    public Pair<Boolean, IPartitioningProperty> coordinateRequirements(IPartitioningProperty requirements,
            IPartitioningProperty firstDeliveredPartitioning, ILogicalOperator op, IOptimizationContext context)
            throws AlgebricksException;
}

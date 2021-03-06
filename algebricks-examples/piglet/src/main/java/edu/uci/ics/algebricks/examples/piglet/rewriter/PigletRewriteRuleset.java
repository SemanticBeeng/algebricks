package edu.uci.ics.algebricks.examples.piglet.rewriter;

import java.util.LinkedList;
import java.util.List;

import edu.uci.ics.algebricks.compiler.optimizer.base.HeuristicOptimizer;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.BreakSelectIntoConjunctsRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.ComplexJoinInferenceRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.ConsolidateAssignsRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.ConsolidateSelectsRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.EliminateSubplanRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.EnforceStructuralPropertiesRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.ExtractCommonOperatorsRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.ExtractGbyExpressionsRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.FactorRedundantGroupAndDecorVarsRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.InferTypesRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.InlineVariablesRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.IntroduceGroupByForStandaloneAggregRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.IsolateHyracksOperatorsRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.PullSelectOutOfEqJoin;
import edu.uci.ics.algebricks.compiler.optimizer.rules.PushLimitDownRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.PushProjectDownRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.PushProjectIntoDataSourceScanRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.PushSelectDownRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.PushSelectIntoJoinRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.ReinferAllTypesRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.RemoveUnusedAssignAndAggregateRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.SetAlgebricksPhysicalOperatorsRule;
import edu.uci.ics.algebricks.compiler.optimizer.rules.SetExecutionModeRule;

public class PigletRewriteRuleset {

    public final static List<IAlgebraicRewriteRule> buildTypeInferenceRuleCollection() {
        List<IAlgebraicRewriteRule> typeInfer = new LinkedList<IAlgebraicRewriteRule>();
        typeInfer.add(new InferTypesRule());
        return typeInfer;
    }

    public final static List<IAlgebraicRewriteRule> buildNormalizationRuleCollection() {
        List<IAlgebraicRewriteRule> normalization = new LinkedList<IAlgebraicRewriteRule>();
        normalization.add(new EliminateSubplanRule());
        normalization.add(new IntroduceGroupByForStandaloneAggregRule());
        normalization.add(new BreakSelectIntoConjunctsRule());
        normalization.add(new PushSelectIntoJoinRule());
        normalization.add(new ExtractGbyExpressionsRule());
        return normalization;
    }

    public final static List<IAlgebraicRewriteRule> buildCondPushDownRuleCollection() {
        List<IAlgebraicRewriteRule> condPushDown = new LinkedList<IAlgebraicRewriteRule>();
        condPushDown.add(new PushSelectDownRule());
        condPushDown.add(new InlineVariablesRule());
        condPushDown.add(new FactorRedundantGroupAndDecorVarsRule());
        condPushDown.add(new EliminateSubplanRule());
        return condPushDown;
    }

    public final static List<IAlgebraicRewriteRule> buildJoinInferenceRuleCollection() {
        List<IAlgebraicRewriteRule> joinInference = new LinkedList<IAlgebraicRewriteRule>();
        joinInference.add(new InlineVariablesRule());
        joinInference.add(new ComplexJoinInferenceRule());
        return joinInference;
    }

    public final static List<IAlgebraicRewriteRule> buildOpPushDownRuleCollection() {
        List<IAlgebraicRewriteRule> opPushDown = new LinkedList<IAlgebraicRewriteRule>();
        opPushDown.add(new PushProjectDownRule());
        opPushDown.add(new PushSelectDownRule());
        return opPushDown;
    }

    public final static List<IAlgebraicRewriteRule> buildDataExchangeRuleCollection() {
        List<IAlgebraicRewriteRule> dataExchange = new LinkedList<IAlgebraicRewriteRule>();
        dataExchange.add(new SetExecutionModeRule());
        return dataExchange;
    }

    public final static List<IAlgebraicRewriteRule> buildConsolidationRuleCollection() {
        List<IAlgebraicRewriteRule> consolidation = new LinkedList<IAlgebraicRewriteRule>();
        consolidation.add(new ConsolidateSelectsRule());
        consolidation.add(new ConsolidateAssignsRule());
        consolidation.add(new RemoveUnusedAssignAndAggregateRule());
        return consolidation;
    }

    public final static List<IAlgebraicRewriteRule> buildPhysicalRewritesAllLevelsRuleCollection() {
        List<IAlgebraicRewriteRule> physicalPlanRewrites = new LinkedList<IAlgebraicRewriteRule>();
        physicalPlanRewrites.add(new PullSelectOutOfEqJoin());
        physicalPlanRewrites.add(new SetAlgebricksPhysicalOperatorsRule());
        physicalPlanRewrites.add(new EnforceStructuralPropertiesRule());
        physicalPlanRewrites.add(new PushProjectDownRule());
        physicalPlanRewrites.add(new PushLimitDownRule());
        return physicalPlanRewrites;
    }

    public final static List<IAlgebraicRewriteRule> buildPhysicalRewritesTopLevelRuleCollection() {
        List<IAlgebraicRewriteRule> physicalPlanRewrites = new LinkedList<IAlgebraicRewriteRule>();
        physicalPlanRewrites.add(new PushLimitDownRule());
        return physicalPlanRewrites;
    }

    
    public final static List<IAlgebraicRewriteRule> prepareForJobGenRuleCollection() {
        List<IAlgebraicRewriteRule> prepareForJobGenRewrites = new LinkedList<IAlgebraicRewriteRule>();
        prepareForJobGenRewrites.add(new IsolateHyracksOperatorsRule(
                HeuristicOptimizer.hyraxOperatorsBelowWhichJobGenIsDisabled));
        prepareForJobGenRewrites.add(new ExtractCommonOperatorsRule());
        // Re-infer all types, so that, e.g., the effect of not-is-null is
        // propagated.
        prepareForJobGenRewrites.add(new PushProjectIntoDataSourceScanRule());
        prepareForJobGenRewrites.add(new ReinferAllTypesRule());
        return prepareForJobGenRewrites;
    }

}
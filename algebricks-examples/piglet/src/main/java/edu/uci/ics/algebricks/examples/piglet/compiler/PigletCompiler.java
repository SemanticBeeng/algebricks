package edu.uci.ics.algebricks.examples.piglet.compiler;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.uci.ics.algebricks.api.compiler.HeuristicCompilerFactoryBuilder;
import edu.uci.ics.algebricks.api.compiler.ICompiler;
import edu.uci.ics.algebricks.api.compiler.ICompilerFactory;
import edu.uci.ics.algebricks.api.data.ISerializerDeserializerProvider;
import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.api.expr.IExpressionTypeComputer;
import edu.uci.ics.algebricks.api.expr.IVariableTypeEnvironment;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalExpression;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalOperator;
import edu.uci.ics.algebricks.compiler.algebra.base.ILogicalPlan;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalExpressionReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalOperatorReference;
import edu.uci.ics.algebricks.compiler.algebra.base.LogicalVariable;
import edu.uci.ics.algebricks.compiler.algebra.expressions.ConstantExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.ScalarFunctionCallExpression;
import edu.uci.ics.algebricks.compiler.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.algebricks.compiler.algebra.functions.AlgebricksBuiltinFunctions;
import edu.uci.ics.algebricks.compiler.algebra.functions.IFunctionInfo;
import edu.uci.ics.algebricks.compiler.algebra.metadata.IMetadataProvider;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.AssignOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.DataSourceScanOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.EmptyTupleSourceOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.SelectOperator;
import edu.uci.ics.algebricks.compiler.algebra.operators.logical.WriteOperator;
import edu.uci.ics.algebricks.compiler.algebra.plan.ALogicalPlanImpl;
import edu.uci.ics.algebricks.compiler.algebra.prettyprint.LogicalOperatorPrettyPrintVisitor;
import edu.uci.ics.algebricks.compiler.algebra.prettyprint.PlanPrettyPrinter;
import edu.uci.ics.algebricks.compiler.optimizer.base.AbstractRuleController;
import edu.uci.ics.algebricks.compiler.optimizer.base.IAlgebraicRewriteRule;
import edu.uci.ics.algebricks.compiler.optimizer.rulecontrollers.SequentialFixpointRuleController;
import edu.uci.ics.algebricks.compiler.optimizer.rulecontrollers.SequentialOnceRuleController;
import edu.uci.ics.algebricks.examples.piglet.ast.ASTNode;
import edu.uci.ics.algebricks.examples.piglet.ast.AssignmentNode;
import edu.uci.ics.algebricks.examples.piglet.ast.DumpNode;
import edu.uci.ics.algebricks.examples.piglet.ast.ExpressionNode;
import edu.uci.ics.algebricks.examples.piglet.ast.FieldAccessExpressionNode;
import edu.uci.ics.algebricks.examples.piglet.ast.FilterNode;
import edu.uci.ics.algebricks.examples.piglet.ast.FunctionTag;
import edu.uci.ics.algebricks.examples.piglet.ast.LiteralExpressionNode;
import edu.uci.ics.algebricks.examples.piglet.ast.LoadNode;
import edu.uci.ics.algebricks.examples.piglet.ast.RelationNode;
import edu.uci.ics.algebricks.examples.piglet.ast.ScalarFunctionExpressionNode;
import edu.uci.ics.algebricks.examples.piglet.exceptions.PigletException;
import edu.uci.ics.algebricks.examples.piglet.metadata.PigletFileDataSink;
import edu.uci.ics.algebricks.examples.piglet.metadata.PigletFileDataSource;
import edu.uci.ics.algebricks.examples.piglet.metadata.PigletMetadataProvider;
import edu.uci.ics.algebricks.examples.piglet.parser.ParseException;
import edu.uci.ics.algebricks.examples.piglet.parser.PigletParser;
import edu.uci.ics.algebricks.examples.piglet.rewriter.PigletRewriteRuleset;
import edu.uci.ics.algebricks.examples.piglet.runtime.PigletExpressionJobGen;
import edu.uci.ics.algebricks.examples.piglet.types.Schema;
import edu.uci.ics.algebricks.examples.piglet.types.Type;
import edu.uci.ics.algebricks.utils.Pair;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.job.JobSpecification;

public class PigletCompiler {
    private static final Logger LOGGER = Logger.getLogger(PigletCompiler.class.getName());

    private static List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> buildDefaultLogicalRewrites() {
        List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> defaultLogicalRewrites = new ArrayList<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>>();
        SequentialFixpointRuleController seqCtrlNoDfs = new SequentialFixpointRuleController(false);
        SequentialFixpointRuleController seqCtrlFullDfs = new SequentialFixpointRuleController(true);
        SequentialOnceRuleController seqOnceCtrl = new SequentialOnceRuleController(true);
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrl,
                PigletRewriteRuleset.buildTypeInferenceRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlFullDfs,
                PigletRewriteRuleset.buildNormalizationRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                PigletRewriteRuleset.buildCondPushDownRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                PigletRewriteRuleset.buildJoinInferenceRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                PigletRewriteRuleset.buildOpPushDownRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrl,
                PigletRewriteRuleset.buildDataExchangeRuleCollection()));
        defaultLogicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqCtrlNoDfs,
                PigletRewriteRuleset.buildConsolidationRuleCollection()));
        return defaultLogicalRewrites;
    }

    private static List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> buildDefaultPhysicalRewrites() {
        List<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>> defaultPhysicalRewrites = new ArrayList<Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>>();
        SequentialOnceRuleController seqOnceCtrlAllLevels = new SequentialOnceRuleController(true);
        SequentialOnceRuleController seqOnceCtrlTopLevel = new SequentialOnceRuleController(false);
        defaultPhysicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrlAllLevels,
                PigletRewriteRuleset.buildPhysicalRewritesAllLevelsRuleCollection()));
        defaultPhysicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrlTopLevel,
                PigletRewriteRuleset.buildPhysicalRewritesTopLevelRuleCollection()));
        defaultPhysicalRewrites.add(new Pair<AbstractRuleController, List<IAlgebraicRewriteRule>>(seqOnceCtrlAllLevels,
                PigletRewriteRuleset.prepareForJobGenRuleCollection()));
        return defaultPhysicalRewrites;
    }

    private final ICompilerFactory cFactory;

    private final PigletMetadataProvider metadataProvider;

    private int varCounter;

    private ILogicalOperator previousOp;

    public PigletCompiler() {
        HeuristicCompilerFactoryBuilder builder = new HeuristicCompilerFactoryBuilder();
        builder.setLogicalRewrites(buildDefaultLogicalRewrites());
        builder.setPhysicalRewrites(buildDefaultPhysicalRewrites());
        builder.setSerializerDeserializerProvider(new ISerializerDeserializerProvider() {
            @SuppressWarnings("unchecked")
            @Override
            public ISerializerDeserializer getSerializerDeserializer(Object type) throws AlgebricksException {
                return null;
            }
        });
        builder.setPrinterProvider(PigletPrinterFactoryProvider.INSTANCE);
        builder.setExprJobGen(new PigletExpressionJobGen());
        builder.setExpressionTypeComputer(new IExpressionTypeComputer() {
            @Override
            public Object getType(ILogicalExpression expr, IMetadataProvider<?, ?> metadataProvider,
                    IVariableTypeEnvironment env) throws AlgebricksException {
                return null;
            }
        });
        cFactory = builder.create();
        metadataProvider = new PigletMetadataProvider();
    }

    public List<ASTNode> parse(Reader in) throws ParseException {
        PigletParser parser = new PigletParser(in);
        List<ASTNode> statements = parser.Statements();
        return statements;
    }

    public JobSpecification compile(List<ASTNode> ast) throws AlgebricksException, PigletException {
        ILogicalPlan plan = translate(ast);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Translated Plan:");
            LOGGER.info(getPrettyPrintedPlan(plan));
        }
        ICompiler compiler = cFactory.createCompiler(plan, metadataProvider, varCounter);
        compiler.optimize();
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info("Optimized Plan:");
            LOGGER.info(getPrettyPrintedPlan(plan));
        }
        return compiler.createJob(null);
    }

    private ILogicalPlan translate(List<ASTNode> ast) throws PigletException {
        Map<String, Relation> symMap = new HashMap<String, Relation>();
        List<LogicalOperatorReference> roots = new ArrayList<LogicalOperatorReference>();
        previousOp = null;
        for (ASTNode an : ast) {
            switch (an.getTag()) {
                case DUMP: {
                    DumpNode dn = (DumpNode) an;
                    Relation input = symMap.get(dn.getAlias());
                    List<LogicalExpressionReference> expressions = new ArrayList<LogicalExpressionReference>();
                    for (LogicalVariable v : input.schema.values()) {
                        expressions.add(new LogicalExpressionReference(new VariableReferenceExpression(v)));
                    }
                    PigletFileDataSink dataSink = new PigletFileDataSink(dn.getFile());
                    ILogicalOperator op = new WriteOperator(expressions, dataSink);
                    op.getInputs().add(new LogicalOperatorReference(input.op));
                    roots.add(new LogicalOperatorReference(op));
                }
                    break;

                case ASSIGNMENT: {
                    AssignmentNode asn = (AssignmentNode) an;
                    String alias = asn.getAlias();
                    RelationNode rn = asn.getRelation();
                    Relation rel = translate(rn, symMap);
                    previousOp = rel.op;
                    rel.alias = alias;
                    symMap.put(alias, rel);
                }
                    break;
            }
        }
        return new ALogicalPlanImpl(roots);
    }

    private Relation translate(RelationNode rn, Map<String, Relation> symMap) throws PigletException {
        switch (rn.getTag()) {
            case LOAD: {
                LoadNode ln = (LoadNode) rn;
                String file = ln.getDataFile();
                Schema schema = ln.getSchema();
                List<Pair<String, Type>> fieldsSchema = schema.getSchema();
                List<LogicalVariable> variables = new ArrayList<LogicalVariable>();
                List<Object> types = new ArrayList<Object>();
                Relation rel = new Relation();
                for (Pair<String, Type> p : fieldsSchema) {
                    LogicalVariable v = newVariable();
                    rel.schema.put(p.first, v);
                    variables.add(v);
                    types.add(p.second);
                }
                PigletFileDataSource ds = new PigletFileDataSource(file, types.toArray());
                rel.op = new DataSourceScanOperator(variables, ds);
                rel.op.getInputs().add(
                        new LogicalOperatorReference(previousOp == null ? new EmptyTupleSourceOperator() : previousOp));
                return rel;
            }

            case FILTER: {
                FilterNode fn = (FilterNode) rn;
                String alias = fn.getAlias();
                ExpressionNode conditionNode = fn.getExpression();
                Relation inputRel = findInputRelation(alias, symMap);
                Pair<Relation, LogicalVariable> tempInput = translateScalarExpression(inputRel, conditionNode);
                Relation rel = new Relation();
                rel.op = new SelectOperator(new LogicalExpressionReference(new VariableReferenceExpression(
                        tempInput.second)));
                rel.op.getInputs().add(new LogicalOperatorReference(tempInput.first.op));
                rel.schema.putAll(tempInput.first.schema);
                return rel;
            }
        }
        throw new IllegalArgumentException("Unknown node: " + rn.getTag() + " encountered");
    }

    private Pair<Relation, LogicalVariable> translateScalarExpression(Relation inputRel, ExpressionNode expressionNode)
            throws PigletException {
        switch (expressionNode.getTag()) {
            case FIELD_ACCESS: {
                FieldAccessExpressionNode faen = (FieldAccessExpressionNode) expressionNode;
                String fieldName = faen.getFieldName();
                LogicalVariable lVar = findField(fieldName, inputRel.schema);
                return new Pair<Relation, LogicalVariable>(inputRel, lVar);
            }

            case LITERAL: {
                LiteralExpressionNode len = (LiteralExpressionNode) expressionNode;
                String image = len.getImage();
                Type type = len.getType();
                ConstantExpression ce = new ConstantExpression(new ConstantValue(type, image));
                Relation rel = new Relation();
                LogicalVariable var = newVariable();
                List<LogicalVariable> vars = new ArrayList<LogicalVariable>();
                vars.add(var);

                List<LogicalExpressionReference> exprs = new ArrayList<LogicalExpressionReference>();
                exprs.add(new LogicalExpressionReference(ce));

                rel.op = new AssignOperator(vars, exprs);
                rel.op.getInputs().add(new LogicalOperatorReference(inputRel.op));
                rel.schema.putAll(inputRel.schema);

                return new Pair<Relation, LogicalVariable>(rel, var);
            }

            case SCALAR_FUNCTION: {
                ScalarFunctionExpressionNode sfen = (ScalarFunctionExpressionNode) expressionNode;
                List<LogicalExpressionReference> argExprs = new ArrayList<LogicalExpressionReference>();
                List<ASTNode> arguments = sfen.getArguments();
                Relation rel = inputRel;
                for (ASTNode a : arguments) {
                    Pair<Relation, LogicalVariable> argPair = translateScalarExpression(rel, (ExpressionNode) a);
                    rel = argPair.first;
                    argExprs.add(new LogicalExpressionReference(new VariableReferenceExpression(argPair.second)));
                }
                Relation outRel = new Relation();
                outRel.schema.putAll(rel.schema);
                LogicalVariable var = newVariable();
                List<LogicalVariable> vars = new ArrayList<LogicalVariable>();
                vars.add(var);

                IFunctionInfo fInfo = lookupFunction(sfen.getFunctionTag(), sfen.getFunctionName());

                List<LogicalExpressionReference> exprs = new ArrayList<LogicalExpressionReference>();
                exprs.add(new LogicalExpressionReference(new ScalarFunctionCallExpression(fInfo, argExprs)));
                outRel.op = new AssignOperator(vars, exprs);
                outRel.op.getInputs().add(new LogicalOperatorReference(rel.op));
                return new Pair<Relation, LogicalVariable>(outRel, var);
            }
        }
        return null;
    }

    private IFunctionInfo lookupFunction(FunctionTag functionTag, String functionName) throws PigletException {
        switch (functionTag) {
            case EQ:
                return AlgebricksBuiltinFunctions.getBuiltinFunctionInfo(AlgebricksBuiltinFunctions.EQ);

            case NEQ:
                return AlgebricksBuiltinFunctions.getBuiltinFunctionInfo(AlgebricksBuiltinFunctions.NEQ);

            case LT:
                return AlgebricksBuiltinFunctions.getBuiltinFunctionInfo(AlgebricksBuiltinFunctions.LT);

            case LTE:
                return AlgebricksBuiltinFunctions.getBuiltinFunctionInfo(AlgebricksBuiltinFunctions.LE);

            case GT:
                return AlgebricksBuiltinFunctions.getBuiltinFunctionInfo(AlgebricksBuiltinFunctions.GT);

            case GTE:
                return AlgebricksBuiltinFunctions.getBuiltinFunctionInfo(AlgebricksBuiltinFunctions.GE);
        }
        throw new PigletException("Unsupported function: " + functionTag);
    }

    private LogicalVariable newVariable() {
        return new LogicalVariable(varCounter++);
    }

    private LogicalVariable findField(String fieldName, Map<String, LogicalVariable> schema) throws PigletException {
        LogicalVariable var = schema.get(fieldName);
        if (var == null) {
            throw new PigletException("Unable to find field named: " + fieldName);
        }
        return var;
    }

    private Relation findInputRelation(String alias, Map<String, Relation> symMap) throws PigletException {
        Relation rel = symMap.get(alias);
        if (rel == null) {
            throw new PigletException("Unknown alias " + alias + "referenced");
        }
        return rel;
    }

    private static class Relation {
        String alias;
        ILogicalOperator op;
        final Map<String, LogicalVariable> schema;

        public Relation() {
            schema = new LinkedHashMap<String, LogicalVariable>();
        }
    }

    private String getPrettyPrintedPlan(ILogicalPlan plan) throws AlgebricksException {
        LogicalOperatorPrettyPrintVisitor v = new LogicalOperatorPrettyPrintVisitor();
        StringBuilder buffer = new StringBuilder();
        PlanPrettyPrinter.printPlan(plan, buffer, v, 0);
        return buffer.toString();
    }
}
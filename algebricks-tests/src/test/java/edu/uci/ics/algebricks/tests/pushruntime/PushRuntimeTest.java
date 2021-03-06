package edu.uci.ics.algebricks.tests.pushruntime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import edu.uci.ics.algebricks.api.data.IPrinterFactory;
import edu.uci.ics.algebricks.runtime.hyracks.aggregators.TupleCountAggregateFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.aggregators.TupleCountRunningAggregateFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.base.AlgebricksPipeline;
import edu.uci.ics.algebricks.runtime.hyracks.base.IAggregateFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.base.IEvaluatorFactory;
import edu.uci.ics.algebricks.runtime.hyracks.base.IPushRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.base.IRunningAggregateFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.base.IUnnestingFunctionFactory;
import edu.uci.ics.algebricks.runtime.hyracks.evaluators.ColumnAccessEvalFactory;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.data.BinaryBooleanInspectorImpl;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.data.BinaryIntegerInspectorImpl;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.data.IntegerPrinterFactory;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.data.NoopNullWriterFactory;
import edu.uci.ics.algebricks.runtime.hyracks.jobgen.data.UTF8StringPrinterFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.aggreg.AggregateRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.aggreg.NestedPlansAccumulatingAggregatorFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.aggreg.SimpleAlgebricksAccumulatingAggregatorFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.group.MicroPreClusteredGroupRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.meta.AlgebricksMetaOperatorDescriptor;
import edu.uci.ics.algebricks.runtime.hyracks.operators.meta.SubplanRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.sort.InMemorySortRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.AssignRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.EmptyTupleSourceRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.NestedTupleSourceRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.PrinterRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.RunningAggregateRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.SinkWriterRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.StreamLimitRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.StreamProjectRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.StreamSelectRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.StringStreamingRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.operators.std.UnnestRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.writers.PrinterBasedWriterFactory;
import edu.uci.ics.algebricks.tests.util.AlgebricksHyracksIntegrationUtil;
import edu.uci.ics.hyracks.api.constraints.PartitionConstraintHelper;
import edu.uci.ics.hyracks.api.dataflow.IOperatorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.api.dataflow.value.INullWriterFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.ITuplePartitionComputerFactory;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.common.data.comparators.IntegerBinaryComparatorFactory;
import edu.uci.ics.hyracks.dataflow.common.data.comparators.UTF8StringBinaryComparatorFactory;
import edu.uci.ics.hyracks.dataflow.common.data.hash.IntegerBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.FloatSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.FloatParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IValueParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IntegerParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.UTF8StringParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.partition.FieldHashPartitionComputerFactory;
import edu.uci.ics.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.ConstantFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.DelimitedDataTupleParserFactory;
import edu.uci.ics.hyracks.dataflow.std.file.FileScanOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.FileSplit;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.LineFileWriteOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.group.HashGroupOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.group.IAccumulatingAggregatorFactory;
import edu.uci.ics.hyracks.dataflow.std.group.PreclusteredGroupOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.misc.SplitOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.sort.InMemorySortOperatorDescriptor;

public class PushRuntimeTest {

    private static final String SEPARATOR = System.getProperty("file.separator");
    private static final String PATH_ACTUAL = "rttest";
    private static final String PATH_BASE = "src" + SEPARATOR + "test" + SEPARATOR + "resources";
    private static final String PATH_EXPECTED = PATH_BASE + SEPARATOR + "results";

    private static final String[] DEFAULT_NODES = new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID };

    @BeforeClass
    public static void setUp() throws Exception {
        File outdir = new File(PATH_ACTUAL);
        outdir.mkdirs();
        AlgebricksHyracksIntegrationUtil.init();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        AlgebricksHyracksIntegrationUtil.deinit();
        File outdir = new File(PATH_ACTUAL);
        File[] files = outdir.listFiles();
        if (files == null || files.length == 0) {
            outdir.delete();
        }
    }

    @Test
    public void etsAssignPrint() throws Exception {
        JobSpecification spec = new JobSpecification();
        IntegerConstantEvalFactory const1 = new IntegerConstantEvalFactory(400);
        IntegerConstantEvalFactory const2 = new IntegerConstantEvalFactory(3);

        EmptyTupleSourceRuntimeFactory ets = new EmptyTupleSourceRuntimeFactory();
        RecordDescriptor etsDesc = new RecordDescriptor(new ISerializerDeserializer[] {});
        AssignRuntimeFactory assign = new AssignRuntimeFactory(new int[] { 0, 1 }, new IEvaluatorFactory[] { const1,
                const2 }, new int[] { 0, 1 });
        RecordDescriptor assignDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });

        PrinterRuntimeFactory printer = new PrinterRuntimeFactory(new int[] { 0, 1 }, new IPrinterFactory[] {
                IntegerPrinterFactory.INSTANCE, IntegerPrinterFactory.INSTANCE }, assignDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 0, 0,
                new IPushRuntimeFactory[] { ets, assign, printer },
                new RecordDescriptor[] { etsDesc, assignDesc, null });
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp, DEFAULT_NODES);
        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);
    }

    @Test
    public void etsAssignWrite() throws Exception {
        JobSpecification spec = new JobSpecification();
        IntegerConstantEvalFactory const1 = new IntegerConstantEvalFactory(400);
        IntegerConstantEvalFactory const2 = new IntegerConstantEvalFactory(3);

        EmptyTupleSourceRuntimeFactory ets = new EmptyTupleSourceRuntimeFactory();
        RecordDescriptor etsDesc = new RecordDescriptor(new ISerializerDeserializer[] {});
        AssignRuntimeFactory assign = new AssignRuntimeFactory(new int[] { 0, 1 }, new IEvaluatorFactory[] { const1,
                const2 }, new int[] { 0, 1 });
        RecordDescriptor assignDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "etsAssignWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0, 1 }, new IPrinterFactory[] {
                IntegerPrinterFactory.INSTANCE, IntegerPrinterFactory.INSTANCE }, outFile,
                PrinterBasedWriterFactory.INSTANCE, assignDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 0, 0,
                new IPushRuntimeFactory[] { ets, assign, writer }, new RecordDescriptor[] { etsDesc, assignDesc, null });
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp, DEFAULT_NODES);
        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("400; 3", buf.toString());
        outFile.delete();
    }

    @Test
    public void scanSelectWrite() throws Exception {
        JobSpecification spec = new JobSpecification();

        // the scanner
        FileSplit[] intFileSplits = new FileSplit[1];
        intFileSplits[0] = new FileSplit(AlgebricksHyracksIntegrationUtil.NC1_ID, new FileReference(new File(
                "data/simple/int-part1.tbl")));
        IFileSplitProvider intSplitProvider = new ConstantFileSplitProvider(intFileSplits);
        RecordDescriptor intScannerDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });
        IValueParserFactory[] valueParsers = new IValueParserFactory[] { IntegerParserFactory.INSTANCE };
        FileScanOperatorDescriptor intScanner = new FileScanOperatorDescriptor(spec, intSplitProvider,
                new DelimitedDataTupleParserFactory(valueParsers, '|'), intScannerDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, intScanner, DEFAULT_NODES);

        // the algebricks op.
        IEvaluatorFactory cond = new IntegerGreaterThanEvalFactory(new IntegerConstantEvalFactory(2),
                new ColumnAccessEvalFactory(0));
        StreamSelectRuntimeFactory select = new StreamSelectRuntimeFactory(cond, new int[] { 0 },
                BinaryBooleanInspectorImpl.INSTANCE);
        RecordDescriptor selectDesc = intScannerDesc;

        String filePath = PATH_ACTUAL + SEPARATOR + "scanSelectWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                selectDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 1, 0,
                new IPushRuntimeFactory[] { select, writer }, new RecordDescriptor[] { selectDesc, null });

        PartitionConstraintHelper.addPartitionCountConstraint(spec, algebricksOp, 1);

        spec.connect(new OneToOneConnectorDescriptor(spec), intScanner, 0, algebricksOp, 0);

        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("0", buf.toString());
        outFile.delete();
    }

    @Test
    public void etsAssignProjectWrite() throws Exception {

        JobSpecification spec = new JobSpecification();
        IntegerConstantEvalFactory const1 = new IntegerConstantEvalFactory(400);
        IntegerConstantEvalFactory const2 = new IntegerConstantEvalFactory(3);

        EmptyTupleSourceRuntimeFactory ets = new EmptyTupleSourceRuntimeFactory();
        RecordDescriptor etsDesc = new RecordDescriptor(new ISerializerDeserializer[] {});
        AssignRuntimeFactory assign = new AssignRuntimeFactory(new int[] { 0, 1 }, new IEvaluatorFactory[] { const1,
                const2 }, new int[] { 0, 1 });
        RecordDescriptor assignDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        StreamProjectRuntimeFactory project = new StreamProjectRuntimeFactory(new int[] { 1 });
        RecordDescriptor projectDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "etsAssignProjectWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                projectDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 0, 0,
                new IPushRuntimeFactory[] { ets, assign, project, writer }, new RecordDescriptor[] { etsDesc,
                        assignDesc, projectDesc, null });

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp, DEFAULT_NODES);

        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("3", buf.toString());
        outFile.delete();
    }

    @Test
    public void scanLimitWrite() throws Exception {
        JobSpecification spec = new JobSpecification();

        // the scanner
        FileSplit[] fileSplits = new FileSplit[1];
        fileSplits[0] = new FileSplit(AlgebricksHyracksIntegrationUtil.NC1_ID, new FileReference(new File(
                "data/tpch0.001/customer.tbl")));
        IFileSplitProvider splitProvider = new ConstantFileSplitProvider(fileSplits);

        RecordDescriptor scannerDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, FloatSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE });
        IValueParserFactory[] valueParsers = new IValueParserFactory[] { IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE, IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, FloatParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE };
        FileScanOperatorDescriptor scanner = new FileScanOperatorDescriptor(spec, splitProvider,
                new DelimitedDataTupleParserFactory(valueParsers, '|'), scannerDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, scanner, DEFAULT_NODES);

        // the algebricks op.
        StreamLimitRuntimeFactory limit = new StreamLimitRuntimeFactory(new IntegerConstantEvalFactory(2), null,
                new int[] { 0 }, BinaryIntegerInspectorImpl.INSTANCE);
        RecordDescriptor limitDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "scanLimitWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                limitDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 1, 0,
                new IPushRuntimeFactory[] { limit, writer }, new RecordDescriptor[] { limitDesc, null });
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        spec.connect(new OneToOneConnectorDescriptor(spec), scanner, 0, algebricksOp, 0);

        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("12", buf.toString());
        outFile.delete();
    }

    @Test
    public void etsUnnestWrite() throws Exception {
        JobSpecification spec = new JobSpecification();

        EmptyTupleSourceRuntimeFactory ets = new EmptyTupleSourceRuntimeFactory();
        RecordDescriptor etsDesc = new RecordDescriptor(new ISerializerDeserializer[] {});
        IUnnestingFunctionFactory aggregFactory = new IntArrayUnnester(new int[] { 100, 200, 300 });
        UnnestRuntimeFactory unnest = new UnnestRuntimeFactory(0, aggregFactory, new int[] { 0 });
        RecordDescriptor unnestDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "etsUnnestWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                unnestDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 0, 0,
                new IPushRuntimeFactory[] { ets, unnest, writer }, new RecordDescriptor[] { etsDesc, unnestDesc, null });
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });
        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("100200300", buf.toString());
        outFile.delete();
    }

    @Test
    public void scanAggregateWrite() throws Exception {
        JobSpecification spec = new JobSpecification();

        // the scanner
        FileSplit[] fileSplits = new FileSplit[1];
        fileSplits[0] = new FileSplit(AlgebricksHyracksIntegrationUtil.NC1_ID, new FileReference(new File(
                "data/tpch0.001/customer-part1.tbl")));
        IFileSplitProvider splitProvider = new ConstantFileSplitProvider(fileSplits);
        RecordDescriptor scannerDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, FloatSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE });
        IValueParserFactory[] valueParsers = new IValueParserFactory[] { IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE, IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, FloatParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE };
        FileScanOperatorDescriptor scanner = new FileScanOperatorDescriptor(spec, splitProvider,
                new DelimitedDataTupleParserFactory(valueParsers, '|'), scannerDesc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, scanner,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        // the algebricks op.
        AggregateRuntimeFactory agg = new AggregateRuntimeFactory(
                new IAggregateFunctionFactory[] { new TupleCountAggregateFunctionFactory() });
        RecordDescriptor aggDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "scanAggregateWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                aggDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 1, 0,
                new IPushRuntimeFactory[] { agg, writer }, new RecordDescriptor[] { aggDesc, null });

        PartitionConstraintHelper.addPartitionCountConstraint(spec, algebricksOp, 1);

        spec.connect(new OneToOneConnectorDescriptor(spec), scanner, 0, algebricksOp, 0);

        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("75", buf.toString());
        outFile.delete();
    }

    @Test
    public void scanSortGbySelectWrite() throws Exception {
        JobSpecification spec = new JobSpecification();

        // the scanner
        FileSplit[] fileSplits = new FileSplit[1];
        fileSplits[0] = new FileSplit(AlgebricksHyracksIntegrationUtil.NC1_ID, new FileReference(new File(
                "data/tpch0.001/customer.tbl")));
        IFileSplitProvider splitProvider = new ConstantFileSplitProvider(fileSplits);
        RecordDescriptor scannerDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, FloatSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE });
        IValueParserFactory[] valueParsers = new IValueParserFactory[] { IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE, IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, FloatParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE };
        FileScanOperatorDescriptor scanner = new FileScanOperatorDescriptor(spec, splitProvider,
                new DelimitedDataTupleParserFactory(valueParsers, '|'), scannerDesc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, scanner,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        // the sort (by nation id)
        RecordDescriptor sortDesc = scannerDesc;
        InMemorySortOperatorDescriptor sort = new InMemorySortOperatorDescriptor(spec, new int[] { 3 },
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, sortDesc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, sort,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        // the group-by
        NestedTupleSourceRuntimeFactory nts = new NestedTupleSourceRuntimeFactory();
        RecordDescriptor ntsDesc = sortDesc;
        AggregateRuntimeFactory agg = new AggregateRuntimeFactory(
                new IAggregateFunctionFactory[] { new TupleCountAggregateFunctionFactory() });
        RecordDescriptor aggDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });
        AlgebricksPipeline pipeline = new AlgebricksPipeline(new IPushRuntimeFactory[] { nts, agg },
                new RecordDescriptor[] { ntsDesc, aggDesc });
        NestedPlansAccumulatingAggregatorFactory npaaf = new NestedPlansAccumulatingAggregatorFactory(
                new AlgebricksPipeline[] { pipeline }, new int[] { 3 }, new int[] {});
        RecordDescriptor gbyDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        PreclusteredGroupOperatorDescriptor gby = new PreclusteredGroupOperatorDescriptor(spec, new int[] { 3 },
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, npaaf, gbyDesc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, gby,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        // the algebricks op.
        IEvaluatorFactory cond = new IntegerEqualsEvalFactory(new IntegerConstantEvalFactory(3),
                new ColumnAccessEvalFactory(0)); // Canadian customers
        StreamSelectRuntimeFactory select = new StreamSelectRuntimeFactory(cond, new int[] { 1 },
                BinaryBooleanInspectorImpl.INSTANCE);
        RecordDescriptor selectDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "scanSortGbySelectWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                selectDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 1, 0,
                new IPushRuntimeFactory[] { select, writer }, new RecordDescriptor[] { selectDesc, null });

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        spec.connect(new OneToOneConnectorDescriptor(spec), scanner, 0, sort, 0);
        spec.connect(new OneToOneConnectorDescriptor(spec), sort, 0, gby, 0);
        spec.connect(new OneToOneConnectorDescriptor(spec), gby, 0, algebricksOp, 0);
        spec.addRoot(algebricksOp);

        AlgebricksHyracksIntegrationUtil.runJob(spec);
        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("9", buf.toString());
        outFile.delete();
    }

    @Test
    public void scanHashGbySelectWrite() throws Exception {
        JobSpecification spec = new JobSpecification();

        // the scanner
        FileSplit[] fileSplits = new FileSplit[1];
        fileSplits[0] = new FileSplit(AlgebricksHyracksIntegrationUtil.NC1_ID, new FileReference(new File(
                "data/tpch0.001/customer.tbl")));
        IFileSplitProvider splitProvider = new ConstantFileSplitProvider(fileSplits);
        RecordDescriptor scannerDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, FloatSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE });
        IValueParserFactory[] valueParsers = new IValueParserFactory[] { IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE, IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, FloatParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE };
        FileScanOperatorDescriptor scanner = new FileScanOperatorDescriptor(spec, splitProvider,
                new DelimitedDataTupleParserFactory(valueParsers, '|'), scannerDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, scanner,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        // the group-by
        RecordDescriptor gbyDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        ITuplePartitionComputerFactory tpcf = new FieldHashPartitionComputerFactory(new int[] { 3 },
                new IBinaryHashFunctionFactory[] { IntegerBinaryHashFunctionFactory.INSTANCE });
        IAggregateFunctionFactory[] aggFuns = new IAggregateFunctionFactory[] { new TupleCountAggregateFunctionFactory() };
        IAccumulatingAggregatorFactory aggFactory = new SimpleAlgebricksAccumulatingAggregatorFactory(aggFuns,
                new int[] { 3 }, new int[] {});
        HashGroupOperatorDescriptor gby = new HashGroupOperatorDescriptor(spec, new int[] { 3 }, tpcf,
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, aggFactory, gbyDesc, 1024);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, gby,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        // the algebricks op.
        IEvaluatorFactory cond = new IntegerEqualsEvalFactory(new IntegerConstantEvalFactory(3),
                new ColumnAccessEvalFactory(0)); // Canadian customers
        StreamSelectRuntimeFactory select = new StreamSelectRuntimeFactory(cond, new int[] { 1 },
                BinaryBooleanInspectorImpl.INSTANCE);
        RecordDescriptor selectDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "scanHashGbySelectWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                selectDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 1, 0,
                new IPushRuntimeFactory[] { select, writer }, new RecordDescriptor[] { selectDesc, null });

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        spec.connect(new OneToOneConnectorDescriptor(spec), scanner, 0, gby, 0);
        spec.connect(new OneToOneConnectorDescriptor(spec), gby, 0, algebricksOp, 0);
        spec.addRoot(algebricksOp);

        AlgebricksHyracksIntegrationUtil.runJob(spec);
        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("9", buf.toString());
        outFile.delete();
    }

    @Test
    public void etsUnnestRunningaggregateWrite() throws Exception {
        JobSpecification spec = new JobSpecification();

        EmptyTupleSourceRuntimeFactory ets = new EmptyTupleSourceRuntimeFactory();
        RecordDescriptor etsDesc = new RecordDescriptor(new ISerializerDeserializer[] {});
        IUnnestingFunctionFactory aggregFactory = new IntArrayUnnester(new int[] { 100, 200, 300 });
        UnnestRuntimeFactory unnest = new UnnestRuntimeFactory(0, aggregFactory, new int[] { 0 });
        RecordDescriptor unnestDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        RunningAggregateRuntimeFactory ragg = new RunningAggregateRuntimeFactory(new int[] { 1 },
                new IRunningAggregateFunctionFactory[] { new TupleCountRunningAggregateFunctionFactory() }, new int[] {
                        0, 1 });
        RecordDescriptor raggDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "etsUnnestRunningaggregateWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 1 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                raggDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 0, 0,
                new IPushRuntimeFactory[] { ets, unnest, ragg, writer }, new RecordDescriptor[] { etsDesc, unnestDesc,
                        raggDesc, null });

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("123", buf.toString());
        outFile.delete();
    }

    @Test
    public void etsAssignScriptWrite() throws Exception {
        JobSpecification spec = new JobSpecification();
        IntegerConstantEvalFactory const1 = new IntegerConstantEvalFactory(400);
        IntegerConstantEvalFactory const2 = new IntegerConstantEvalFactory(3);

        EmptyTupleSourceRuntimeFactory ets = new EmptyTupleSourceRuntimeFactory();
        RecordDescriptor etsDesc = new RecordDescriptor(new ISerializerDeserializer[] {});
        AssignRuntimeFactory assign = new AssignRuntimeFactory(new int[] { 0, 1 }, new IEvaluatorFactory[] { const1,
                const2 }, new int[] { 0, 1 });
        RecordDescriptor assignDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });

        IValueParserFactory[] valueParsers = { IntegerParserFactory.INSTANCE, IntegerParserFactory.INSTANCE };

        String osname = System.getProperty("os.name");
        String command;
        if (osname.equals("Linux")) {
            command = "bash target/testscripts/idscript";
        } else if (osname.startsWith("Windows")) {
            command = "target\\testscripts\\idscript.cmd";
        } else {
            // don't know how to test
            return;
        }

        StringStreamingRuntimeFactory script = new StringStreamingRuntimeFactory(command, new IPrinterFactory[] {
                IntegerPrinterFactory.INSTANCE, IntegerPrinterFactory.INSTANCE }, ' ',
                new DelimitedDataTupleParserFactory(valueParsers, ' '));
        RecordDescriptor scriptDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "etsAssignScriptWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0, 1 }, new IPrinterFactory[] {
                IntegerPrinterFactory.INSTANCE, IntegerPrinterFactory.INSTANCE }, outFile,
                PrinterBasedWriterFactory.INSTANCE, scriptDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 0, 0,
                new IPushRuntimeFactory[] { ets, assign, script, writer }, new RecordDescriptor[] { etsDesc,
                        assignDesc, scriptDesc, null });

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("400; 3", buf.toString());
        outFile.delete();
    }

    @Test
    public void scanSplitWrite() throws Exception {
        final int outputArity = 2;

        JobSpecification spec = new JobSpecification();

        String inputFileName = "data/tpch0.001/customer.tbl";
        File inputFile = new File(inputFileName);
        File[] outputFile = new File[outputArity];
        for (int i = 0; i < outputArity; i++) {
            outputFile[i] = File.createTempFile("splitop", null);
        }

        FileSplit[] inputSplits = new FileSplit[] { new FileSplit(AlgebricksHyracksIntegrationUtil.NC1_ID,
                new FileReference(inputFile)) };

        DelimitedDataTupleParserFactory stringParser = new DelimitedDataTupleParserFactory(
                new IValueParserFactory[] { UTF8StringParserFactory.INSTANCE }, '\u0000');
        RecordDescriptor stringRec = new RecordDescriptor(
                new ISerializerDeserializer[] { UTF8StringSerializerDeserializer.INSTANCE, });

        FileScanOperatorDescriptor scanOp = new FileScanOperatorDescriptor(spec, new ConstantFileSplitProvider(
                inputSplits), stringParser, stringRec);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, scanOp,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        SplitOperatorDescriptor splitOp = new SplitOperatorDescriptor(spec, stringRec, outputArity);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, splitOp,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        IOperatorDescriptor outputOp[] = new IOperatorDescriptor[outputFile.length];
        for (int i = 0; i < outputArity; i++) {
            outputOp[i] = new LineFileWriteOperatorDescriptor(spec, new FileSplit[] { new FileSplit(
                    AlgebricksHyracksIntegrationUtil.NC1_ID, new FileReference(outputFile[i])) });
            PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, outputOp[i],
                    new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });
        }

        spec.connect(new OneToOneConnectorDescriptor(spec), scanOp, 0, splitOp, 0);
        for (int i = 0; i < outputArity; i++) {
            spec.connect(new OneToOneConnectorDescriptor(spec), splitOp, i, outputOp[i], 0);
        }

        for (int i = 0; i < outputArity; i++) {
            spec.addRoot(outputOp[i]);
        }
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        for (int i = 0; i < outputArity; i++) {
            compareFiles(inputFileName, outputFile[i].getAbsolutePath());
        }
    }

    @Test
    public void scanMicroSortWrite() throws Exception {
        JobSpecification spec = new JobSpecification();

        // the scanner
        FileSplit[] fileSplits = new FileSplit[1];
        fileSplits[0] = new FileSplit(AlgebricksHyracksIntegrationUtil.NC1_ID, new FileReference(new File(
                "data/tpch0.001/nation.tbl")));
        IFileSplitProvider splitProvider = new ConstantFileSplitProvider(fileSplits);
        RecordDescriptor scannerDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                IntegerSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE });
        IValueParserFactory[] valueParsers = new IValueParserFactory[] { IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, IntegerParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE };
        FileScanOperatorDescriptor scanner = new FileScanOperatorDescriptor(spec, splitProvider,
                new DelimitedDataTupleParserFactory(valueParsers, '|'), scannerDesc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, scanner,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        // the algebricks op.
        InMemorySortRuntimeFactory sort = new InMemorySortRuntimeFactory(new int[] { 1 }, null,
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE }, null);
        RecordDescriptor sortDesc = scannerDesc;

        String fileName = "scanMicroSortWrite.out";
        String filePath = PATH_ACTUAL + SEPARATOR + fileName;
        String resultFilePath = PATH_EXPECTED + SEPARATOR + fileName;
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0, 1, 2, 3 }, new IPrinterFactory[] {
                IntegerPrinterFactory.INSTANCE, UTF8StringPrinterFactory.INSTANCE, IntegerPrinterFactory.INSTANCE,
                UTF8StringPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE, sortDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 1, 0,
                new IPushRuntimeFactory[] { sort, writer }, new RecordDescriptor[] { sortDesc, null });

        PartitionConstraintHelper.addPartitionCountConstraint(spec, algebricksOp, 1);

        spec.connect(new OneToOneConnectorDescriptor(spec), scanner, 0, algebricksOp, 0);

        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        compareFiles(filePath, resultFilePath);
        outFile.delete();
    }

    @Test
    public void etsAssignSubplanProjectWrite() throws Exception {
        JobSpecification spec = new JobSpecification();
        IntegerConstantEvalFactory const1 = new IntegerConstantEvalFactory(400);
        IntegerConstantEvalFactory const2 = new IntegerConstantEvalFactory(3);

        EmptyTupleSourceRuntimeFactory ets = new EmptyTupleSourceRuntimeFactory();
        RecordDescriptor etsDesc = new RecordDescriptor(new ISerializerDeserializer[] {});

        AssignRuntimeFactory assign1 = new AssignRuntimeFactory(new int[] { 0 }, new IEvaluatorFactory[] { const1 },
                new int[] { 0 });
        RecordDescriptor assign1Desc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        NestedTupleSourceRuntimeFactory nts = new NestedTupleSourceRuntimeFactory();

        AssignRuntimeFactory assign2 = new AssignRuntimeFactory(new int[] { 1 },
                new IEvaluatorFactory[] { new IntegerAddEvalFactory(new ColumnAccessEvalFactory(0), const2) },
                new int[] { 0, 1 });
        RecordDescriptor assign2Desc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });

        StreamProjectRuntimeFactory project1 = new StreamProjectRuntimeFactory(new int[] { 1 });
        RecordDescriptor project1Desc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        AlgebricksPipeline pipeline = new AlgebricksPipeline(new IPushRuntimeFactory[] { nts, assign2, project1 },
                new RecordDescriptor[] { assign1Desc, assign2Desc, project1Desc });

        SubplanRuntimeFactory subplan = new SubplanRuntimeFactory(pipeline,
                new INullWriterFactory[] { NoopNullWriterFactory.INSTANCE }, assign1Desc, null);

        RecordDescriptor subplanDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });

        StreamProjectRuntimeFactory project2 = new StreamProjectRuntimeFactory(new int[] { 1 });
        RecordDescriptor project2Desc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "etsAssignSubplanProjectWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                project2Desc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 0, 0,
                new IPushRuntimeFactory[] { ets, assign1, subplan, project2, writer }, new RecordDescriptor[] {
                        etsDesc, assign1Desc, subplanDesc, project2Desc, null });

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp, DEFAULT_NODES);

        spec.addRoot(algebricksOp);
        AlgebricksHyracksIntegrationUtil.runJob(spec);

        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("403", buf.toString());
        outFile.delete();
    }

    @Test
    public void scanMicroSortGbySelectWrite() throws Exception {
        JobSpecification spec = new JobSpecification();

        // the scanner
        FileSplit[] fileSplits = new FileSplit[1];
        fileSplits[0] = new FileSplit(AlgebricksHyracksIntegrationUtil.NC1_ID, new FileReference(new File(
                "data/tpch0.001/customer.tbl")));
        IFileSplitProvider splitProvider = new ConstantFileSplitProvider(fileSplits);
        RecordDescriptor scannerDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, FloatSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE });
        IValueParserFactory[] valueParsers = new IValueParserFactory[] { IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE, IntegerParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE, FloatParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                UTF8StringParserFactory.INSTANCE };
        FileScanOperatorDescriptor scanner = new FileScanOperatorDescriptor(spec, splitProvider,
                new DelimitedDataTupleParserFactory(valueParsers, '|'), scannerDesc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, scanner,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        // the sort (by nation id)
        RecordDescriptor sortDesc = scannerDesc;
        InMemorySortRuntimeFactory sort = new InMemorySortRuntimeFactory(new int[] { 3 }, null,
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, null);

        // the group-by
        NestedTupleSourceRuntimeFactory nts = new NestedTupleSourceRuntimeFactory();
        RecordDescriptor ntsDesc = sortDesc;
        AggregateRuntimeFactory agg = new AggregateRuntimeFactory(
                new IAggregateFunctionFactory[] { new TupleCountAggregateFunctionFactory() });
        RecordDescriptor aggDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });
        AlgebricksPipeline pipeline = new AlgebricksPipeline(new IPushRuntimeFactory[] { nts, agg },
                new RecordDescriptor[] { ntsDesc, aggDesc });
        NestedPlansAccumulatingAggregatorFactory npaaf = new NestedPlansAccumulatingAggregatorFactory(
                new AlgebricksPipeline[] { pipeline }, new int[] { 3 }, new int[] {});
        RecordDescriptor gbyDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        MicroPreClusteredGroupRuntimeFactory gby = new MicroPreClusteredGroupRuntimeFactory(new int[] { 3 },
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, npaaf, sortDesc, gbyDesc,
                null);

        // the algebricks op.
        IEvaluatorFactory cond = new IntegerEqualsEvalFactory(new IntegerConstantEvalFactory(3),
                new ColumnAccessEvalFactory(0)); // Canadian customers
        StreamSelectRuntimeFactory select = new StreamSelectRuntimeFactory(cond, new int[] { 1 },
                BinaryBooleanInspectorImpl.INSTANCE);
        RecordDescriptor selectDesc = new RecordDescriptor(
                new ISerializerDeserializer[] { IntegerSerializerDeserializer.INSTANCE });

        String filePath = PATH_ACTUAL + SEPARATOR + "scanSortGbySelectWrite.out";
        File outFile = new File(filePath);
        SinkWriterRuntimeFactory writer = new SinkWriterRuntimeFactory(new int[] { 0 },
                new IPrinterFactory[] { IntegerPrinterFactory.INSTANCE }, outFile, PrinterBasedWriterFactory.INSTANCE,
                selectDesc);

        AlgebricksMetaOperatorDescriptor algebricksOp = new AlgebricksMetaOperatorDescriptor(spec, 1, 0,
                new IPushRuntimeFactory[] { sort, gby, select, writer }, new RecordDescriptor[] { sortDesc, gbyDesc,
                        selectDesc, null });

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, algebricksOp,
                new String[] { AlgebricksHyracksIntegrationUtil.NC1_ID });

        spec.connect(new OneToOneConnectorDescriptor(spec), scanner, 0, algebricksOp, 0);
        spec.addRoot(algebricksOp);

        AlgebricksHyracksIntegrationUtil.runJob(spec);
        StringBuilder buf = new StringBuilder();
        readFileToString(outFile, buf);
        Assert.assertEquals("9", buf.toString());
        outFile.delete();
    }

    private static void readFileToString(File file, StringBuilder buf) throws Exception {
        BufferedReader result = new BufferedReader(new FileReader(file));
        boolean first = true;
        while (true) {
            String s = result.readLine();
            if (s == null) {
                break;
            } else {
                if (!first) {
                    first = false;
                    buf.append('\n');
                }
                buf.append(s);
            }
        }
        result.close();
    }

    public void compareFiles(String fileNameA, String fileNameB) throws IOException {
        BufferedReader fileA = new BufferedReader(new FileReader(fileNameA));
        BufferedReader fileB = new BufferedReader(new FileReader(fileNameB));

        String lineA, lineB;
        while ((lineA = fileA.readLine()) != null) {
            lineB = fileB.readLine();
            Assert.assertEquals(lineA, lineB);
        }
        Assert.assertNull(fileB.readLine());
    }

}

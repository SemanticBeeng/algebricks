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
package edu.uci.ics.algebricks.runtime.hyracks.operators.std;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import edu.uci.ics.algebricks.api.data.IAWriter;
import edu.uci.ics.algebricks.api.data.IAWriterFactory;
import edu.uci.ics.algebricks.api.data.IPrinterFactory;
import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.runtime.hyracks.base.IPushRuntime;
import edu.uci.ics.algebricks.runtime.hyracks.base.IPushRuntimeFactory;
import edu.uci.ics.algebricks.runtime.hyracks.context.RuntimeContext;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;

public class SinkWriterRuntimeFactory implements IPushRuntimeFactory {

    private static final long serialVersionUID = 1L;

    private final int[] fields;
    private final IPrinterFactory[] printerFactories;
    private final File outputFile;
    private final RecordDescriptor inputRecordDesc;
    private final IAWriterFactory writerFactory;

    public SinkWriterRuntimeFactory(int[] fields, IPrinterFactory[] printerFactories, File outputFile,
            IAWriterFactory writerFactory, RecordDescriptor inputRecordDesc) {
        this.fields = fields;
        this.printerFactories = printerFactories;
        this.outputFile = outputFile;
        this.writerFactory = writerFactory;
        this.inputRecordDesc = inputRecordDesc;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("sink-write " + "[");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                buf.append("; ");
            }
            buf.append(fields[i]);
        }
        buf.append("] outputFile");
        return buf.toString();
    }

    @Override
    public IPushRuntime createPushRuntime(RuntimeContext context) throws AlgebricksException {
        PrintStream filePrintStream = null;
        try {
            filePrintStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
        } catch (FileNotFoundException e) {
            throw new AlgebricksException(e);
        }
        IAWriter w = writerFactory.createWriter(fields, filePrintStream, printerFactories, inputRecordDesc);
        return new SinkWriterRuntime(w, context, filePrintStream, inputRecordDesc, true);
    }
}

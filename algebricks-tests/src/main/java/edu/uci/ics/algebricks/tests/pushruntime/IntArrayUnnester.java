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
package edu.uci.ics.algebricks.tests.pushruntime;

import java.io.DataOutput;
import java.io.IOException;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.algebricks.runtime.hyracks.base.IUnnestingFunction;
import edu.uci.ics.algebricks.runtime.hyracks.base.IUnnestingFunctionFactory;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class IntArrayUnnester implements IUnnestingFunctionFactory {

    private int[] x;

    public IntArrayUnnester(int[] x) {
        this.x = x;
    }

    private static final long serialVersionUID = 1L;

    @Override
    public IUnnestingFunction createUnnestingFunction(IDataOutputProvider provider) throws AlgebricksException {

        final DataOutput out = provider.getDataOutput();

        return new IUnnestingFunction() {

            private int pos;

            @Override
            public void init(IFrameTupleReference tuple) throws AlgebricksException {
                pos = 0;
            }

            @Override
            public boolean step() throws AlgebricksException {
                try {
                    if (pos < x.length) {
                        // Writes one byte to distinguish between null
                        // values and end of sequence.
                        out.writeInt(x[pos]);
                        ++pos;
                        return true;
                    } else {
                        return false;
                    }

                } catch (IOException e) {
                    throw new AlgebricksException(e);
                }
            }

        };

    }

}

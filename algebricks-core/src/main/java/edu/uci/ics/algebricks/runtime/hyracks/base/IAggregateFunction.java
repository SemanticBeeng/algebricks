package edu.uci.ics.algebricks.runtime.hyracks.base;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public interface IAggregateFunction {
    /** should be called each time a new aggregate value is computed */
    public void init() throws AlgebricksException;

    public void step(IFrameTupleReference tuple) throws AlgebricksException;

    public void finish() throws AlgebricksException;

    public void finishPartial() throws AlgebricksException;
}

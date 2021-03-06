package edu.uci.ics.algebricks.examples.piglet.types;

import java.util.List;

import edu.uci.ics.algebricks.utils.Pair;

public class Schema {
    private List<Pair<String, Type>> schema;

    public Schema(List<Pair<String, Type>> schema) {
        this.schema = schema;
    }

    public List<Pair<String, Type>> getSchema() {
        return schema;
    }
}
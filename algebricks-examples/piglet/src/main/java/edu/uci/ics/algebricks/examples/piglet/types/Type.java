package edu.uci.ics.algebricks.examples.piglet.types;

public abstract class Type {
    public enum Tag {
        INTEGER,
        LONG,
        FLOAT,
        DOUBLE,
        CHAR_ARRAY,
        TUPLE,
        BAG,
        MAP
    }
    
    public abstract Tag getTag();
}
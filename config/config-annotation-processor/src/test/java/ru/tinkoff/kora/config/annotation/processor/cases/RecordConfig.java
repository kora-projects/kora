package ru.tinkoff.kora.config.annotation.processor.cases;

import java.util.List;
import java.util.Properties;

public record RecordConfig(
    int intField,
    Integer boxedIntField,
    long longField,
    Long boxedLongField,
    double doubleField,
    Double boxedDoubleField,
    boolean booleanField,
    Boolean boxedBooleanField,
    String stringField,
    List<Integer> listField,
    SomeConfig objectField,
    Properties props
) {

}

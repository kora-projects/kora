package ru.tinkoff.kora.config.annotation.processor.cases;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public final class PojoConfig {
    private final int intField;
    @Nullable
    private final Integer boxedIntField;
    private final long longField;
    private final Long boxedLongField;
    private final double doubleField;
    private final Double boxedDoubleField;
    private final boolean booleanField;
    private final Boolean boxedBooleanField;
    private final String stringField;
    private final List<Integer> listField;
    private final SomeConfig objectField;
    private final Properties props;

    public PojoConfig(int intField,
                      @Nullable Integer boxedIntField,
                      long longField,
                      Long boxedLongField,
                      double doubleField,
                      Double boxedDoubleField,
                      boolean booleanField,
                      Boolean boxedBooleanField,
                      String stringField,
                      List<Integer> listField,
                      SomeConfig objectField, Properties props) {
        this.intField = intField;
        this.boxedIntField = boxedIntField;
        this.longField = longField;
        this.boxedLongField = boxedLongField;
        this.doubleField = doubleField;
        this.boxedDoubleField = boxedDoubleField;
        this.booleanField = booleanField;
        this.boxedBooleanField = boxedBooleanField;
        this.stringField = stringField;
        this.listField = listField;
        this.objectField = objectField;
        this.props = props;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PojoConfig that = (PojoConfig) o;
        return intField == that.intField && longField == that.longField && Double.compare(that.doubleField, doubleField) == 0 && booleanField == that.booleanField && Objects.equals(boxedIntField, that.boxedIntField) && Objects.equals(boxedLongField, that.boxedLongField) && Objects.equals(boxedDoubleField, that.boxedDoubleField) && Objects.equals(boxedBooleanField, that.boxedBooleanField) && Objects.equals(stringField, that.stringField) && Objects.equals(listField, that.listField) && Objects.equals(objectField, that.objectField) && Objects.equals(props, that.props);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intField, boxedIntField, longField, boxedLongField, doubleField, boxedDoubleField, booleanField, boxedBooleanField, stringField, listField, objectField, props);
    }

    @Override
    public String toString() {
        return "PojoConfig{" +
               "intField=" + intField +
               ", boxedIntField=" + boxedIntField +
               ", longField=" + longField +
               ", boxedLongField=" + boxedLongField +
               ", doubleField=" + doubleField +
               ", boxedDoubleField=" + boxedDoubleField +
               ", booleanField=" + booleanField +
               ", boxedBooleanField=" + boxedBooleanField +
               ", stringField='" + stringField + '\'' +
               ", listField=" + listField +
               ", objectField=" + objectField +
               ", props=" + props +
               '}';
    }
}

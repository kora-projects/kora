package io.koraframework.database.jdbc.postgres;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.ArrayColumnData;
import io.koraframework.database.jdbc.EnumColumnData;

import java.math.BigDecimal;
import java.util.UUID;

public interface PostgresArrayColumnDataModule {

    @DefaultComponent
    default ArrayColumnData<UUID> uuidArrayColumnData() {
        return ArrayColumnData.withNoopMapping("uuid");
    }

    @DefaultComponent
    default ArrayColumnData<String> stringArrayColumnData() {
        return ArrayColumnData.withNoopMapping("varchar");
    }

    @DefaultComponent
    default ArrayColumnData<Short> shortArrayColumnData() {
        return ArrayColumnData.withNoopMapping("int2");
    }

    @DefaultComponent
    default ArrayColumnData<Integer> integerArrayColumnData() {
        return ArrayColumnData.withNoopMapping("int4");
    }

    @DefaultComponent
    default ArrayColumnData<Long> longArrayColumnData() {
        return ArrayColumnData.withNoopMapping("int8");
    }

    @DefaultComponent
    default ArrayColumnData<Float> floatArrayColumnData() {
        return ArrayColumnData.withNoopMapping("float4");
    }

    @DefaultComponent
    default ArrayColumnData<Double> doubleArrayColumnData() {
        return ArrayColumnData.withNoopMapping("float8");
    }

    @DefaultComponent
    default ArrayColumnData<Boolean> booleanArrayColumnData() {
        return ArrayColumnData.withNoopMapping("bool");
    }

    @DefaultComponent
    default ArrayColumnData<BigDecimal> bigDecimalArrayColumnData() {
        return ArrayColumnData.withNoopMapping("numeric");
    }

    default <E extends Enum<E>> ArrayColumnData<E> enumArrayColumnData(EnumColumnData<E, String> enumColumnData) {
        var typeName = enumColumnData.sqlTypeName() != null ? enumColumnData.sqlTypeName() : "varchar";
        return new ArrayColumnData<>(
                typeName,
                e -> enumColumnData.valueGetter().apply(e),
                o -> enumColumnData.fromValueFactory().apply((String) o));
    }
}

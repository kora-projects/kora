package io.koraframework.avro.common;

import io.koraframework.avro.common.reader.GenericAvroReader;
import io.koraframework.avro.common.writer.GenericAvroWriter;
import org.apache.avro.Conversions;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.generic.GenericData;

/**
 * <b>Русский</b>: Фабрика {@link GenericData} со стандартными конверсиями логических типов Avro
 * (decimal, uuid, date, time, timestamp, local-timestamp), чтобы {@link GenericAvroReader}/{@link GenericAvroWriter}
 * корректно читали и писали логические типы, а не их низкоуровневые представления.
 * <hr>
 * <b>English</b>: Factory for {@link GenericData} with the standard Avro logical-type conversions registered
 * (decimal, uuid, date, time, timestamp, local-timestamp), so {@link GenericAvroReader}/{@link GenericAvroWriter}
 * read and write logical types as their Java representations instead of the raw underlying types.
 */
public final class AvroGenericData {

    private AvroGenericData() {}

    /**
     * <b>Русский</b>: Создаёт новый экземпляр {@link GenericData} со всеми стандартными конверсиями логических типов.
     * Конверсии не имеют состояния, поэтому полученный экземпляр можно безопасно переиспользовать и делить между потоками.
     * <hr>
     * <b>English</b>: Creates a new {@link GenericData} with all standard logical-type conversions registered.
     * Conversions are stateless, so the returned instance is safe to reuse and share between threads.
     */
    public static GenericData withStandardConversions() {
        var data = new GenericData();
        data.addLogicalTypeConversion(new Conversions.DecimalConversion());
        data.addLogicalTypeConversion(new Conversions.UUIDConversion());
        data.addLogicalTypeConversion(new TimeConversions.DateConversion());
        data.addLogicalTypeConversion(new TimeConversions.TimeMillisConversion());
        data.addLogicalTypeConversion(new TimeConversions.TimeMicrosConversion());
        data.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
        data.addLogicalTypeConversion(new TimeConversions.TimestampMicrosConversion());
        data.addLogicalTypeConversion(new TimeConversions.LocalTimestampMillisConversion());
        data.addLogicalTypeConversion(new TimeConversions.LocalTimestampMicrosConversion());
        return data;
    }
}

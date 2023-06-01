package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParseException;
import ru.tinkoff.kora.common.DefaultComponent;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public interface JsonCommonModule {

    JsonFactory JSON_FACTORY = new JsonFactory(new JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_FIELD_NAMES));

    @DefaultComponent
    default JsonWriter<Object> objectJsonWriter() {
        return JsonObjectCodec::write;
    }

    @DefaultComponent
    default JsonReader<Object> objectJsonReader() {
        return JsonObjectCodec::parse;
    }

    default <T> JsonWriter<List<T>> listJsonWriterFactory(JsonWriter<T> writer) {
        return new ListJsonWriter<>(writer);
    }

    default <T> JsonReader<List<T>> listJsonReaderFactory(JsonReader<T> reader) {
        return new ListJsonReader<>(reader);
    }

    default <T> JsonWriter<Map<String, T>> mapJsonWriterFactory(JsonWriter<T> writer) {
        return new MapJsonWriter<>(writer);
    }

    default <T> JsonReader<Map<String, T>> mapJsonReaderFactory(JsonReader<T> reader) {
        return new MapJsonReader<>(reader);
    }

    default <T> JsonWriter<Set<T>> setJsonWriterFactory(JsonWriter<T> writer) {
        return new SetJsonWriter<>(writer);
    }

    default <T> JsonReader<Set<T>> setJsonReaderFactory(JsonReader<T> reader) {
        return new SetJsonReader<>(reader);
    }

    default <T extends Comparable<T>> JsonReader<SortedSet<T>> sortedSetJsonReaderFactory(JsonReader<T> reader) {
        return new SortedSetJsonReader<>(reader);
    }

    default JsonWriter<Integer> integerJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(object);
            }
        };
    }

    default JsonReader<Integer> integerJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_NUMBER_INT -> parser.getIntValue();
            default -> throw new JsonParseException(parser, "Expecting VALUE_NUMBER_INT token, got " + parser.currentToken());
        };
    }

    /**
     * {@link DateTimeFormatter#ISO_DATE} is used intentionally for OffsetID formatting
     * @return writer
     */
    @DefaultComponent
    default JsonWriter<LocalDate> localDateJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.format(DateTimeFormatter.ISO_DATE));
            }
        };
    }

    /**
     * {@link DateTimeFormatter#ISO_DATE} is used intentionally for flexible nanos precision parsing
     * @return reader
     */
    @DefaultComponent
    default JsonReader<LocalDate> localDateJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> LocalDate.parse(parser.getValueAsString(), DateTimeFormatter.ISO_DATE);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<LocalTime> localTimeJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.format(KoraDateTimeFormatters.ISO_LOCAL_TIME));
            }
        };
    }

    /**
     * {@link DateTimeFormatter#ISO_LOCAL_TIME} is used intentionally for flexible nanos precision parsing
     * @return reader
     */
    @DefaultComponent
    default JsonReader<LocalTime> localTimeJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> LocalTime.parse(parser.getValueAsString(), DateTimeFormatter.ISO_LOCAL_TIME);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<LocalDateTime> localDateTimeJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.format(KoraDateTimeFormatters.ISO_LOCAL_DATE_TIME));
            }
        };
    }

    /**
     * {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME} is used intentionally for flexible nanos precision parsing
     * @return reader
     */
    @DefaultComponent
    default JsonReader<LocalDateTime> localDateTimeJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> LocalDateTime.parse(parser.getValueAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<OffsetTime> offsetTimeJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.format(KoraDateTimeFormatters.ISO_OFFSET_TIME));
            }
        };
    }

    /**
     * {@link DateTimeFormatter#ISO_OFFSET_TIME} is used intentionally for flexible nanos precision parsing
     * @return reader
     */
    @DefaultComponent
    default JsonReader<OffsetTime> offsetTimeJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> OffsetTime.parse(parser.getValueAsString(), DateTimeFormatter.ISO_OFFSET_TIME);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<OffsetDateTime> offsetDateTimeJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.format(KoraDateTimeFormatters.ISO_OFFSET_DATE_TIME));
            }
        };
    }

    /**
     * {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} is used intentionally for flexible nanos precision parsing
     * @return reader
     */
    @DefaultComponent
    default JsonReader<OffsetDateTime> offsetDateTimeJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> OffsetDateTime.parse(parser.getValueAsString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<ZonedDateTime> zonedDateTimeJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.format(KoraDateTimeFormatters.ISO_ZONED_DATE_TIME));
            }
        };
    }

    /**
     * {@link DateTimeFormatter#ISO_ZONED_DATE_TIME} is used intentionally for flexible nanos precision parsing
     * @return reader
     */
    @DefaultComponent
    default JsonReader<ZonedDateTime> zonedDateTimeJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> ZonedDateTime.parse(parser.getValueAsString(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<Instant> instantJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(DateTimeFormatter.ISO_INSTANT.format(object));
            }
        };
    }

    @DefaultComponent
    default JsonReader<Instant> instantJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> DateTimeFormatter.ISO_INSTANT.parse(parser.getValueAsString()).query(Instant::from);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<Year> yearJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.format(KoraDateTimeFormatters.ISO_YEAR));
            }
        };
    }

    @DefaultComponent
    default JsonReader<Year> yearJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> Year.parse(parser.getValueAsString(), KoraDateTimeFormatters.ISO_YEAR);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<YearMonth> yearMonthJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.format(KoraDateTimeFormatters.ISO_YEAR_MONTH));
            }
        };
    }

    @DefaultComponent
    default JsonReader<YearMonth> yearMonthJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> YearMonth.parse(parser.getValueAsString(), KoraDateTimeFormatters.ISO_YEAR_MONTH);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<MonthDay> monthDayJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.format(KoraDateTimeFormatters.ISO_MONTH_DAY));
            }
        };
    }

    @DefaultComponent
    default JsonReader<MonthDay> monthDayJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> MonthDay.parse(parser.getValueAsString(), KoraDateTimeFormatters.ISO_MONTH_DAY);
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };

    }

    @DefaultComponent
    default JsonWriter<Month> monthJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.name());
            }
        };
    }

    @DefaultComponent
    default JsonReader<Month> monthJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> {
                final String valueAsString = parser.getValueAsString();
                for (Month month : KoraDateTimeFormatters.MONTHS) {
                    if (month.name().equalsIgnoreCase(valueAsString)) {
                        yield month;
                    }
                }

                yield Month.of(Integer.parseInt(valueAsString));
            }
            case VALUE_NUMBER_INT -> Month.of(parser.getValueAsInt());
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING or VALUE_NUMBER_INT token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<DayOfWeek> dayOfWeekJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.name());
            }
        };
    }

    @DefaultComponent
    default JsonReader<DayOfWeek> dayOfWeekJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> {
                final String valueAsString = parser.getValueAsString();
                for (DayOfWeek dayOfWeek : KoraDateTimeFormatters.DAY_OF_WEEKS) {
                    if (dayOfWeek.name().equalsIgnoreCase(valueAsString)) {
                        yield dayOfWeek;
                    }
                }

                yield DayOfWeek.of(Integer.parseInt(valueAsString));
            }
            case VALUE_NUMBER_INT -> DayOfWeek.of(parser.getValueAsInt());
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING or VALUE_NUMBER_INT token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<ZoneId> zoneIdJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.getId());
            }
        };
    }

    @DefaultComponent
    default JsonReader<ZoneId> zoneIdJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> ZoneId.of(parser.getValueAsString());
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    @DefaultComponent
    default JsonWriter<Duration> durationJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.toString());
            }
        };
    }

    @DefaultComponent
    default JsonReader<Duration> durationJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> Duration.parse(parser.getValueAsString());
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    default JsonWriter<Long> longJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(object);
            }
        };
    }

    default JsonReader<Long> longJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_NUMBER_INT -> parser.getLongValue();
            default -> throw new JsonParseException(parser, "Expecting VALUE_NUMBER_INT token, got " + parser.currentToken());
        };
    }

    default JsonWriter<Double> doubleJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(object);
            }
        };
    }

    default JsonReader<Double> doubleJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT -> parser.getDoubleValue();
            default -> throw new JsonParseException(parser, "Expecting VALUE_NUMBER_FLOAT or VALUE_NUMBER_INT token, got " + parser.currentToken());
        };
    }

    default JsonWriter<String> stringJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object);
            }
        };
    }

    default JsonReader<String> stringJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_STRING -> parser.getText();
            default -> throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        };
    }

    default JsonWriter<Boolean> booleanJsonWriter() {
        return (gen, object) -> {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeBoolean(object);
            }
        };
    }

    default JsonReader<Boolean> booleanJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            default -> throw new JsonParseException(parser, "Expecting VALUE_TRUE or VALUE_FALSE token, got " + parser.currentToken());
        };
    }

    default JsonWriter<BigDecimal> bigDecimalJsonWriter() {
        return (gen, bigDecimal) -> {
            if (bigDecimal == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(bigDecimal);
            }
        };
    }

    default JsonReader<BigDecimal> bigDecimalJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT -> parser.getDecimalValue();
            default -> throw new JsonParseException(parser, "Expecting VALUE_NUMBER_FLOAT or VALUE_NUMBER_INT token, got " + parser.currentToken());
        };
    }


    default JsonWriter<BigInteger> bigIntegerJsonWriter() {
        return (gen, bigDecimal) -> {
            if (bigDecimal == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(bigDecimal);
            }
        };
    }

    default JsonReader<BigInteger> bigIntegerJsonReader() {
        return parser -> switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_NUMBER_INT -> parser.getBigIntegerValue();
            default -> throw new JsonParseException(parser, "Expecting VALUE_NUMBER_INT token, got " + parser.currentToken());
        };
    }

    default JsonWriter<RawJson> rawJsonWriter() {
        return new RawJsonWriter();
    }

    default JsonReader<UUID> uuidJsonReader() {
        return new UuidJsonCodec();
    }

    default JsonWriter<UUID> uuidJsonWriter() {
        return new UuidJsonCodec();
    }
}

package io.koraframework.cache.redis;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public interface RedisCacheMapperModule {

    @Json
    @DefaultComponent
    default <V> RedisCacheValueMapper<V> jsonRedisCacheValueMapper(JsonWriter<V> jsonWriter, JsonReader<V> jsonReader) {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(V value) {
                return jsonWriter.toByteArray(value);
            }

            @Override
            public V read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return jsonReader.read(serializedValue);
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<String> stringRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(String value) {
                return value.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return new String(serializedValue, StandardCharsets.UTF_8);
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<byte[]> bytesRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(byte[] value) {
                return value;
            }

            @Override
            public byte[] read(byte[] serializedValue) {
                return serializedValue;
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Integer> intRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(Integer value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Integer read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return Integer.valueOf(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Long> longRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(Long value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Long read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return Long.valueOf(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<BigInteger> bigIntRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(BigInteger value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public BigInteger read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return new BigInteger(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<UUID> uuidRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(UUID value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public UUID read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return UUID.fromString(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Short> shortRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(Short value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Short read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return Short.valueOf(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Character> characterRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(Character value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Character read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    var str = new String(serializedValue, StandardCharsets.UTF_8);
                    return str.isEmpty() ? null : str.charAt(0);
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Float> floatRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(Float value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Float read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return Float.valueOf(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Double> doubleRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(Double value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Double read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return Double.valueOf(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<BigDecimal> bigDecimalRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(BigDecimal value) {
                return value.stripTrailingZeros().toPlainString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public BigDecimal read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return new BigDecimal(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Instant> instantRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(Instant value) {
                return DateTimeFormatter.ISO_INSTANT.format(value).getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Instant read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return DateTimeFormatter.ISO_INSTANT.parse(new String(serializedValue, StandardCharsets.UTF_8)).query(Instant::from);
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<LocalDateTime> localDateTimeRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(LocalDateTime value) {
                return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value).getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public LocalDateTime read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return LocalDateTime.parse(new String(serializedValue, StandardCharsets.UTF_8), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<LocalDate> localDateRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(LocalDate value) {
                return DateTimeFormatter.ISO_LOCAL_DATE.format(value).getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public LocalDate read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return LocalDate.parse(new String(serializedValue, StandardCharsets.UTF_8), DateTimeFormatter.ISO_LOCAL_DATE);
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<ZonedDateTime> zonedDateTimeRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(ZonedDateTime value) {
                return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(value).getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public ZonedDateTime read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return ZonedDateTime.parse(new String(serializedValue, StandardCharsets.UTF_8), DateTimeFormatter.ISO_ZONED_DATE_TIME);
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Duration> durationRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {


            @Override
            public byte[] write(Duration value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Duration read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return Duration.parse(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Period> periodRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(Period value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Period read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return Period.parse(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Boolean> booleanRedisCacheValueMapper() {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(Boolean value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Boolean read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return Boolean.valueOf(new String(serializedValue, StandardCharsets.UTF_8));
                }
            }
        };
    }

    @DefaultComponent
    default <T extends Enum<T>> RedisCacheValueMapper<T> enumRedisCacheValueMapper(TypeRef<T> enumType) {
        final Map<String, T> enumValues = new HashMap<>();
        for (T enumConstant : enumType.getRawType().getEnumConstants()) {
            enumValues.put(enumConstant.toString(), enumConstant);
        }

        var logger = LoggerFactory.getLogger(RedisCacheValueMapper.class);
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(T value) {
                return value.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public T read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    var enumAsStr = new String(serializedValue, StandardCharsets.UTF_8);
                    var enumValue = enumValues.get(enumAsStr);
                    if (enumValue == null) {
                        logger.warn("Unknown Enum.toString() value '{}' for enum: {}", enumAsStr, enumType.getRawType());
                    }
                    return enumValue;
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheKeyMapper<byte[]> byteRedisCacheKeyMapper() {
        // Still possible collision with user value, attention!
        var NULL_KEY_VALUE = "@N-U-L@".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : Base64.getEncoder().encodeToString(value).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Boolean> booleanRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        var valTrue = Boolean.TRUE.toString().getBytes(StandardCharsets.UTF_8);
        var valFalse = Boolean.FALSE.toString().getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : (value) ? valTrue : valFalse;
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Character> characterRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Short> shortRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Integer> intRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Long> longRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<BigInteger> bigIntegerRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<BigDecimal> bigDecimalRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.stripTrailingZeros().toPlainString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<UUID> uuidRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<String> stringRedisCacheKeyMapper() {
        // Still possible collision with user value, attention!
        var NULL_KEY_VALUE = "@N-U-L@".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Instant> instantRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : DateTimeFormatter.ISO_INSTANT.format(value).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<LocalDateTime> localDateTimeRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<LocalDate> localDateRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : DateTimeFormatter.ISO_LOCAL_DATE.format(value).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<ZonedDateTime> zonedDateTimeRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : DateTimeFormatter.ISO_INSTANT.format(value.toInstant()).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Duration> durationRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Period> periodRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default <T extends Enum<T>> RedisCacheKeyMapper<T> enumRedisCacheKeyMapper() {
        var NULL_KEY_VALUE = "0NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default <T> RedisCacheKeyMapper<Collection<T>> collectionRedisCacheKeyMapper(RedisCacheKeyMapper<T> itemMapper) {
        // Still possible collision with user value, attention!
        var NULL_KEY_VALUE = "@N-U-L@".getBytes(StandardCharsets.UTF_8);
        var NULL_KEY_ITEM = "@N-I-L@".getBytes(StandardCharsets.UTF_8);
        return value -> {
            if (value == null) {
                return NULL_KEY_VALUE;
            }

            List<byte[]> items = new ArrayList<>(value.size());
            for (T item : value) {
                var itemAsBytes = itemMapper.apply(item);
                if (itemAsBytes == null) {
                    items.add(NULL_KEY_ITEM);
                } else {
                    items.add(itemAsBytes);
                }
            }

            items.sort(Arrays::compare);
            int length = 0;
            for (byte[] item : items) {
                length += item.length;
            }
            if (items.size() > 1) {
                length += (items.size() - 1) * RedisCacheKeyMapper.DELIMITER.length;
            }

            var result = new byte[length];
            int offset = 0;
            for (int i = 0; i < items.size(); i++) {
                byte[] item = items.get(i);
                System.arraycopy(item, 0, result, offset, item.length);
                offset += item.length;
                if (i < items.size() - 1) {
                    System.arraycopy(RedisCacheKeyMapper.DELIMITER, 0, result, offset, RedisCacheKeyMapper.DELIMITER.length);
                    offset += RedisCacheKeyMapper.DELIMITER.length;
                }
            }
            return result;
        };
    }
}

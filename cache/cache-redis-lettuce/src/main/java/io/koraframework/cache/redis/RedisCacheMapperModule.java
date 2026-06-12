package io.koraframework.cache.redis;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.DefaultComponent;
import io.koraframework.json.common.JsonModule;
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

public interface RedisCacheMapperModule extends JsonModule {

    @Json
    @DefaultComponent
    default <V> RedisCacheValueMapper<V> cacheRedisValueJsonMapper(JsonWriter<V> jsonWriter, JsonReader<V> jsonReader) {
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
    default RedisCacheValueMapper<String> cacheRedisValueStringMapper() {
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
    default RedisCacheValueMapper<byte[]> cacheRedisValueBytesMapper() {
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
    default RedisCacheValueMapper<Integer> cacheRedisValueIntMapper() {
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
    default RedisCacheValueMapper<Long> cacheRedisValueLongMapper() {
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
    default RedisCacheValueMapper<BigInteger> cacheRedisValueBigIntMapper() {
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
    default RedisCacheValueMapper<UUID> cacheRedisValueUuidMapper() {
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
    default RedisCacheValueMapper<Short> shortRedisValueMapper() {
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
    default RedisCacheValueMapper<Character> characterRedisValueMapper() {
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
    default RedisCacheValueMapper<Float> floatRedisValueMapper() {
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
    default RedisCacheValueMapper<Double> doubleRedisValueMapper() {
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
    default RedisCacheValueMapper<BigDecimal> bigDecimalRedisValueMapper() {
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
    default RedisCacheValueMapper<Instant> instantRedisValueMapper() {
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
    default RedisCacheValueMapper<LocalDateTime> localDateTimeRedisValueMapper() {
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
    default RedisCacheValueMapper<LocalDate> localDateRedisValueMapper() {
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
    default RedisCacheValueMapper<ZonedDateTime> zonedDateTimeRedisValueMapper() {
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
    default RedisCacheValueMapper<Duration> durationRedisValueMapper() {
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
    default RedisCacheValueMapper<Period> periodRedisValueMapper() {
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
    default RedisCacheValueMapper<Boolean> booleanRedisValueMapper() {
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
    default <T extends Enum<T>> RedisCacheValueMapper<T> enumRedisValueMapper(TypeRef<T> enumType) {
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
    default RedisCacheKeyMapper<byte[]> byteRedisKeyMapper() {
        // Still possible collision with user value, attention!
        var NULL_KEY_VALUE = "@N-U-L@".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : Base64.getEncoder().encodeToString(value).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Boolean> booleanRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        var valTrue = Boolean.TRUE.toString().getBytes(StandardCharsets.UTF_8);
        var valFalse = Boolean.FALSE.toString().getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : (value) ? valTrue : valFalse;
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Character> characterRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Short> shortRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Integer> cacheRedisKeyIntMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Long> cacheRedisKeyLongMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<BigInteger> cacheRedisKeyBigIntMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<BigDecimal> bigDecimalRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.stripTrailingZeros().toPlainString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<UUID> cacheRedisKeyUuidMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<String> cacheRedisKeyStringMapper() {
        // Still possible collision with user value, attention!
        var NULL_KEY_VALUE = "@N-U-L@".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Instant> instantRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : DateTimeFormatter.ISO_INSTANT.format(value).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<LocalDateTime> localDateTimeRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<LocalDate> localDateRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : DateTimeFormatter.ISO_LOCAL_DATE.format(value).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<ZonedDateTime> zonedDateTimeRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : DateTimeFormatter.ISO_INSTANT.format(value.toInstant()).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Duration> durationRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Period> periodRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default <T extends Enum<T>> RedisCacheKeyMapper<T> enumRedisKeyMapper() {
        var NULL_KEY_VALUE = "0NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default <T> RedisCacheKeyMapper<Collection<T>> collectionRedisKeyMapper(RedisCacheKeyMapper<T> itemMapper) {
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

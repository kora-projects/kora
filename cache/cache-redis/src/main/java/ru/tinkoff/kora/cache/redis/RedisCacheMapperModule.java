package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.telemetry.CacheMetrics;
import ru.tinkoff.kora.cache.telemetry.CacheTracer;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.json.common.JsonCommonModule;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public interface RedisCacheMapperModule extends JsonCommonModule {

    @DefaultComponent
    default RedisCacheTelemetry redisCacheTelemetry(@Nullable CacheMetrics metrics, @Nullable CacheTracer tracer) {
        return new RedisCacheTelemetry(metrics, tracer);
    }

    @Json
    @DefaultComponent
    default <V> RedisCacheValueMapper<V> jsonRedisValueMapper(JsonWriter<V> jsonWriter, JsonReader<V> jsonReader) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(V value) {
                return jsonWriter.toByteArrayUnchecked(value);
            }

            @Override
            public V read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    return jsonReader.readUnchecked(serializedValue);
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<String> stringRedisValueMapper() {
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
    default RedisCacheValueMapper<byte[]> bytesRedisValueMapper() {
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
    default RedisCacheValueMapper<Integer> intRedisValueMapper(RedisCacheKeyMapper<Integer> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Integer value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<Long> longRedisValueMapper(RedisCacheKeyMapper<Long> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Long value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<BigInteger> bigIntRedisValueMapper(RedisCacheKeyMapper<BigInteger> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(BigInteger value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<UUID> uuidRedisValueMapper(RedisCacheKeyMapper<UUID> keyMapper) {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(UUID value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<Short> shortRedisValueMapper(RedisCacheKeyMapper<Short> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Short value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<Character> characterRedisValueMapper(RedisCacheKeyMapper<Character> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Character value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<Float> floatRedisValueMapper(RedisCacheKeyMapper<Float> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Float value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<Double> doubleRedisValueMapper(RedisCacheKeyMapper<Double> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Double value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<BigDecimal> bigDecimalRedisValueMapper(RedisCacheKeyMapper<BigDecimal> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(BigDecimal value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<Instant> instantRedisValueMapper(RedisCacheKeyMapper<Instant> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Instant value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<LocalDateTime> localDateTimeRedisValueMapper(RedisCacheKeyMapper<LocalDateTime> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(LocalDateTime value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<LocalDate> localDateRedisValueMapper(RedisCacheKeyMapper<LocalDate> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(LocalDate value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<ZonedDateTime> zonedDateTimeRedisValueMapper(RedisCacheKeyMapper<ZonedDateTime> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(ZonedDateTime value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<Duration> durationRedisValueMapper(RedisCacheKeyMapper<Duration> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Duration value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<Period> periodRedisValueMapper(RedisCacheKeyMapper<Period> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Period value) {
                return keyMapper.apply(value);
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
    default RedisCacheValueMapper<Boolean> booleanRedisValueMapper(RedisCacheKeyMapper<Boolean> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Boolean value) {
                return keyMapper.apply(value);
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
    default RedisCacheKeyMapper<Integer> intRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Long> longRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<BigInteger> bigIntRedisKeyMapper() {
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
    default RedisCacheKeyMapper<UUID> uuidRedisKeyMapper() {
        var NULL_KEY_VALUE = "NUL".getBytes(StandardCharsets.UTF_8);
        return value -> (value == null)
            ? NULL_KEY_VALUE
            : value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<String> stringRedisKeyMapper() {
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
        return value -> {
            if (value == null) {
                return NULL_KEY_VALUE;
            }

            List<byte[]> items = new ArrayList<>(value.size());
            for (T item : value) {
                var itemAsBytes = itemMapper.apply(item);
                if (itemAsBytes == null) {
                    throw new IllegalArgumentException("RedisCacheKeyMapper item in collection can't be null");
                }
                items.add(itemAsBytes);
            }

            items.sort(Arrays::compare);
            int length = 0;
            for (byte[] item : items) {
                length += item.length;
            }

            var result = new byte[length];
            int offset = 0;
            for (byte[] item : items) {
                System.arraycopy(item, 0, result, offset, item.length);
                offset += item.length;
            }
            return result;
        };
    }
}

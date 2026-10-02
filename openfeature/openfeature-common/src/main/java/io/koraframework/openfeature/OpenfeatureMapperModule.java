package io.koraframework.openfeature;

import dev.openfeature.sdk.exceptions.TypeMismatchError;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.util.Size;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.Json;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public interface OpenfeatureMapperModule {

    @DefaultComponent
    default OpenfeatureFlagMapper<Integer> openfeatureMapperInt() {
        return (features, key, defaultValue, ctx) -> {
            try {
                try {
                    return features.getIntegerValue(key, defaultValue, ctx);
                } catch (TypeMismatchError error) {
                    try {
                        var s = features.getStringValue(key, "", ctx);
                        return Integer.parseInt(s);
                    } catch (Exception e) {
                        error.addSuppressed(e);
                        throw e;
                    }
                }
            } catch (NumberFormatException e) {
                throw new TypeMismatchError("Unprocessable conversion to Integer for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Boolean> openfeatureMapperBoolean() {
        return (features, key, defaultValue, ctx) -> {
            try {
                try {
                    return features.getBooleanValue(key, defaultValue, ctx);
                } catch (TypeMismatchError error) {
                    try {
                        var s = features.getStringValue(key, "", ctx);
                        return Boolean.parseBoolean(s);
                    } catch (Exception e) {
                        error.addSuppressed(e);
                        throw e;
                    }
                }
            } catch (NumberFormatException e) {
                throw new TypeMismatchError("Unprocessable conversion to Boolean for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Long> openfeatureMapperLong() {
        return (features, key, defaultValue, ctx) -> {
            try {
                try {
                    var i = features.getIntegerValue(key, defaultValue.intValue(), ctx);
                    return i.longValue();
                } catch (TypeMismatchError error) {
                    try {
                        var s = features.getStringValue(key, "", ctx);
                        return Long.parseLong(s);
                    } catch (Exception e) {
                        error.addSuppressed(e);
                        throw e;
                    }
                }
            } catch (NumberFormatException e) {
                throw new TypeMismatchError("Unprocessable conversion to Long for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Double> openfeatureMapperDouble() {
        return (features, key, defaultValue, ctx) -> {
            try {
                try {
                    return features.getDoubleValue(key, defaultValue, ctx);
                } catch (TypeMismatchError error) {
                    try {
                        var s = features.getStringValue(key, "", ctx);
                        return Double.parseDouble(s);
                    } catch (Exception e) {
                        error.addSuppressed(e);
                        throw e;
                    }
                }
            } catch (NumberFormatException e) {
                throw new TypeMismatchError("Unprocessable conversion to Double for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Float> openfeatureMapperFloat() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultDouble = Double.valueOf(defaultValue);
                var value = features.getDoubleValue(key, defaultDouble, ctx);
                return value.floatValue();
            } catch (TypeMismatchError error) {
                try {
                    var value = features.getIntegerValue(key, -1, ctx);
                    return value.floatValue();
                } catch (TypeMismatchError e) {
                    try {
                        var s = features.getStringValue(key, "", ctx);
                        return Float.parseFloat(s);
                    } catch (Exception ex) {
                        error.addSuppressed(ex);
                        throw e;
                    }
                }
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Pattern> openfeatureMapperPattern() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                if (string == defaultStr) {
                    return defaultValue;
                }
                return Pattern.compile(string);
            } catch (PatternSyntaxException e) {
                throw new TypeMismatchError("Unprocessable conversion to Pattern for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<BigInteger> openfeatureMapperBigInteger() {
        return (features, key, defaultValue, ctx) -> {
            try {
                try {
                    var defaultStr = defaultValue.toString();
                    var string = features.getStringValue(key, defaultStr, ctx);
                    if (string == defaultStr) {
                        return defaultValue;
                    } else {
                        return new BigInteger(string);
                    }
                } catch (TypeMismatchError error) {
                    try {
                        var value = features.getIntegerValue(key, -1, ctx);
                        return BigInteger.valueOf(value);
                    } catch (Exception e) {
                        error.addSuppressed(e);
                        throw e;
                    }
                }
            } catch (NumberFormatException e) {
                throw new TypeMismatchError("Unprocessable conversion to BigInteger for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<BigDecimal> openfeatureMapperBigDecimal() {
        return (features, key, defaultValue, ctx) -> {
            try {
                try {
                    var defaultStr = defaultValue.toString();
                    var string = features.getStringValue(key, defaultStr, ctx);
                    if (string == defaultStr) {
                        return defaultValue;
                    } else {
                        return new BigDecimal(string);
                    }
                } catch (TypeMismatchError error) {
                    try {
                        var value = features.getIntegerValue(key, -1, ctx);
                        return BigDecimal.valueOf(value);
                    } catch (TypeMismatchError e) {
                        var value = features.getDoubleValue(key, -1d, ctx);
                        return BigDecimal.valueOf(value);
                    }
                }
            } catch (NumberFormatException e) {
                throw new TypeMismatchError("Unprocessable conversion to BigDecimal for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Period> openfeatureMapperPeriod() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                if (string == defaultStr) {
                    return defaultValue;
                }
                return Period.parse(string);
            } catch (DateTimeException e) {
                throw new TypeMismatchError("Unprocessable conversion to Period for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<UUID> openfeatureMapperUUID() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                if (string == defaultStr) {
                    return defaultValue;
                }
                return UUID.fromString(string);
            } catch (IllegalArgumentException e) {
                throw new TypeMismatchError("Unprocessable conversion to UUID for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<LocalDate> openfeatureMapperLocalDate() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                if (string == defaultStr) {
                    return defaultValue;
                }
                return LocalDate.parse(string);
            } catch (DateTimeException e) {
                throw new TypeMismatchError("Unprocessable conversion to LocalDate for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<LocalDateTime> openfeatureMapperLocalDateTime() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                if (string == defaultStr) {
                    return defaultValue;
                }
                return LocalDateTime.parse(string);
            } catch (DateTimeException e) {
                throw new TypeMismatchError("Unprocessable conversion to LocalDateTime for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<LocalTime> openfeatureMapperLocalTime() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                if (string == defaultStr) {
                    return defaultValue;
                }
                return LocalTime.parse(string);
            } catch (DateTimeException e) {
                throw new TypeMismatchError("Unprocessable conversion to LocalTime for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<OffsetDateTime> openfeatureMapperOffsetDateTime() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                if (string == defaultStr) {
                    return defaultValue;
                }
                return OffsetDateTime.parse(string);
            } catch (DateTimeException e) {
                throw new TypeMismatchError("Unprocessable conversion to OffsetDateTime for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Size> openfeatureMapperSize() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.to(Size.Type.BYTES).toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                if (string == defaultStr) {
                    return defaultValue;
                }
                return Size.parse(string);
            } catch (IllegalArgumentException e) {
                throw new TypeMismatchError("Unprocessable conversion to Size for key: " + key);
            }
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Duration> openfeatureMapperDuration() {
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                return Duration.parse(string);
            } catch (DateTimeException e) {
                throw new TypeMismatchError("Unprocessable conversion to Duration for key: " + key);
            }
        };
    }

    @DefaultComponent
    default <T extends Enum<T>> OpenfeatureFlagMapper<T> openfeatureMapperEnum(TypeRef<T> typeRef) {
        var map = new HashMap<String, T>();
        for (T enumConstant : typeRef.getRawType().getEnumConstants()) {
            map.put(enumConstant.toString(), enumConstant);
        }
        return (features, key, defaultValue, ctx) -> {
            try {
                var defaultStr = defaultValue.toString();
                var string = features.getStringValue(key, defaultStr, ctx);
                var enumvue = map.get(string);
                if (enumvue != null) {
                    return enumvue;
                }
                throw new TypeMismatchError("Unexpected Openfeature flag enum value '" + string + "', expected one of: " + map.values());
            } catch (DateTimeException e) {
                throw new TypeMismatchError("Unprocessable conversion to Duration for key: " + key);
            }
        };
    }

    @Json
    @DefaultComponent
    default <T> OpenfeatureFlagMapper<T> openfeatureMapperJson(JsonReader<T> reader, TypeRef<T> type) {
        return (features, key, defaultValue, ctx) -> {
            var defaultStr = defaultValue.toString();
            var string = features.getStringValue(key, defaultStr, ctx);
            if (string == defaultStr) {
                return defaultValue;
            }
            try {
                var readed = reader.read(string);
                if (readed == null) {
                    throw new TypeMismatchError("Unprocessable conversion to " + type.getRawType() + " from Value: " + string);
                }
                return readed;
            } catch (IOException e) {
                throw new TypeMismatchError("Unprocessable conversion to " + type.getRawType() + " from Value: " + string);
            }
        };
    }
}

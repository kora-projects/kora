package io.koraframework.config.common;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <b>Русский</b>: Базовое предоставления значения в конфигурации в Kora.
 * <hr>
 * <b>English</b>: Basic value representation in the configuration in Kora.
 */
sealed public interface ConfigValue<T> {

    @Nullable
    T value();

    ConfigValueOrigin origin();

    default String asString() {
        return switch (this) {
            case ConfigValue.StringValue str -> str.value();
            case ConfigValue.NumberValue number -> number.value().toString();
            case ConfigValue.BooleanValue booleanValue -> booleanValue.value() ? "true" : "false";
            default -> throw ConfigValueException.unexpectedValueType(this, StringValue.class);
        };
    }

    default Number asNumber() {
        if (this instanceof ConfigValue.StringValue str) {
            try {
                return new BigDecimal(str.value());
            } catch (NumberFormatException e) {
                throw ConfigValueException.parsingError(this, e);
            }
        } else if (this instanceof ConfigValue.NumberValue number) {
            return number.value();
        } else {
            throw ConfigValueException.unexpectedValueType(this, ConfigValue.NumberValue.class);
        }
    }

    default ArrayValue asArray() {
        if (this instanceof ArrayValue arrayValue) {
            return arrayValue;
        }
        throw ConfigValueException.unexpectedValueType(this, ConfigValue.ArrayValue.class);
    }

    default ObjectValue asObject() {
        if (this instanceof ObjectValue object) {
            return object;
        }
        throw ConfigValueException.unexpectedValueType(this, ConfigValue.ObjectValue.class);
    }

    default boolean asBoolean() {
        if (this instanceof ConfigValue.StringValue str) {
            return Boolean.parseBoolean(str.value());
        }
        if (this instanceof BooleanValue bv) {
            return bv.value;
        }
        throw ConfigValueException.unexpectedValueType(this, ConfigValue.BooleanValue.class);
    }

    default boolean isNull() {
        return this instanceof NullValue;
    }

    record NullValue(ConfigValueOrigin origin) implements ConfigValue<Void> {
        public NullValue {
            Objects.requireNonNull(origin);
        }

        @Override
        public String toString() {
            return "null";
        }

        @Override
        public Void value() {
            return null;
        }
    }

    record BooleanValue(ConfigValueOrigin origin, Boolean value) implements ConfigValue<Boolean> {
        public BooleanValue {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(value);
        }

        @Override
        public String toString() {
            return Objects.toString(value);
        }
    }

    record StringValue(ConfigValueOrigin origin, String value) implements ConfigValue<String> {
        public StringValue {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(value);
        }

        @Override
        public String toString() {
            return "\"" + this.value + "\"";
        }
    }

    record NumberValue(ConfigValueOrigin origin, Number value) implements ConfigValue<Number> {
        public NumberValue {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(value);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    record ArrayValue(ConfigValueOrigin origin, List<ConfigValue<?>> value) implements ConfigValue<List<ConfigValue<?>>>, Iterable<ConfigValue<?>> {
        public ArrayValue {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(value);
        }

        public ConfigValue<?> get(int i) {
            return Objects.requireNonNull(this.value.get(i));
        }

        @Override
        public Iterator<ConfigValue<?>> iterator() {
            return this.value.iterator();
        }

        @Override
        public String toString() {
            return value.stream().map(Objects::toString).collect(Collectors.joining(", ", "[", "]"));
        }
    }

    /**
     * @param overriddenKeys keys of {@code value} that came from a lower-priority config layer and are overridden there by a key
     *                       of a higher-priority layer spelled differently (for example {@code maxPoolSize} by {@code max-pool-size}):
     *                       they stay in {@code value} as literal map entries, but field lookups through {@link #get(PathElement.Key)} skip them
     */
    record ObjectValue(ConfigValueOrigin origin, Map<String, ConfigValue<?>> value, Set<String> overriddenKeys) implements ConfigValue<Map<String, ConfigValue<?>>>, Iterable<Map.Entry<String, ConfigValue<?>>> {
        public ObjectValue {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(value);
            Objects.requireNonNull(overriddenKeys);
        }

        public ObjectValue(ConfigValueOrigin origin, Map<String, ConfigValue<?>> value) {
            this(origin, value, Set.of());
        }

        public ConfigValue<?> get(String key) {
            return this.get(PathElement.get(key));
        }

        public ConfigValue<?> get(PathElement.Key key) {
            var value = this.overriddenKeys.contains(key.name()) ? null : this.value.get(key.name());
            if (value != null) {
                return value;
            }
            for (var relaxedName : key.relaxedNames()) {
                if (this.overriddenKeys.contains(relaxedName)) {
                    continue;
                }
                value = this.value.get(relaxedName);
                if (value != null) {
                    return value;
                }
            }
            return new NullValue(this.origin.child(key));
        }

        @Override
        public Iterator<Map.Entry<String, ConfigValue<?>>> iterator() {
            return this.value.entrySet().iterator();
        }

        @Override
        public String toString() {
            return value.entrySet().stream().map(e -> ("\"" + e.getKey() + "\": " + e.getValue()).indent(2).stripTrailing() + ",\n").collect(Collectors.joining("", "{\n", "}"));
        }
    }
}

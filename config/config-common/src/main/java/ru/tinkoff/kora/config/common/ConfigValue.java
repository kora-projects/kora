package ru.tinkoff.kora.config.common;

import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

sealed public interface ConfigValue<T> {
    @Nullable
    T value();

    ConfigValueOrigin origin();

    default String asString() {
        if (this instanceof ConfigValue.StringValue str) {
            return str.value();
        } else if (this instanceof ConfigValue.NumberValue number) {
            return number.value().toString();
        } else if (this instanceof ConfigValue.BooleanValue booleanValue) {
            return booleanValue.value() ? "true" : "false";
        } else {
            throw ConfigValueExtractionException.unexpectedValueType(this, ConfigValue.StringValue.class);
        }
    }

    default Number asNumber() {
        if (this instanceof ConfigValue.StringValue str) {
            try {
                return new BigDecimal(str.value());
            } catch (NumberFormatException e) {
                throw ConfigValueExtractionException.parsingError(this, e);
            }
        } else if (this instanceof ConfigValue.NumberValue number) {
            return number.value();
        } else {
            throw ConfigValueExtractionException.unexpectedValueType(this, ConfigValue.NumberValue.class);
        }
    }

    default ArrayValue asArray() {
        if (this instanceof ArrayValue arrayValue) {
            return arrayValue;
        }
        throw ConfigValueExtractionException.unexpectedValueType(this, ConfigValue.ArrayValue.class);
    }

    default ObjectValue asObject() {
        if (this instanceof ObjectValue object) {
            return object;
        }
        throw ConfigValueExtractionException.unexpectedValueType(this, ConfigValue.ObjectValue.class);
    }

    default boolean asBoolean() {
        if (this instanceof ConfigValue.StringValue str) {
            return Boolean.parseBoolean(str.value());
        }
        if (this instanceof BooleanValue bv) {
            return bv.value;
        }
        throw ConfigValueExtractionException.unexpectedValueType(this, ConfigValue.BooleanValue.class);
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

    record ObjectValue(ConfigValueOrigin origin, Map<String, ConfigValue<?>> value) implements ConfigValue<Map<String, ConfigValue<?>>>, Iterable<Map.Entry<String, ConfigValue<?>>> {
        public ObjectValue {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(value);
        }

        public ConfigValue<?> get(String key) {
            return this.get(new PathElement.Key(key));
        }

        public ConfigValue<?> get(PathElement.Key key) {
            var value = this.value.get(key.name());
            if (value != null) {
                return value;
            }
            for (var relaxedName : key.relaxedNames()) {
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

package ru.tinkoff.grpc.client.config;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class DefaultServiceConfigConfigValueExtractor implements ConfigValueExtractor<DefaultServiceConfig> {
    @Nullable
    @Override
    public DefaultServiceConfig extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        var object = value.asObject();
        var map = this.toMap(object);
        return new DefaultServiceConfig(map);
    }

    private Map<String, Object> toMap(ConfigValue.ObjectValue objectValue) {
        var result = new HashMap<String, Object>();
        for (var entry : objectValue) {
            var key = entry.getKey();
            var value = entry.getValue();
            var object = this.toObject(value);
            if (object != null) {
                result.put(key, object);
            }
        }
        return result;
    }

    private Object toObject(ConfigValue<?> value) {
        if (value instanceof ConfigValue.StringValue str) {
            return str.value();
        } else if (value instanceof ConfigValue.BooleanValue bool) {
            return bool.value();
        } else if (value instanceof ConfigValue.NumberValue num) {
            return num.value().doubleValue(); // service config accepts only double values
        } else if (value instanceof ConfigValue.ObjectValue obj) {
            return this.toMap(obj);
        } else if (value instanceof ConfigValue.ArrayValue arr) {
            var list = new ArrayList<>();
            for (var item : arr) {
                var object = this.toObject(item);
                if (object != null) {
                    list.add(object);
                }
            }
            return list;
        } else if (value instanceof ConfigValue.NullValue) {
            return null;
        } else {
            throw new IllegalArgumentException("Unsupported config value type: " + value.getClass());
        }
    }
}

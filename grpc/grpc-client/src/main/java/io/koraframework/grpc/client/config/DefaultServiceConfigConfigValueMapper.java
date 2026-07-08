package io.koraframework.grpc.client.config;

import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class DefaultServiceConfigConfigValueMapper implements ConfigValueMapper<DefaultServiceConfig> {

    @Nullable
    @Override
    public DefaultServiceConfig map(ConfigValue<?> value) {
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
        return switch (value) {
            case ConfigValue.StringValue str -> str.value();
            case ConfigValue.BooleanValue bool -> bool.value();
            case ConfigValue.NumberValue num -> num.value().doubleValue(); // service config accepts only double values
            case ConfigValue.ObjectValue obj -> this.toMap(obj);
            case ConfigValue.ArrayValue arr -> {
                var list = new ArrayList<>();
                for (var item : arr) {
                    var object = this.toObject(item);
                    if (object != null) {
                        list.add(object);
                    }
                }
                yield list;
            }
            case ConfigValue.NullValue nullValue -> null;
            default -> throw new IllegalArgumentException("Unsupported config value type: " + value.getClass());
        };
    }
}

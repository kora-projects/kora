package ru.tinkoff.kora.config.hocon;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValuePath;
import ru.tinkoff.kora.config.common.impl.SimpleConfig;
import ru.tinkoff.kora.config.common.impl.SimpleConfigValueOrigin;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class HoconConfigFactory {
    public static Config fromHocon(ConfigOrigin origin, com.typesafe.config.Config config) {
        var object = config.root();
        var path = ConfigValuePath.root();

        var value = toObject(origin, object, path);
        return new SimpleConfig(origin, value);
    }

    @Nullable
    private static ConfigValue<?> toValue(ConfigOrigin origin, com.typesafe.config.ConfigValue object, ConfigValuePath path) {
        try {
            return switch (object.valueType()) {
                case OBJECT -> toObject(origin, (ConfigObject) object, path);
                case LIST -> toArray(origin, (ConfigList) object, path);
                case NUMBER -> new ConfigValue.NumberValue(new SimpleConfigValueOrigin(origin, path), (Number) object.unwrapped());
                case BOOLEAN -> new ConfigValue.BooleanValue(new SimpleConfigValueOrigin(origin, path), (Boolean) object.unwrapped());
                case NULL -> null;
                case STRING -> new ConfigValue.StringValue(new SimpleConfigValueOrigin(origin, path), (String) object.unwrapped());
            };
        } catch (ConfigException.NotResolved notResolved) {
            return new ConfigValue.StringValue(new SimpleConfigValueOrigin(origin, path), object.render(ConfigRenderOptions.concise().setJson(false)));
        }
    }

    private static ConfigValue.ObjectValue toObject(ConfigOrigin origin, ConfigObject object, ConfigValuePath path) {
        var result = new LinkedHashMap<String, ConfigValue<?>>();
        for (var entry : object.entrySet()) {
            var value = toValue(origin, entry.getValue(), path.child(entry.getKey()));
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }

        return new ConfigValue.ObjectValue(new SimpleConfigValueOrigin(origin, path), result);
    }

    private static ConfigValue.ArrayValue toArray(ConfigOrigin origin, ConfigList list, ConfigValuePath path) {
        var result = new ArrayList<ConfigValue<?>>(list.size());
        for (var i = 0; i < list.size(); i++) {
            var configValue = list.get(i);
            result.add(toValue(origin, configValue, path.child(i)));
        }
        return new ConfigValue.ArrayValue(new SimpleConfigValueOrigin(origin, path), List.copyOf(result));
    }
}

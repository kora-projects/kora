package io.koraframework.config.common.util;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.ConfigValuePath;
import io.koraframework.config.common.PathElement;
import io.koraframework.config.common.exception.ConfigValueException;

import java.util.LinkedList;

public final class ConfigValueExtractorUtils {

    private ConfigValueExtractorUtils() { }

    public static ConfigValue<?> extract(Config config, ConfigValuePath path) {
        var value = (ConfigValue<?>) config.root();
        var parts = new LinkedList<PathElement>();
        while (path != null) {
            if (path.last() == null) {
                break;
            }
            parts.addFirst(path.last());
            path = path.prev();
        }
        for (int j = 0; j < parts.size(); j++) {
            var part = parts.get(j);
            if (part instanceof PathElement.Index i) {
                if (value instanceof ConfigValue.ArrayValue arrayValue) {
                    value = arrayValue.get(i.index());
                    if (value instanceof ConfigValue.NullValue) {
                        var origin = arrayValue.origin();
                        do {
                            origin = origin.child(parts.get(j));
                            j++;
                        } while (j < parts.size());
                        return new ConfigValue.NullValue(origin);
                    }
                } else {
                    throw ConfigValueException.unexpectedValueType(value, ConfigValue.ArrayValue.class);
                }
            } else if (part instanceof PathElement.Key f) {
                if (value instanceof ConfigValue.ObjectValue objectValue) {
                    value = objectValue.get(f);
                    if (value instanceof ConfigValue.NullValue) {
                        var origin = objectValue.origin();
                        do {
                            origin = origin.child(parts.get(j));
                            j++;
                        } while (j < parts.size());
                        return new ConfigValue.NullValue(origin);
                    }
                } else {
                    throw ConfigValueException.unexpectedValueType(value, ConfigValue.ArrayValue.class);
                }
            } else {
                throw new IllegalStateException();
            }
        }
        return value;
    }
}

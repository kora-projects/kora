package ru.tinkoff.kora.config.common.factory;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValuePath;
import ru.tinkoff.kora.config.common.PathElement;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;
import ru.tinkoff.kora.config.common.impl.SimpleConfig;
import ru.tinkoff.kora.config.common.impl.SimpleConfigValueOrigin;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;

import java.util.*;

public class MapConfigFactory {
    public static Config fromMap(Map<String, ?> map) {
        return fromMap("Map@" + System.identityHashCode(map), map);
    }

    public static Config fromMap(String description, Map<String, ?> map) {
        var origin = new SimpleConfigOrigin(description);
        return fromMap(origin, map);
    }

    public static Config fromMap(ConfigOrigin origin, Map<String, ?> map) {
        var path = ConfigValuePath.root();

        var value = toObject(origin, map, path);
        return new SimpleConfig(origin, value);
    }

    public static Config fromProperties(Properties properties) {
        return fromProperties("Properties@" + System.identityHashCode(properties), properties);
    }

    public static Config fromProperties(String description, Properties properties) {
        var origin = new SimpleConfigOrigin(description);
        return fromProperties(origin, properties);
    }

    public static Config fromProperties(ConfigOrigin origin, Properties properties) {
        var map = new LinkedHashMap<String, Object>();

        for (var key : properties.stringPropertyNames()) {
            var path = ConfigValuePath.parse(key);
            var value = properties.getProperty(key);
            var _parts = new LinkedList<PathElement>();
            while (path != null) {
                if (path.last() == null) {
                    break;
                }
                _parts.addFirst(path.last());
                path = path.prev();
            }
            var parts = new ArrayList<>(_parts);
            var currentObject = (Object) map;
            for (int i = 0; i < parts.size(); i++) {
                var element = parts.get(i);
                if (element instanceof PathElement.Index index) {
                    @SuppressWarnings("unchecked")
                    var list = (List<Object>) currentObject;
                    for (int j = 0; j <= index.index() + 1; j++) {
                        if (list.size() < j) {
                            list.add(null);
                        }
                    }
                    if (list.size() < index.index() || list.get(index.index()) == null) {
                        if (i + 1 < parts.size()) {
                            var next = parts.get(i + 1);
                            if (next instanceof PathElement.Index) {
                                currentObject = new ArrayList<Object>();
                                list.set(index.index(), currentObject);
                            } else {
                                currentObject = new LinkedHashMap<String, Object>();
                                list.set(index.index(), currentObject);
                            }
                        } else {
                            list.set(index.index(), value);
                        }
                    } else {
                        var item = list.get(index.index());
                        if (i + 1 < parts.size()) {
                            currentObject = item;
                        } else {
                            list.set(index.index(), value);
                        }
                    }
                } else {
                    var field = (PathElement.Key) element;
                    if (!(currentObject instanceof Map<?, ?>)) {
                        var prev = (Object) map;
                        for (int j = 0; j < i - 1; j++) {
                            if (parts.get(j) instanceof PathElement.Key k) {
                                prev = ((Map<String, Object>) prev).get(k.name());
                            } else if (parts.get(j) instanceof PathElement.Index index) {
                                prev = ((List<Object>) prev).get(index.index());
                            } else {
                                throw new IllegalStateException();
                            }
                        }
                        var prevPath = parts.get(i - 1);
                        currentObject = new LinkedHashMap<String, Object>();
                        if (prevPath instanceof PathElement.Key k) {
                            ((Map<String, Object>) prev).put(k.name(), currentObject);
                        } else if (prevPath instanceof PathElement.Index index) {
                            ((List<Object>) prev).set(index.index(), currentObject);
                        } else {
                            throw new IllegalStateException();
                        }
                    }
                    var object = (Map<String, Object>) currentObject;
                    var currentValue = object.get(field.name());
                    if (currentValue == null) {
                        if (i + 1 < parts.size()) {
                            var next = parts.get(i + 1);
                            if (next instanceof PathElement.Index) {
                                currentObject = new ArrayList<Object>();
                                object.put(field.name(), currentObject);
                            } else {
                                currentObject = new LinkedHashMap<String, Object>();
                                object.put(field.name(), currentObject);
                            }
                        } else {
                            object.put(field.name(), value);
                        }
                    } else {
                        if (i + 1 < parts.size()) {
                            currentObject = currentValue;
                        } else if (!(currentValue instanceof Map<?, ?>)) {
                            object.put(field.name(), value);
                        }
                    }
                }
            }
        }
        return fromMap(origin, map);
    }

    private static ConfigValue<?> toValue(ConfigOrigin origin, Object object, ConfigValuePath path) {
        if (object instanceof ConfigValue<?> configValue) {
            return configValue;
        }
        if (object instanceof Config configValue) {
            return configValue.root();
        }
        if (object instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            var map = (Map<String, ?>) object;
            return toObject(origin, map, path);
        }
        if (object instanceof List<?> list) {
            return toList(origin, list, path);
        }
        var valueOrigin = new SimpleConfigValueOrigin(origin, path);
        if (object instanceof Number number) {
            return new ConfigValue.NumberValue(valueOrigin, number);
        }
        if (object instanceof String string) {
            return new ConfigValue.StringValue(valueOrigin, string);
        }
        if (object instanceof Boolean bool) {
            return new ConfigValue.BooleanValue(valueOrigin, bool);
        }
        if (object instanceof Enum<?> e) {
            return new ConfigValue.StringValue(valueOrigin, e.name());
        }
        throw new ConfigValueExtractionException(
            origin,
            "Unexpected object type with path %s: %s. Supported types are Map<String, ?>, List<?>, String, Number, Boolean and Enum<?>".formatted(path, object.getClass()),
            null
        );
    }

    private static ConfigValue.ObjectValue toObject(ConfigOrigin origin, Map<String, ?> object, ConfigValuePath path) {
        var result = new LinkedHashMap<String, ConfigValue<?>>();
        for (var entry : object.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), toValue(origin, entry.getValue(), path.child(entry.getKey())));
            }
        }

        return new ConfigValue.ObjectValue(new SimpleConfigValueOrigin(origin, path), result);
    }

    private static ConfigValue.ArrayValue toList(ConfigOrigin origin, List<?> list, ConfigValuePath path) {
        var result = new ArrayList<ConfigValue<?>>(list.size());
        for (var i = 0; i < list.size(); i++) {
            var configValue = list.get(i);
            if (configValue == null) {
                result.add(null);
            } else {
                result.add(toValue(origin, configValue, path.child(i)));
            }
        }
        return new ConfigValue.ArrayValue(new SimpleConfigValueOrigin(origin, path), Collections.unmodifiableList(result));
    }

}

package ru.tinkoff.kora.config.common.impl;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValuePath;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class ConfigResolver {
    private record ResolveContext(Config root, ArrayDeque<ConfigValuePath> chain) {}

    public static Config resolve(Config config) {
        var ctx = new ResolveContext(config, new ArrayDeque<>());
        var newRoot = resolve(ctx, config.root());
        if (newRoot == config.root()) {
            return config;
        }
        return new SimpleConfig(config.origin(), newRoot);
    }

    private static ConfigValue.ObjectValue resolve(ResolveContext ctx, ConfigValue.ObjectValue object) {
        var newContent = new LinkedHashMap<String, ConfigValue<?>>();
        var changed = false;
        for (var entry : object) {
            var key = entry.getKey();
            var value = entry.getValue();
            var newValue = resolve(ctx, value);
            newContent.put(key, newValue);
            if (newValue != value) {
                changed = true;
            }
        }
        if (!changed) {
            return object;
        }
        return new ConfigValue.ObjectValue(object.origin(), newContent);
    }

    private static ConfigValue.ArrayValue resolve(ResolveContext ctx, ConfigValue.ArrayValue array) {
        var newContent = new ArrayList<ConfigValue<?>>(array.value().size());
        var changed = false;
        for (var value : array) {
            var newValue = resolve(ctx, value);
            newContent.add(newValue);
            if (newValue != value) {
                changed = true;
            }
        }
        if (!changed) {
            return array;
        }
        return new ConfigValue.ArrayValue(array.origin(), newContent);
    }

    private static ConfigValue<?> resolve(ResolveContext ctx, ConfigValue<?> value) {
        if (value instanceof ConfigValue.ObjectValue objectValue) {
            return resolve(ctx, objectValue);
        }
        if (value instanceof ConfigValue.ArrayValue arrayValue) {
            return resolve(ctx, arrayValue);
        }
        if (value instanceof ConfigValue.NumberValue) {
            return value;
        }
        if (value instanceof ConfigValue.BooleanValue) {
            return value;
        }
        if (value instanceof ConfigValue.StringValue stringValue) {
            return resolve(ctx, stringValue);
        }
        throw new IllegalStateException("Unknown value type: " + value.getClass());
    }


    private static ConfigValue<?> resolve(ResolveContext ctx, ConfigValue.StringValue stringValue) {
        var buf = stringValue.value().toCharArray();
        enum Token {STRING, REFERENCE, REFERENCE_DEFAULT_VALUE, REFERENCE_NULLABLE}
        var parts = new ArrayList<ConfigValue<?>>();
        var token = Token.STRING;
        var tokenStart = 0;
        var prevTokenStart = 0;
        for (int i = 0; i < buf.length; i++) {
            var c = buf[i];
            if (c == '$' && buf.length > i + 2 && buf[i + 1] == '{' && (i == 0 || buf[i - 1] != '\\')) {
                if (token == Token.STRING) {
                    token = buf[i + 2] == '?'
                        ? Token.REFERENCE_NULLABLE
                        : Token.REFERENCE
                    ;
                    var strLen = i - tokenStart;
                    if (strLen > 0) {
                        parts.add(new ConfigValue.StringValue(stringValue.origin(), new String(buf, tokenStart, strLen)));
                    }
                    i++;
                    prevTokenStart = tokenStart;
                    tokenStart = i + 1;
                    if (token == Token.REFERENCE_NULLABLE) {
                        tokenStart++;
                    }
                    continue;
                }
                continue;
            }
            if (c == ':' && token == Token.REFERENCE) {
                token = Token.REFERENCE_DEFAULT_VALUE;
                prevTokenStart = tokenStart;
                tokenStart = i + 1;
                continue;
            }
            if (c == '}') {
                if (token == Token.REFERENCE) {
                    var ref = new String(buf, tokenStart, i - tokenStart);
                    var path = ConfigValuePath.parse(ref);
                    ctx.chain().add(path);
                    var value = ctx.root().get(path);
                    if (value instanceof ConfigValue.NullValue) {
                        throw new RuntimeException("Unresolved path: " + ref);
                    } else {
                        ctx.chain().push(path);
                        try {
                            parts.add(resolve(ctx, value));
                        } finally {
                            ctx.chain().pop();
                        }
                    }
                    prevTokenStart = tokenStart;
                    tokenStart = i + 1;
                    continue;
                }
                if (token == Token.REFERENCE_NULLABLE) {
                    var ref = new String(buf, tokenStart, i - tokenStart);
                    var path = ConfigValuePath.parse(ref);
                    ctx.chain().add(path);
                    var value = ctx.root().get(path);
                    if (value instanceof ConfigValue.NullValue) {
                        parts.add(value);
                    } else {
                        ctx.chain().push(path);
                        try {
                            parts.add(resolve(ctx, value));
                        } finally {
                            ctx.chain().pop();
                        }
                    }
                    prevTokenStart = tokenStart;
                    tokenStart = i + 1;
                    continue;
                }
                if (token == Token.REFERENCE_DEFAULT_VALUE) {
                    var ref = new String(buf, prevTokenStart, tokenStart - prevTokenStart - 1);
                    var defaultValue = new String(buf, tokenStart, i - tokenStart);
                    var path = ConfigValuePath.parse(ref);
                    var value = ctx.root().get(path);
                    if (value instanceof ConfigValue.NullValue) {
                        ctx.chain().push(path);
                        try {
                            parts.add(resolve(ctx, new ConfigValue.StringValue(stringValue.origin(), defaultValue)));
                        } finally {
                            ctx.chain().pop();
                        }
                    } else {
                        ctx.chain().push(path);
                        try {
                            parts.add(resolve(ctx, value));
                        } finally {
                            ctx.chain().pop();
                        }
                    }
                    prevTokenStart = tokenStart;
                    tokenStart = i + 1;
                    continue;
                }
            }
        }
        if (parts.size() == 0) {
            return stringValue;
        }
        if (tokenStart < buf.length) {
            parts.add(new ConfigValue.StringValue(stringValue.origin(), new String(buf, tokenStart, buf.length - tokenStart)));
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        var sb = new StringBuilder();
        for (var part : parts) {
            if (part instanceof ConfigValue.StringValue strValue) {
                sb.append(strValue.value());
            } else if (part instanceof ConfigValue.NumberValue numberValue) {
                sb.append(numberValue.value());
            } else {
                throw new RuntimeException("Unexpected type: " + part.getClass());// TODO
            }
        }
        var value = sb.toString();
        return new ConfigValue.StringValue(stringValue.origin(), value);
    }
}

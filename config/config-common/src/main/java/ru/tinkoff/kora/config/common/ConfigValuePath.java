package ru.tinkoff.kora.config.common;

import ru.tinkoff.kora.config.common.impl.SimpleConfigValuePath;

import java.util.Objects;
import java.util.regex.Pattern;

public interface ConfigValuePath {
    ConfigValuePath ROOT = new SimpleConfigValuePath(null, null);

    PathElement last();

    ConfigValuePath prev();

    static ConfigValuePath root() {
        return ROOT;
    }

    default ConfigValuePath child(PathElement path) {
        return new SimpleConfigValuePath(Objects.requireNonNull(path), this);
    }

    default ConfigValuePath child(String key) {
        return new SimpleConfigValuePath(new PathElement.Key(Objects.requireNonNull(key)), this);
    }

    default ConfigValuePath child(int index) {
        return new SimpleConfigValuePath(new PathElement.Index(index), this);
    }

    Pattern ARRAY_ELEMENT_PATTERN = Pattern.compile("(?<array>.*)\\[(?<number>\\d+)]");

    static ConfigValuePath parse(String reference) {
        if (reference.equals(".")) {
            return root();
        }
        var elements = reference.split("\\.");
        var path = root();
        for (var element : elements) {
            var elementPath = element.trim();
            var matcher = ARRAY_ELEMENT_PATTERN.matcher(elementPath);
            if (matcher.matches()) {
                var arrayPath = matcher.group(1);
                var arrayIndex = Integer.parseInt(matcher.group(2));
                path = path.child(arrayPath).child(arrayIndex);
            } else {
                path = path.child(elementPath);
            }
        }
        return path;
    }

}

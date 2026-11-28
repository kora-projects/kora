package io.koraframework.config.common;

import io.koraframework.config.common.impl.SimpleConfigValuePath;

import java.util.Objects;

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
        return new SimpleConfigValuePath(PathElement.get(Objects.requireNonNull(key)), this);
    }

    default ConfigValuePath child(int index) {
        return new SimpleConfigValuePath(new PathElement.Index(index), this);
    }

    static ConfigValuePath parse(String reference) {
        if (reference == null || reference.isEmpty()) {
            return root();
        }
        if (reference.equals(".")) {
            return root();
        }

        var path = root();
        int len = reference.length();
        int start = 0;

        while (start < len) {
            int dotIdx = reference.indexOf('.', start);
            int end = (dotIdx == -1) ? len : dotIdx;
            int currentStart = start;
            int currentEnd = end;
            while (currentStart < currentEnd && reference.charAt(currentStart) <= ' ') {
                currentStart++;
            }
            while (currentEnd > currentStart && reference.charAt(currentEnd - 1) <= ' ') {
                currentEnd--;
            }

            if (currentStart < currentEnd) {
                if (reference.charAt(currentEnd - 1) == ']') {
                    int openBracketIdx = reference.lastIndexOf('[', currentEnd - 2);
                    if (openBracketIdx > currentStart) {
                        String arrayPath = reference.substring(currentStart, openBracketIdx).trim();
                        String indexStr = reference.substring(openBracketIdx + 1, currentEnd - 1).trim();

                        int arrayIndex = Integer.parseInt(indexStr);

                        path = path.child(arrayPath).child(arrayIndex);
                    } else {
                        path = path.child(reference.substring(currentStart, currentEnd));
                    }
                } else {
                    path = path.child(reference.substring(currentStart, currentEnd));
                }
            }

            if (dotIdx == -1) {
                break;
            }
            start = dotIdx + 1;
        }

        return path;
    }}

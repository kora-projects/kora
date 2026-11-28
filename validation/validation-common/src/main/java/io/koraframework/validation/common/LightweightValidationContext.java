package io.koraframework.validation.common;

public final class LightweightValidationContext implements ValidationContext, ValidationContext.Path {

    private final boolean failFast;
    private final String[] keys;
    private final int[] indices;
    private int depth = 0;

    public LightweightValidationContext(int maxDepth, boolean failFast) {
        this.failFast = failFast;
        this.keys = new String[maxDepth];
        this.indices = new int[maxDepth];
    }

    @Override
    public ValidationContext addPath(String path) {
        keys[depth] = path;
        indices[depth] = -1;
        depth++;
        return this;
    }

    @Override
    public ValidationContext addPath(int pathIndex) {
        indices[depth] = pathIndex;
        depth++;
        return this;
    }

    public void popPath() {
        depth--;
        keys[depth] = null;
        indices[depth] = -1;
    }

    @Override
    public Path path() {
        return this;
    }

    @Override
    public boolean isFailFast() {
        return failFast;
    }

    @Override
    public String value() {
        if (depth == 0) return "";
        int lastIdx = depth - 1;
        int indexValue = indices[lastIdx];
        if (indexValue >= 0) return Integer.toString(indexValue);
        String keyValue = keys[lastIdx];
        return keyValue != null ? keyValue : "";
    }

    @Override
    public ValidationContext.Path root() {
        return null;
    }

    @Override
    public String full() {
        return toString();
    }

    @Override
    public String toString() {
        if (depth == 0) return "";
        var sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            int index = indices[i];
            if (index >= 0) {
                sb.append('[').append(index).append(']');
            } else {
                String key = keys[i];
                if (key != null && !key.isEmpty()) {
                    if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != ']') {
                        sb.append('.');
                    }
                    sb.append(key);
                }
            }
        }
        return sb.toString();
    }
}

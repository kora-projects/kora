package io.koraframework.application.graph;

import java.lang.reflect.*;
import java.util.*;

public final class TypeIntMap {
    private Type[] keys;
    private int[] values;
    private int mask;
    private int size;
    private final float loadFactor = 0.5f;

    public TypeIntMap(int capacity) {
        int tableSize = 1;
        while (tableSize < capacity) {
            tableSize <<= 1;
        }
        keys = new Type[tableSize];
        values = new int[tableSize];
        Arrays.fill(values, -1);
        mask = tableSize - 1;
    }

    private TypeIntMap(Type[] keys, int[] values, int mask, int size) {
        this.keys = keys;
        this.values = values;
        this.mask = mask;
        this.size = size;
    }

    public void put(Type key, int value) {
        if (size >= keys.length * loadFactor) {
            rehash();
        }
        int idx = hash(key) & mask;
        while (keys[idx] != null) {
            if (keys[idx].equals(key)) {
                values[idx] = value;
                return;
            }
            idx = (idx + 1) & mask;
        }
        keys[idx] = key;
        values[idx] = value;
        size++;
    }

    public int get(Type key) {
        int idx = hash(key) & mask;
        while (keys[idx] != null) {
            if (keys[idx].equals(key)) {
                return values[idx];
            }
            idx = (idx + 1) & mask;
        }
        return -1;
    }

    public TypeIntMap copy() {
        var newKeys = new Type[keys.length];
        var newValues = new int[values.length];
        System.arraycopy(keys, 0, newKeys, 0, keys.length);
        System.arraycopy(values, 0, newValues, 0, values.length);
        return new TypeIntMap(newKeys, newValues, mask, size);
    }

    private void rehash() {
        Type[] oldKeys = keys;
        int[] oldValues = values;
        int newCapacity = oldKeys.length << 1;

        keys = new Type[newCapacity];
        values = new int[newCapacity];
        Arrays.fill(values, -1);
        mask = newCapacity - 1;
        size = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != null) {
                put(oldKeys[i], oldValues[i]);
            }
        }
    }

    private int hash(Type key) {
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }
}

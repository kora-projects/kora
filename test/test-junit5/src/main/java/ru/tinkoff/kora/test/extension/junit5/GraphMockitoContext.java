package ru.tinkoff.kora.test.extension.junit5;

import org.mockito.internal.creation.MockSettingsImpl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

record GraphMockitoContext(Map<Object, MockSettingsImpl<?>> mocksMap,
                           Set<Object> spySet) {

    GraphMockitoContext() {
        this(new HashMap<>(), new HashSet<>());
    }

    public boolean isEmpty() {
        return mocksMap.isEmpty() && spySet.isEmpty();
    }
}

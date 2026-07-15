package io.koraframework.database.jdbc.mapper.result;

import java.util.HashSet;
import java.util.Set;

public final class SetCollectionFactory<T> implements CollectionFactory<T, Set<T>> {

    @Override
    public Set<T> create(int size) {
        return new HashSet<>(Math.max(2, (int) (size / 0.75f) + 1));
    }
}

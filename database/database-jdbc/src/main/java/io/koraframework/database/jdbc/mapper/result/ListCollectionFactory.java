package io.koraframework.database.jdbc.mapper.result;

import java.util.ArrayList;
import java.util.List;

public final class ListCollectionFactory<T> implements CollectionFactory<T, List<T>> {

    @Override
    public List<T> create(int size) {
        return new ArrayList<>(size);
    }
}

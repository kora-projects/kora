package io.koraframework.database.jdbc.mapper.result;

import java.util.Collection;

public interface CollectionFactory<T, C extends Collection<T>> {

    C create(int size);
}

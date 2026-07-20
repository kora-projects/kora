package io.koraframework.database.jdbc.mapper.result;

import io.koraframework.database.jdbc.ArrayColumnData;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

public class CollectionFromSqlArrayResultColumnMapper<T, C extends Collection<T>> implements JdbcResultColumnMapper<C> {

    private final ArrayColumnData<T> arrayColumnData;
    private final CollectionFactory<T, C> collectionFactory;

    public CollectionFromSqlArrayResultColumnMapper(ArrayColumnData<T> arrayColumnData,
                                                    CollectionFactory<T, C> collectionFactory) {
        this.arrayColumnData = arrayColumnData;
        this.collectionFactory = collectionFactory;
    }

    @Override
    public @Nullable C apply(ResultSet row, int index) throws SQLException {
        var sqlArray = row.getArray(index);
        if (row.wasNull()) {
            return null;
        }
        var raw = (Object[]) sqlArray.getArray();
        var result = collectionFactory.create(raw.length);
        for (var element : raw) {
            result.add(element == null ? null : arrayColumnData.fromDbElement().apply(element));
        }
        return result;
    }
}

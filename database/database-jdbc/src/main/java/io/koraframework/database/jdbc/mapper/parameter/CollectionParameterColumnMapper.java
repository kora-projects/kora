package io.koraframework.database.jdbc.mapper.parameter;

import io.koraframework.database.jdbc.ArrayColumnData;
import org.jspecify.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;

public class CollectionParameterColumnMapper<T, C extends Collection<T>> implements JdbcParameterColumnMapper<C> {

    private final ArrayColumnData<T> arrayColumnData;

    public CollectionParameterColumnMapper(ArrayColumnData<T> arrayColumnData) {
        this.arrayColumnData = arrayColumnData;
    }

    @Override
    public void set(PreparedStatement stmt, int index, @Nullable C value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.ARRAY);
            return;
        }
        var elements = new Object[value.size()];
        var i = 0;
        for (var element : value) {
            elements[i++] = element == null ? null : arrayColumnData.toDbElement().apply(element);
        }
        var array = stmt.getConnection().createArrayOf(arrayColumnData.sqlTypeName(), elements);
        stmt.setArray(index, array);
    }
}

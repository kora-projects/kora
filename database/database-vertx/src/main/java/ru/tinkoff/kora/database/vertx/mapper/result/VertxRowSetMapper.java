package ru.tinkoff.kora.database.vertx.mapper.result;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.database.common.UpdateCount;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface VertxRowSetMapper<T> extends Mapping.MappingFunction {
    T apply(RowSet<Row> rows);

    static UpdateCount extractUpdateCount(RowSet<Row> rowSet) {
        var result = 0L;
        while (rowSet != null) {
            result += rowSet.rowCount();
            rowSet = rowSet.next();
        }
        return new UpdateCount(result);
    }

    static <T> VertxRowSetMapper<Optional<T>> optionalRowSetMapper(VertxRowMapper<T> rowMapper) {
        return rows -> {
            if (rows.size() < 1) {
                return Optional.empty();
            }
            var row = rows.iterator().next();
            return Optional.ofNullable(rowMapper.apply(row));
        };
    }

    static <T> VertxRowSetMapper<List<T>> listRowSetMapper(VertxRowMapper<T> rowMapper) {
        return rows -> {
            var result = new ArrayList<T>(rows.size());
            for (var row : rows) {
                var value = rowMapper.apply(row);
                result.add(value);
            }
            return result;
        };
    }

    static <T> VertxRowSetMapper<T> singleRowSetMapper(VertxRowMapper<T> rowMapper) {
        return rows -> {
            if (rows.size() < 1) {
                return null;
            }
            var row = rows.iterator().next();
            return rowMapper.apply(row);
        };
    }
}

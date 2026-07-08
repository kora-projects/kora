package io.koraframework.database.cassandra;

import io.koraframework.database.common.QueryContext;
import io.koraframework.test.cassandra.CassandraParams;
import io.koraframework.test.cassandra.CassandraTestContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CassandraTestContainer.class)
class CassandraSessionTest {
    @Test
    public void testQuery(CassandraParams params) {
        params.execute("create table test_table(id int, value varchar, primary key (id));\n");
        params.execute("insert into test_table(id, value) values (1,'test1');\n");

        record Entity(Integer id, String value) {}
        var qctx = new QueryContext(
            "SELECT id, value FROM test_table WHERE value = :value allow filtering",
            "SELECT id, value FROM test_table WHERE value = ? allow filtering"
        );

        CassandraTestUtils.withDb(params, db -> {
            var result = db.query(qctx, stmt -> {
                var s = stmt.bind("test1");
                return db.currentSession().execute(s).map(row -> {
                    var __id = row.isNull("id") ? null : row.getInt("id");
                    var __value = row.getString("value");
                    return new Entity(__id, __value);
                });
            });
            Assertions.assertThat(result)
                .hasSize(1)
                .first()
                .isEqualTo(new Entity(1, "test1"));
        });
    }

    @Test
    public void testAsyncQuery(CassandraParams params) {
        params.execute("create table test_table(id int, value varchar, primary key (id));\n");
        params.execute("insert into test_table(id, value) values (1,'test1');\n");

        record Entity(Integer id, String value) {}
        var qctx = new QueryContext(
            "SELECT id, value FROM test_table WHERE value = :value allow filtering",
            "SELECT id, value FROM test_table WHERE value = ? allow filtering"
        );

        CassandraTestUtils.withDb(params, db -> {
            var result = db.query(qctx, stmt -> {
                var s = stmt.bind("test1");
                return db.currentSession().execute(s).map(row -> {
                    var __id = row.isNull("id") ? null : row.getInt("id");
                    var __value = row.getString("value");
                    return new Entity(__id, __value);
                });
            });

            Assertions.assertThat(result)
                .hasSize(1)
                .first()
                .isEqualTo(new Entity(1, "test1"));

        });
    }
}

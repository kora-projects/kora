package io.koraframework.database.cassandra;

import io.koraframework.database.cassandra.mapper.result.CassandraRowMapper;
import io.koraframework.test.cassandra.CassandraParams;
import io.koraframework.test.cassandra.CassandraTestContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(CassandraTestContainer.class)
class CassandraQueryExecutorTest {

    @Test
    void testCassandraQuery(CassandraParams params) {
        params.execute("create table test_table_query(id int, value varchar, status varchar, primary key (id));\n");
        params.execute("insert into test_table_query(id, value, status) values (1,'test1','active');\n");
        params.execute("insert into test_table_query(id, value, status) values (2,'test2','archived');\n");

        record Entity(Integer id, String value) {}

        CassandraTestUtils.withDb(params, db -> {
            var status = "active";
            var query = CassandraQuery.named()
                .cql("SELECT id, value FROM test_table_query WHERE id = :id")
                .cqlIf(" AND status = :status", status != null)
                .bindIf("status", status, status != null)
                .cql(" ALLOW FILTERING")
                .bind("id", 1)
                .build();
            CassandraRowMapper<Entity> mapper = row -> {
                var __id = row.isNull("id") ? null : row.getInt("id");
                var __value = row.getString("value");
                return new Entity(__id, __value);
            };

            var result = db.queryList(query, mapper);

            Assertions.assertThat(result)
                .hasSize(1)
                .first()
                .isEqualTo(new Entity(1, "test1"));

            var one = db.queryOne(
                CassandraQuery.named()
                    .cql("SELECT id, value FROM test_table_query WHERE id = :id")
                    .bind("id", 1)
                    .build(),
                mapper
            );
            Assertions.assertThat(one).isEqualTo(new Entity(1, "test1"));

            var optional = db.queryOptional(
                CassandraQuery.named()
                    .cql("SELECT id, value FROM test_table_query WHERE id = :id")
                    .bind("id", 999)
                    .build(),
                mapper
            );
            Assertions.assertThat(optional).isEmpty();
        });
    }

    @Test
    void testCassandraQueryBindIn(CassandraParams params) {
        params.execute("create table test_table_query_in(id int, value varchar, primary key (id));\n");
        params.execute("insert into test_table_query_in(id, value) values (1,'test1');\n");
        params.execute("insert into test_table_query_in(id, value) values (2,'test2');\n");
        params.execute("insert into test_table_query_in(id, value) values (3,'test3');\n");

        record Entity(Integer id, String value) {}

        CassandraTestUtils.withDb(params, db -> {
            var query = CassandraQuery.named()
                .cql("SELECT id, value FROM test_table_query_in WHERE id IN (:ids)")
                .bindIn("ids", List.of(1, 3))
                .build();
            CassandraRowMapper<Entity> mapper = row -> new Entity(
                row.isNull("id") ? null : row.getInt("id"),
                row.getString("value")
            );

            var result = db.queryList(query, mapper);

            Assertions.assertThat(result)
                .containsExactlyInAnyOrder(new Entity(1, "test1"), new Entity(3, "test3"));
        });
    }
}

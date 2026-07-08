package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

class CassandraQueryTest {

    @Test
    void testNamedQueryBuilder() {
        var status = "active";
        String value = null;
        var query = CassandraQuery.named()
            .cql("SELECT * FROM users WHERE id = :id")
            .cqlIf(" AND status = :status", status != null)
            .bindIf("status", status, status != null)
            .cqlIf(" AND value = :value", value != null)
            .bindIf("value", value, value != null)
            .cqlIf(" ALLOW FILTERING", true)
            .bind("id", 1)
            .build();

        Assertions.assertThat(query.sourceCql())
            .isEqualTo("SELECT * FROM users WHERE id = :id AND status = :status ALLOW FILTERING");
        Assertions.assertThat(query.cql())
            .isEqualTo("SELECT * FROM users WHERE id = ? AND status = ? ALLOW FILTERING");
        Assertions.assertThat(query.parameters().stream().map(CassandraQuery.Parameter::value))
            .containsExactly(1, "active");
    }

    @Test
    void testNamedBindIn() {
        var query = CassandraQuery.named()
            .cql("SELECT * FROM users WHERE tenant_id = :tenant_id AND id IN (:ids)")
            .bind("tenant_id", "tenant-1")
            .bindIn("ids", List.of(1, 2, 3))
            .build();

        Assertions.assertThat(query.sourceCql())
            .isEqualTo("SELECT * FROM users WHERE tenant_id = :tenant_id AND id IN (:ids)");
        Assertions.assertThat(query.cql())
            .isEqualTo("SELECT * FROM users WHERE tenant_id = ? AND id IN (?, ?, ?)");
        Assertions.assertThat(query.parameters().stream().map(CassandraQuery.Parameter::value))
            .containsExactly("tenant-1", 1, 2, 3);
    }

    @Test
    void testNamedBindKeepsCollectionAsSingleParameter() {
        var ids = List.of(1, 2, 3);
        var query = CassandraQuery.named()
            .cql("SELECT :ids")
            .bind("ids", ids)
            .build();

        Assertions.assertThat(query.cql()).isEqualTo("SELECT ?");
        Assertions.assertThat(query.parameters().stream().map(CassandraQuery.Parameter::value))
            .containsExactly(ids);
    }

    @Test
    void testTemplateQueryBuilder() {
        var query = CassandraQuery.template()
            .cql("SELECT * FROM users WHERE tenant_id = ?")
            .bind("tenant-1")
            .cqlIf(" AND status = ?", true, "active")
            .cqlIf(" AND value = ?", false, "test")
            .build();

        Assertions.assertThat(query.sourceCql()).isEqualTo("SELECT * FROM users WHERE tenant_id = ? AND status = ?");
        Assertions.assertThat(query.cql()).isEqualTo("SELECT * FROM users WHERE tenant_id = ? AND status = ?");
        Assertions.assertThat(query.parameters().stream().map(CassandraQuery.Parameter::value))
            .containsExactly("tenant-1", "active");
    }

    @Test
    void testTemplateFactory() {
        var query = CassandraQuery.template(
            "SELECT * FROM users WHERE tenant_id = ? AND id = ?",
            "tenant-1",
            42
        );

        Assertions.assertThat(query.cql()).isEqualTo("SELECT * FROM users WHERE tenant_id = ? AND id = ?");
        Assertions.assertThat(query.parameters().stream().map(CassandraQuery.Parameter::value))
            .containsExactly("tenant-1", 42);
    }

    @Test
    void testCassandraQueryOptions() {
        var options = CassandraQuery.OptsBuilder.builder()
            .consistencyLevel(ConsistencyLevel.LOCAL_QUORUM)
            .serialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL)
            .pageSize(100)
            .timeout(Duration.ofSeconds(5))
            .idempotent(true)
            .tracing(true)
            .build();
        var query = CassandraQuery.named()
            .cql("SELECT * FROM users WHERE id = :id")
            .bind("id", 1)
            .opts(options)
            .build();

        Assertions.assertThat(query.options()).isEqualTo(options);
        Assertions.assertThat(query.options().consistencyLevel()).isEqualTo(ConsistencyLevel.LOCAL_QUORUM);
        Assertions.assertThat(query.options().serialConsistencyLevel()).isEqualTo(ConsistencyLevel.LOCAL_SERIAL);
        Assertions.assertThat(query.options().pageSize()).isEqualTo(100);
        Assertions.assertThat(query.options().timeout()).isEqualTo(Duration.ofSeconds(5));
        Assertions.assertThat(query.options().idempotent()).isTrue();
        Assertions.assertThat(query.options().tracing()).isTrue();
    }

    @Test
    void testMissingParameterFails() {
        Assertions.assertThatThrownBy(() -> CassandraQuery.named()
            .cql("SELECT * FROM users WHERE id = :id")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Parameter 'id' is not specified");
    }

    @Test
    void testUnusedParameterFails() {
        Assertions.assertThatThrownBy(() -> CassandraQuery.named()
            .cql("SELECT * FROM users")
            .bind("id", 1)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Parameter 'id' is not used in CQL");
    }

    @Test
    void testEmptyBindInFails() {
        Assertions.assertThatThrownBy(() -> CassandraQuery.named()
            .cql("SELECT * FROM users WHERE id IN (:ids)")
            .bindIn("ids", List.of())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Parameter 'ids' collection is empty");
    }
}

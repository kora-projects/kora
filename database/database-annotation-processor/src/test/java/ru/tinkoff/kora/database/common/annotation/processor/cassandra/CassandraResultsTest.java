package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.cassandra.CassandraConnectionFactory;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraReactiveResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.repository.AllowedReactiveResultsRepository;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.repository.AllowedResultsRepository;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.JdbcEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CassandraResultsTest {
    private final TestContext ctx = new TestContext();
    private final MockCassandraExecutor executor = new MockCassandraExecutor();
    private final AllowedResultsRepository repository;
    private final AllowedReactiveResultsRepository reactiveRepository;

    public CassandraResultsTest() {
        ctx.addContextElement(TypeRef.of(CassandraConnectionFactory.class), executor);
        ctx.addMock(TypeRef.of(CassandraEntity.TestEntityCassandraRowMapperNonFinal.class));
        ctx.addMock(TypeRef.of(CassandraEntity.TestEntityFieldCassandraResultColumnMapperNonFinal.class));
        ctx.addMock(TypeRef.of(CassandraResultSetMapper.class, Integer.class));
        ctx.addMock(TypeRef.of(CassandraReactiveResultSetMapper.class, Void.class, TypeRef.of(Mono.class, Void.class)));
        ctx.addMock(TypeRef.of(CassandraReactiveResultSetMapper.class, Integer.class, TypeRef.of(Mono.class, Integer.class)));
        ctx.addMock(TypeRef.of(CassandraReactiveResultSetMapper.class, Integer.class, TypeRef.of(Flux.class, Integer.class)));
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedResultsRepository.class));
        reactiveRepository = ctx.newInstance(DbTestUtils.compileClass(AllowedReactiveResultsRepository.class));
    }

    @Test
    void testReturnVoid() {
        repository.returnVoid();
    }

    @Test
    void testReturnNativeType() {
        var mapper = ctx.findInstance(TypeRef.of(CassandraResultSetMapper.class, Integer.class));
        when(mapper.apply(any())).thenReturn(42);

        assertThat(repository.returnPrimitive()).isEqualTo(42);
        verify(mapper).apply(any());
    }
//
//    @Test
//    void testReturnNativeTypeBoxed() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getInt(0)).thenReturn(42);
//        when(executor.iterator.next().isNull(0)).thenReturn(false);
//        assertThat(repository.returnNativeTypeBoxed()).isEqualTo(42);
//    }
//
//    @Test
//    void testReturnNativeTypeNullable() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getInt(0)).thenReturn(42);
//        when(executor.iterator.next().isNull(0)).thenReturn(false);
//        assertThat(repository.returnNativeTypeNullable()).isEqualTo(42);
//
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getInt(0)).thenReturn(-1);
//        when(executor.iterator.next().isNull(0)).thenReturn(true);
//        assertThat(repository.returnNativeTypeNullable()).isNull();
//
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnNativeTypeNullable()).isNull();
//    }
//
//    @Test
//    void testReturnEntity() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getString("param1")).thenReturn("test1");
//        when(executor.iterator.next().getInt("param2")).thenReturn(42);
//        when(executor.iterator.next().getString("param3")).thenReturn(null);
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false, true);
//        assertThat(repository.returnEntity()).isEqualTo(new AllowedResultsRepository.Entity("test1", 42, null));
//
//    }
//
//    @Test
//    void testReturnMutableEntity() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getString("param1")).thenReturn("test1");
//        when(executor.iterator.next().getInt("param2")).thenReturn(42);
//        when(executor.iterator.next().getString("param3")).thenReturn(null);
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false, true);
//        assertThat(repository.returnMutableEntity())
//            .hasFieldOrPropertyWithValue("param1", "test1")
//            .hasFieldOrPropertyWithValue("param2", 42)
//            .hasFieldOrPropertyWithValue("param3", null);
//    }
//
//    @Test
//    void testReturnNullableEntity() {
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnNullableEntity()).isNull();
//    }
//
//    @Test
//    void testReturnEntityRowMapper() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getString(0)).thenReturn("test1");
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false, true);
//        assertThat(repository.returnEntityRowMapper()).isEqualTo(new AllowedResultsRepository.MappedEntity("test1"));
//    }
//
//    @Test
//    void testReturnEntityResultSetMapper() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getString(0)).thenReturn("test1");
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false, true);
//        assertThat(repository.returnEntityResultSetMapper()).isEqualTo(new AllowedResultsRepository.MappedEntity("test1"));
//    }
//
//    @Test
//    void testReturnNativeTypeOptional() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getInt(0)).thenReturn(42);
//        when(executor.iterator.next().isNull(0)).thenReturn(false);
//        assertThat(repository.returnNativeTypeOptional()).contains(42);
//
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getInt(0)).thenReturn(-1);
//        when(executor.iterator.next().isNull(0)).thenReturn(true);
//        assertThat(repository.returnNativeTypeOptional()).isEmpty();
//
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnNativeTypeOptional()).isEmpty();
//    }
//
//    @Test
//    void testReturnOptionalEntity() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getString("param1")).thenReturn("test1");
//        when(executor.iterator.next().getInt("param2")).thenReturn(42);
//        when(executor.iterator.next().getString("param3")).thenReturn(null);
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false, true);
//        assertThat(repository.returnOptionalEntity()).contains(new AllowedResultsRepository.Entity("test1", 42, null));
//
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnOptionalEntity()).isEmpty();
//    }
//
//    @Test
//    void testReturnOptionalWithRowMapper() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getString(0)).thenReturn("test1");
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false, true);
//        assertThat(repository.returnOptionalWithRowMapper()).contains(new AllowedResultsRepository.MappedEntity("test1"));
//
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnOptionalWithRowMapper()).isEmpty();
//    }
//
//    @Test
//    void testReturnOptionalWithResultSetMapper() {
//        when(executor.iterator.hasNext()).thenReturn(true);
//        when(executor.iterator.next().getString(0)).thenReturn("test1");
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false, true);
//        assertThat(repository.returnOptionalWithResultSetMapper()).contains(new AllowedResultsRepository.MappedEntity("test1"));
//
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnOptionalWithResultSetMapper()).isEmpty();
//    }
//
//    @Test
//    void testReturnNativeTypeList() {
//        when(executor.iterator.hasNext()).thenReturn(true, true, true, false);
//        when(executor.iterator.next().getInt(0)).thenReturn(42, 43, 44);
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false, false);
//        assertThat(repository.returnNativeTypeList()).containsExactly(42, 43, 44);
//
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnNativeTypeList()).isEmpty();
//    }
//
//    @Test
//    void testReturnListEntity() {
//        when(executor.iterator.hasNext()).thenReturn(true, true, true, false);
//        when(executor.iterator.next().getString("param1")).thenReturn("test1", "test2", "test3");
//        when(executor.iterator.next().getInt("param2")).thenReturn(42, 43, 44);
//        when(executor.iterator.next().getString("param3")).thenReturn(null);
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false, true, false, false, true, false, false, true);
//        assertThat(repository.returnListEntity()).containsExactly(
//            new AllowedResultsRepository.Entity("test1", 42, null),
//            new AllowedResultsRepository.Entity("test2", 43, null),
//            new AllowedResultsRepository.Entity("test3", 44, null)
//        );
//
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnListEntity()).isEmpty();
//    }
//
//    @Test
//    void testReturnListWithRowMapper() {
//        when(executor.iterator.hasNext()).thenReturn(true, true, false);
//        when(executor.iterator.next().getString(0)).thenReturn("val1", "val2");
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false);
//        assertThat(repository.returnListWithRowMapper()).containsExactly(new AllowedResultsRepository.MappedEntity("val1"), new AllowedResultsRepository.MappedEntity("val2"));
//
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnListWithRowMapper()).isEmpty();
//    }
//
//    @Test
//    void testReturnListWithResultSetMapper() {
//        when(executor.iterator.hasNext()).thenReturn(true, true, false);
//        when(executor.iterator.next().getString(0)).thenReturn("val1", "val2");
//        when(executor.iterator.next().isNull(0)).thenReturn(false, false);
//        assertThat(repository.returnListWithResultSetMapper()).containsExactly(new AllowedResultsRepository.MappedEntity("val1"), new AllowedResultsRepository.MappedEntity("val2"));
//
//        when(executor.iterator.hasNext()).thenReturn(false);
//        assertThat(repository.returnListWithResultSetMapper()).isEmpty();
//    }

}

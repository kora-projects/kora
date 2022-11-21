package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.vertx.repository.AllowedResultsRepository;
import ru.tinkoff.kora.database.vertx.VertxConnectionFactory;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VertxResultsTest {
    private final MockVertxExecutor executor = new MockVertxExecutor();
    private final TestContext ctx = new TestContext();
    private final AllowedResultsRepository repository;

    public VertxResultsTest() {
        ctx.addContextElement(TypeRef.of(VertxConnectionFactory.class), executor);
        ctx.addMock(TypeRef.of(VertxRowSetMapper.class, Void.class));
        ctx.addMock(TypeRef.of(VertxRowSetMapper.class, TestEntityRecord.class));
        ctx.addMock(TypeRef.of(VertxEntity.TestEntityVertxRowMapperNonFinal.class));
        this.repository = ctx.newInstance(DbTestUtils.compileClass(AllowedResultsRepository.class));
    }

    @BeforeEach
    void setUp() {
        executor.reset();
    }

    @Test
    void testReturnVoid() {
        repository.returnVoid();
    }
    /*

    @Test
    void testReturnNativeType() {
        executor.setRow(new MockColumn("test", 42));
        assertThat(repository.returnNativeType().block()).isEqualTo(42);

        executor.setRow(new MockColumn("test", null));
        assertThatThrownBy(repository.returnNativeType()::block);

        executor.setRows(List.of());
        assertThat(repository.returnNativeType().block()).isNull();
    }

    @Test
    void testReturnEntity() {
        executor.setRow(List.of(
            new MockColumn("param1", "test1"),
            new MockColumn("param2", 42),
            new MockColumn("param3", null)
        ));
        assertThat(repository.returnEntity().block()).isEqualTo(new Entity("test1", 42, null));

        executor.setRows(List.of());
        assertThat(repository.returnEntity().block()).isNull();

        executor.setRow(List.of(
            new MockColumn("param1", null),
            new MockColumn("param2", 42),
            new MockColumn("param3", null)
        ));
        assertThatThrownBy(repository.returnEntity()::block).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testReturnMutableEntity() {
        executor.setRow(List.of(
            new MockColumn("param1", "test1"),
            new MockColumn("param2", 42),
            new MockColumn("param3", null)
        ));
        assertThat(repository.returnMutableEntity().block())
            .hasFieldOrPropertyWithValue("param1", "test1")
            .hasFieldOrPropertyWithValue("param2", 42)
            .hasFieldOrPropertyWithValue("param3", null);

        executor.setRows(List.of());
        assertThat(repository.returnMutableEntity().block()).isNull();

        executor.setRow(List.of(
            new MockColumn("param1", null),
            new MockColumn("param2", 42),
            new MockColumn("param3", null)
        ));
        assertThatThrownBy(repository.returnMutableEntity()::block).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testReturnEntityWithMappedRow() {
        executor.setRow(List.of(
            new MockColumn("param1", "test1"),
            new MockColumn("param2", "test2")
        ));

        assertThat(repository.returnEntityWithMappedRow().block()).isEqualTo(new AllowedResultsRepository.EntityWithMappedColumn("test1", new AllowedResultsRepository.MappedEntityColumn("test2")));
    }

    @Test
    void testReturnEntityRowMapper() {
        executor.setRow(
            new MockColumn("param1", "test1")
        );
        assertThat(repository.returnEntityRowMapper().block()).isEqualTo(new AllowedResultsRepository.MappedEntity("test1"));
    }

    @Test
    void testReturnColumnMapper() {
        executor.setRow(
            new MockColumn("test_column", "test1")
        );
        assertThat(repository.returnColumnMapper().block()).isEqualTo(new AllowedResultsRepository.MappedEntityColumn("test1"));
    }

    @Test
    void testReturnEntityRowSetMapper() {
        executor.setRow(
            new MockColumn("test_column", "test1")
        );
        assertThat(repository.returnEntityRowSetMapper().block()).isEqualTo(new AllowedResultsRepository.MappedEntity("test1"));
    }


    @Test
    void testReturnNativeTypeList() {
        executor.setRows(List.of(
            List.of(new MockColumn("test_column", 42)),
            List.of(new MockColumn("test_column", 43)),
            List.of(new MockColumn("test_column", 44))
        ));
        assertThat(repository.returnNativeTypeList().block()).containsExactly(42, 43, 44);

        executor.setRows(List.of());
        assertThat(repository.returnNativeTypeList().block()).isEmpty();
    }


    @Test
    void testReturnListEntity() {
        executor.setRows(List.of(
            List.of(new MockColumn("param1", "test1"), new MockColumn("param2", 42), new MockColumn("param3", null)),
            List.of(new MockColumn("param1", "test2"), new MockColumn("param2", 43), new MockColumn("param3", null)),
            List.of(new MockColumn("param1", "test3"), new MockColumn("param2", 44), new MockColumn("param3", null))
        ));

        assertThat(repository.returnListEntity().block()).containsExactly(
            new Entity("test1", 42, null),
            new Entity("test2", 43, null),
            new Entity("test3", 44, null)
        );

        executor.setRows(List.of());
        assertThat(repository.returnListEntity().block()).isEmpty();
    }

    @Test
    void testReturnListWithRowMapper() {
        executor.setRows(List.of(
            List.of(new MockColumn("param1", "val1")),
            List.of(new MockColumn("param1", "val2"))
        ));

        assertThat(repository.returnListWithRowMapper().block()).containsExactly(new AllowedResultsRepository.MappedEntity("val1"), new AllowedResultsRepository.MappedEntity("val2"));
    }

    @Test
    void testReturnListWithRowSetMapper() {
        executor.setRows(List.of(
            List.of(new MockColumn("param1", "val1")),
            List.of(new MockColumn("param1", "val2"))
        ));
        assertThat(repository.returnListWithRowSetMapper().block()).containsExactly(new AllowedResultsRepository.MappedEntity("val1"), new AllowedResultsRepository.MappedEntity("val2"));
    }

     */

}

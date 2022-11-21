package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.vertx.repository.AllowedParametersRepository;
import ru.tinkoff.kora.database.vertx.VertxConnectionFactory;
import ru.tinkoff.kora.database.vertx.mapper.parameter.VertxParameterColumnMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VertxParametersTest {
    private final MockVertxExecutor executor = new MockVertxExecutor();
    private final TestContext ctx = new TestContext();
    private final AllowedParametersRepository repository;

    public VertxParametersTest() {
        ctx.addContextElement(TypeRef.of(VertxConnectionFactory.class), executor);
        ctx.addMock(TypeRef.of(VertxRowSetMapper.class, Void.class));
        ctx.addMock(TypeRef.of(VertxParameterColumnMapper.class, TestEntityRecord.UnknownTypeField.class));
        ctx.addMock(TypeRef.of(VertxParameterColumnMapper.class, byte[].class));
        ctx.addMock(TypeRef.of(VertxEntity.TestEntityFieldVertxParameterColumnMapperNonFinal.class));
        this.repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository.class));
    }

    @BeforeEach
    void setUp() {
        executor.reset();
    }


    @Test
    void testConnectionParameter() {
        repository.connectionParameter(executor.connection);
    }


    @Test
    void testParametersWithSimilarNames() {
        repository.parametersWithSimilarNames("test", 42).block();

        Mockito.verify(executor.connection).prepare(eq("INSERT INTO test(value1, value2) VALUES ($1, $2)"), Mockito.<Handler<AsyncResult<PreparedStatement>>>any());
        Mockito.verify(executor.query).execute(ArgumentMatchers.argThat(argument -> Tuple.of("test", 42).deepToString().equals(argument.deepToString())), any());
    }
}

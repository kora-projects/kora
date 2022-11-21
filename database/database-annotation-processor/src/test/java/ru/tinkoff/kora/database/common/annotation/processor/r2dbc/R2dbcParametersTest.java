package ru.tinkoff.kora.database.common.annotation.processor.r2dbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.TestContext;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.DbTestUtils;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.r2dbc.repository.AllowedParametersRepository;
import ru.tinkoff.kora.database.r2dbc.R2dbcConnectionFactory;
import ru.tinkoff.kora.database.r2dbc.mapper.parameter.R2dbcParameterColumnMapper;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper;

import java.util.concurrent.Executor;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class R2dbcParametersTest {
    private final MockR2dbcExecutor executor = new MockR2dbcExecutor();
    private final AllowedParametersRepository repository;
    private final TestContext ctx = new TestContext();

    public R2dbcParametersTest() {
        ctx.addContextElement(TypeRef.of(R2dbcConnectionFactory.class), executor);
        ctx.addContextElement(TypeRef.of(Executor.class), Runnable::run);
        ctx.addMock(TypeRef.of(R2dbcResultFluxMapper.class, Void.class, TypeRef.of(Mono.class, Void.class)));
        ctx.addMock(TypeRef.of(R2dbcParameterColumnMapper.class, TestEntityRecord.UnknownTypeField.class));
        ctx.addMock(TypeRef.of(R2dbcEntity.TestEntityFieldR2dbcParameterColumnMapperNonFinal.class));
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository.class));
    }

    @BeforeEach
    void setUp() {
        executor.reset();
    }

    @Test
    void testConnectionParameter() {
        repository.connectionParameter(executor.con);
    }

    @Test
    void testParametersWithSimilarNames() {
        Mockito.when(ctx.findInstance(TypeRef.of(R2dbcResultFluxMapper.class, Void.class, TypeRef.of(Mono.class, Void.class))).apply(ArgumentMatchers.any())).thenReturn(Mono.empty());

        repository.parametersWithSimilarNames("test", 42).block();

        Mockito.verify(executor.con).createStatement("INSERT INTO test(value1, value2) VALUES ($1, $2)");
        Mockito.verify(executor.statement).bind(0, "test");
        Mockito.verify(executor.statement).bind(1, 42);
    }
}

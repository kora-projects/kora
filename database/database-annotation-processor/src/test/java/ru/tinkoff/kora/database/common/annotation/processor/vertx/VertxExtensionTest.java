package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.vertx.VertxEntity.AllNativeTypesEntity;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxResultColumnMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper;
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper;

import java.util.List;

public class VertxExtensionTest {
    @Test
    void testEntity() throws Exception {
        TestUtils.testKoraExtension(new TypeRef<?>[]{
                TypeRef.of(VertxRowSetMapper.class, TypeRef.of(List.class, TestEntityRecord.class)),
                TypeRef.of(VertxRowMapper.class, TestEntityRecord.class),
                TypeRef.of(VertxRowSetMapper.class, TypeRef.of(List.class, TestEntityJavaBean.class)),
                TypeRef.of(VertxRowMapper.class, TestEntityJavaBean.class),
                TypeRef.of(VertxRowSetMapper.class, TypeRef.of(List.class, AllNativeTypesEntity.class)),
                TypeRef.of(VertxRowMapper.class, AllNativeTypesEntity.class),
            },
            TypeRef.of(VertxResultColumnMapper.class, TestEntityRecord.UnknownTypeField.class),
            TypeRef.of(VertxEntity.TestEntityFieldVertxResultColumnMapperNonFinal.class)
        );
    }
}

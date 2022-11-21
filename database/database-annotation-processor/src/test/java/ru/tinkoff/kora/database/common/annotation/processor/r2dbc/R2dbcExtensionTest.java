package ru.tinkoff.kora.database.common.annotation.processor.r2dbc;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityJavaBean;
import ru.tinkoff.kora.database.common.annotation.processor.entity.TestEntityRecord;
import ru.tinkoff.kora.database.common.annotation.processor.r2dbc.R2dbcEntity.AllNativeTypesEntity;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultColumnMapper;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcRowMapper;

public class R2dbcExtensionTest {
    @Test
    void testEntity() throws Exception {
        TestUtils.testKoraExtension(new TypeRef<?>[]{
                TypeRef.of(R2dbcRowMapper.class, TestEntityRecord.class),
                TypeRef.of(R2dbcRowMapper.class, TestEntityJavaBean.class),
                TypeRef.of(R2dbcRowMapper.class, AllNativeTypesEntity.class),
            },
            TypeRef.of(R2dbcResultColumnMapper.class, TestEntityRecord.UnknownTypeField.class),
            TypeRef.of(R2dbcEntity.TestEntityFieldR2dbcResultColumnMapperNonFinal.class)
        );
    }
}

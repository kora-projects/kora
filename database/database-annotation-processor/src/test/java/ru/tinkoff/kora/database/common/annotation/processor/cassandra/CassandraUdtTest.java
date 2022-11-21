package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraUdtAnnotationProcessor;
import ru.tinkoff.kora.database.common.annotation.processor.cassandra.repository.UdtRecords;

public class CassandraUdtTest {


    @Test
    void testEntity() throws Exception {
        TestUtils.annotationProcess(UdtRecords.class, new CassandraUdtAnnotationProcessor());
    }
}

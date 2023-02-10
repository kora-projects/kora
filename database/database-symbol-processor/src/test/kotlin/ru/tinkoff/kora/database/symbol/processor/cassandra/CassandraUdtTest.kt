package ru.tinkoff.kora.database.symbol.processor.cassandra

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ClassAssert
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper
import ru.tinkoff.kora.database.symbol.processor.cassandra.repository.Udt
import ru.tinkoff.kora.database.symbol.processor.cassandra.udt.CassandraUdtSymbolProcessorProvider
import ru.tinkoff.kora.ksp.common.symbolProcess
import kotlin.reflect.KClass

class CassandraUdtTest {
    @Test
    fun testUdt() {
        val cl = symbolProcess(Udt::class, CassandraUdtSymbolProcessorProvider())
        assertThat(cl.loadClass("ru.tinkoff.kora.database.symbol.processor.cassandra.repository.\$Udt_UdtEntity_CassandraRowColumnMapper"))
            .isNotNull
            .implements(CassandraRowColumnMapper::class)
        assertThat(cl.loadClass("ru.tinkoff.kora.database.symbol.processor.cassandra.repository.\$Udt_UdtEntity_List_CassandraRowColumnMapper"))
            .isNotNull
            .implements(CassandraRowColumnMapper::class)
        assertThat(cl.loadClass("ru.tinkoff.kora.database.symbol.processor.cassandra.repository.\$Udt_UdtEntity_CassandraParameterColumnMapper"))
            .isNotNull
            .implements(CassandraParameterColumnMapper::class)
        assertThat(cl.loadClass("ru.tinkoff.kora.database.symbol.processor.cassandra.repository.\$Udt_UdtEntity_List_CassandraParameterColumnMapper"))
            .isNotNull
            .implements(CassandraParameterColumnMapper::class)
        assertThat(cl.loadClass("ru.tinkoff.kora.database.symbol.processor.cassandra.repository.\$Udt_InnerUdt_CassandraRowColumnMapper"))
            .isNotNull
            .implements(CassandraRowColumnMapper::class)
        assertThat(cl.loadClass("ru.tinkoff.kora.database.symbol.processor.cassandra.repository.\$Udt_InnerUdt_CassandraParameterColumnMapper"))
            .isNotNull
            .implements(CassandraParameterColumnMapper::class)
        assertThat(cl.loadClass("ru.tinkoff.kora.database.symbol.processor.cassandra.repository.\$Udt_DeepUdt_CassandraRowColumnMapper"))
            .isNotNull
            .implements(CassandraRowColumnMapper::class)
        assertThat(cl.loadClass("ru.tinkoff.kora.database.symbol.processor.cassandra.repository.\$Udt_DeepUdt_CassandraParameterColumnMapper"))
            .isNotNull
            .implements(CassandraParameterColumnMapper::class)
    }

    private fun ClassAssert.implements(expectedSuperinterface: KClass<*>) {
        this.`is`(Condition({
            for (superinterface in it.interfaces) {
                if (superinterface.canonicalName == expectedSuperinterface.qualifiedName) {
                    return@Condition true
                }
            }
            false
        }, "Must implement interface ${expectedSuperinterface.qualifiedName}")
        )
    }
}

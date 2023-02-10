package ru.tinkoff.kora.database.symbol.processor.cassandra.repository

import ru.tinkoff.kora.database.cassandra.UDT

class Udt {
    @UDT
    data class UdtEntity(val string: String, val innerUdt: InnerUdt)

    @UDT
    data class InnerUdt(val id: Int, val deep: DeepUdt)

    @UDT
    data class DeepUdt(val doubleValue: Double?)
}

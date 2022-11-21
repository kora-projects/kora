package ru.tinkoff.kora.database.common.annotation.processor.cassandra.repository;

import ru.tinkoff.kora.database.cassandra.UDT;

public class UdtRecords {

    @UDT
    public record UdtEntity(String string, InnerUdt innerUdt) {}

    @UDT
    public record InnerUdt(Integer id, DeepUdt deep) {}

    @UDT
    public record DeepUdt(double doubleValue) {}
}

package ru.tinkoff.kora.database.symbol.processor.r2dbc

import com.squareup.kotlinpoet.ClassName


object R2dbcTypes {
    val connection = ClassName("io.r2dbc.spi", "Connection")
    val result = ClassName("io.r2dbc.spi", "Result")
    val row = ClassName("io.r2dbc.spi", "Row")

    val connectionFactory = ClassName("ru.tinkoff.kora.database.r2dbc", "R2dbcConnectionFactory")
    val repository = ClassName("ru.tinkoff.kora.database.r2dbc", "R2dbcRepository")

    val resultColumnMapper = ClassName("ru.tinkoff.kora.database.r2dbc.mapper.result", "R2dbcResultColumnMapper")
    val resultFluxMapper = ClassName("ru.tinkoff.kora.database.r2dbc.mapper.result", "R2dbcResultFluxMapper")
    val rowMapper = ClassName("ru.tinkoff.kora.database.r2dbc.mapper.result", "R2dbcRowMapper")

    val parameterColumnMapper = ClassName("ru.tinkoff.kora.database.r2dbc.mapper.parameter", "R2dbcParameterColumnMapper")
}

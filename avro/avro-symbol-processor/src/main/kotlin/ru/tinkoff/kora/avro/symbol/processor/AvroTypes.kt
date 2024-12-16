package ru.tinkoff.kora.avro.symbol.processor

import com.squareup.kotlinpoet.ClassName

object AvroTypes {

    val avroBinary = ClassName("ru.tinkoff.kora.avro.common.annotation", "AvroBinary")
    val avroJson = ClassName("ru.tinkoff.kora.avro.common.annotation", "AvroJson")

    val reader = ClassName("ru.tinkoff.kora.avro.common", "AvroReader")
    val writer = ClassName("ru.tinkoff.kora.avro.common", "AvroWriter")

    val schema = ClassName("org.apache.avro", "Schema")
    val specificData = ClassName("org.apache.avro.specific", "SpecificData")
    val datumReader = ClassName("org.apache.avro.specific", "SpecificDatumReader")
    val datumWriter = ClassName("org.apache.avro.specific", "SpecificDatumWriter")
    val decoderFactory = ClassName("org.apache.avro.io", "DecoderFactory")
    val encoderFactory = ClassName("org.apache.avro.io", "EncoderFactory")
}

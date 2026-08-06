package io.koraframework.avro.symbol.processor

import com.squareup.kotlinpoet.ClassName

object AvroTypes {

    val avro = ClassName("io.koraframework.avro.common.annotation", "Avro")

    val reader = ClassName("io.koraframework.avro.common", "AvroReader")
    val writer = ClassName("io.koraframework.avro.common", "AvroWriter")

    val schema = ClassName("org.apache.avro", "Schema")
    val specificData = ClassName("org.apache.avro.specific", "SpecificData")
    val specificRecord = ClassName("org.apache.avro.specific", "SpecificRecord")
    val datumReader = ClassName("org.apache.avro.specific", "SpecificDatumReader")
    val datumWriter = ClassName("org.apache.avro.specific", "SpecificDatumWriter")
    val decoderFactory = ClassName("org.apache.avro.io", "DecoderFactory")
    val encoderFactory = ClassName("org.apache.avro.io", "EncoderFactory")
}

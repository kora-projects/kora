package ru.tinkoff.kora.avro.annotation.processor;

import com.squareup.javapoet.ClassName;

public final class AvroTypes {

    private AvroTypes() {}

    public static final ClassName avroBinary = ClassName.get("ru.tinkoff.kora.avro.common.annotation", "AvroBinary");
    public static final ClassName avroJson = ClassName.get("ru.tinkoff.kora.avro.common.annotation", "AvroJson");

    public static final ClassName reader = ClassName.get("ru.tinkoff.kora.avro.common", "AvroReader");
    public static final ClassName writer = ClassName.get("ru.tinkoff.kora.avro.common", "AvroWriter");

    public static final ClassName schema = ClassName.get("org.apache.avro", "Schema");
    public static final ClassName specificData = ClassName.get("org.apache.avro.specific", "SpecificData");
    public static final ClassName datumReader = ClassName.get("org.apache.avro.specific", "SpecificDatumReader");
    public static final ClassName datumWriter = ClassName.get("org.apache.avro.specific", "SpecificDatumWriter");
    public static final ClassName decoderFactory = ClassName.get("org.apache.avro.io", "DecoderFactory");
    public static final ClassName encoderFactory = ClassName.get("org.apache.avro.io", "EncoderFactory");
}

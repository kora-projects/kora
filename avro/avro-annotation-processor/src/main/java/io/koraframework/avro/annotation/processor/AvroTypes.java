package io.koraframework.avro.annotation.processor;

import com.palantir.javapoet.ClassName;

public final class AvroTypes {

    private AvroTypes() {}

    public static final ClassName avro = ClassName.get("io.koraframework.avro.common.annotation", "Avro");

    public static final ClassName reader = ClassName.get("io.koraframework.avro.common", "AvroReader");
    public static final ClassName writer = ClassName.get("io.koraframework.avro.common", "AvroWriter");

    public static final ClassName schema = ClassName.get("org.apache.avro", "Schema");
    public static final ClassName specificData = ClassName.get("org.apache.avro.specific", "SpecificData");
    public static final ClassName specificRecord = ClassName.get("org.apache.avro.specific", "SpecificRecord");
    public static final ClassName datumReader = ClassName.get("org.apache.avro.specific", "SpecificDatumReader");
    public static final ClassName datumWriter = ClassName.get("org.apache.avro.specific", "SpecificDatumWriter");
    public static final ClassName decoderFactory = ClassName.get("org.apache.avro.io", "DecoderFactory");
    public static final ClassName encoderFactory = ClassName.get("org.apache.avro.io", "EncoderFactory");
}

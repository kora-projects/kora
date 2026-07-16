package io.koraframework.avro.common;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.EncoderFactory;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class GenericAvroWriter implements AvroWriter<GenericRecord> {

    private static final byte[] EMPTY = new byte[]{};

    private final GenericDatumWriter<GenericRecord> writer;

    public GenericAvroWriter(Schema schema) {
        this.writer = new GenericDatumWriter<>(schema);
    }

    @Override
    public byte[] writeBytes(@Nullable GenericRecord value) throws IOException {
        if (value == null) {
            return EMPTY;
        }
        try (var os = new ByteArrayOutputStream()) {
            var encoder = EncoderFactory.get().directBinaryEncoder(os, null);
            this.writer.write(value, encoder);
            encoder.flush();
            return os.toByteArray();
        }
    }
}

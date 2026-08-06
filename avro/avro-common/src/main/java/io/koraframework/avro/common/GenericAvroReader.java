package io.koraframework.avro.common;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

public final class GenericAvroReader implements AvroReader<GenericRecord> {

    private final GenericDatumReader<GenericRecord> reader;

    public GenericAvroReader(Schema schema) {
        this.reader = new GenericDatumReader<>(schema);
    }

    @Nullable
    @Override
    public GenericRecord read(InputStream is) throws IOException {
        if (is == null) {
            return null;
        }
        var decoder = DecoderFactory.get().directBinaryDecoder(is, null);
        return this.reader.read(null, decoder);
    }
}

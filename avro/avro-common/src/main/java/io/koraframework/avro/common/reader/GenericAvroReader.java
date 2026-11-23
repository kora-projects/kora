package io.koraframework.avro.common.reader;

import io.koraframework.avro.common.AvroGenericData;
import io.koraframework.avro.common.AvroReader;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class GenericAvroReader implements AvroReader<GenericRecord> {

    private final Schema schema;
    private final GenericData genericData;
    private final GenericDatumReader<GenericRecord> reader;
    private final ConcurrentMap<Schema, GenericDatumReader<GenericRecord>> readersByWriterSchema = new ConcurrentHashMap<>();

    public GenericAvroReader(Schema schema) {
        this(schema, AvroGenericData.withStandardConversions());
    }

    public GenericAvroReader(Schema schema, GenericData genericData) {
        this.schema = schema;
        this.genericData = genericData;
        this.reader = new GenericDatumReader<>(schema, schema, genericData);
    }

    @Override
    public Schema getSchema() {
        return this.schema;
    }

    @Nullable
    @Override
    public GenericRecord read(InputStream is) throws UncheckedIOException {
        return read(this.schema, is);
    }

    @Nullable
    @Override
    public GenericRecord read(@Nullable Schema writerSchema, InputStream is) throws UncheckedIOException {
        if (is == null) {
            return null;
        }
        var datumReader = writerSchema == null || this.schema.equals(writerSchema)
            ? this.reader
            : this.readersByWriterSchema.computeIfAbsent(writerSchema, ws -> new GenericDatumReader<>(ws, this.schema, this.genericData));
        try {
            var decoder = DecoderFactory.get().directBinaryDecoder(is, null);
            return datumReader.read(null, decoder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

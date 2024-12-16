package ru.tinkoff.kora.avro.module.http.client;

import org.apache.avro.specific.SpecificRecord;
import ru.tinkoff.kora.avro.common.AvroWriter;
import ru.tinkoff.kora.avro.module.http.AvroHttpBodyOutput;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

public class AvroHttpClientRequestMapper<T extends SpecificRecord> implements HttpClientRequestMapper<T> {

    private final AvroWriter<T> writer;

    public AvroHttpClientRequestMapper(AvroWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public HttpBodyOutput apply(Context ctx, T value) {
        return new AvroHttpBodyOutput<>(this.writer, ctx, value);
    }
}

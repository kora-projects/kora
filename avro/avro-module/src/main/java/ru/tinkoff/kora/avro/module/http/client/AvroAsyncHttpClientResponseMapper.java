package ru.tinkoff.kora.avro.module.http.client;

import org.apache.avro.specific.SpecificRecord;
import ru.tinkoff.kora.avro.common.AvroReader;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class AvroAsyncHttpClientResponseMapper<T extends SpecificRecord> implements HttpClientResponseMapper<CompletionStage<T>> {

    private final AvroReader<T> reader;

    public AvroAsyncHttpClientResponseMapper(AvroReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public CompletionStage<T> apply(HttpClientResponse response) {
        return FlowUtils.toByteArrayFuture(response.body()).thenApply(bytes -> {
            try {
                return this.reader.read(bytes);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }
}

package ru.tinkoff.kora.avro.module.http.client;

import jakarta.annotation.Nonnull;
import org.apache.avro.specific.SpecificRecord;
import ru.tinkoff.kora.avro.common.AvroReader;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.common.HttpResponseEntity;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class AvroAsyncHttpClientResponseEntityMapper<T extends SpecificRecord> implements HttpClientResponseMapper<CompletionStage<HttpResponseEntity<T>>> {

    private final AvroReader<T> reader;

    public AvroAsyncHttpClientResponseEntityMapper(AvroReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public CompletionStage<HttpResponseEntity<T>> apply(@Nonnull HttpClientResponse response) throws HttpClientDecoderException {
        return FlowUtils.toByteArrayFuture(response.body()).thenApply(bytes -> {
            try {
                var value = this.reader.read(bytes);
                return HttpResponseEntity.of(response.code(), response.headers().toMutable(), value);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }
}

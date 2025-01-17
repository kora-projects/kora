package ru.tinkoff.kora.avro.module.http.client;

import jakarta.annotation.Nonnull;
import org.apache.avro.specific.SpecificRecord;
import ru.tinkoff.kora.avro.common.AvroReader;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.HttpClientUnknownException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.common.HttpResponseEntity;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public final class AvroHttpClientResponseEntityMapper<T extends SpecificRecord> implements HttpClientResponseMapper<HttpResponseEntity<T>> {

    private final AvroReader<T> reader;

    public AvroHttpClientResponseEntityMapper(AvroReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public HttpResponseEntity<T> apply(@Nonnull HttpClientResponse response) throws IOException, HttpClientDecoderException {
        try (var body = response.body();
             var is = body.asInputStream()) {
            if (is != null) {
                var value = reader.read(is);
                return HttpResponseEntity.of(response.code(), response.headers().toMutable(), value);
            }

            try {
                var bytes = body.asArrayStage().toCompletableFuture().get();
                var value = this.reader.read(bytes);
                return HttpResponseEntity.of(response.code(), response.headers().toMutable(), value);
            } catch (InterruptedException e) {
                throw new HttpClientUnknownException(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof HttpClientException he) {
                    throw he;
                }
                if (e.getCause() != null) {
                    throw new HttpClientUnknownException(e.getCause());
                }
                throw new HttpClientUnknownException(e);
            }
        }
    }
}

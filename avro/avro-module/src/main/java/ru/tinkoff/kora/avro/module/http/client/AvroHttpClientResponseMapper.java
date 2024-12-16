package ru.tinkoff.kora.avro.module.http.client;

import org.apache.avro.specific.SpecificRecord;
import ru.tinkoff.kora.avro.common.AvroReader;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.HttpClientUnknownException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class AvroHttpClientResponseMapper<T extends SpecificRecord> implements HttpClientResponseMapper<T> {

    private final AvroReader<T> reader;

    public AvroHttpClientResponseMapper(AvroReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public T apply(HttpClientResponse response) throws IOException {
        try (var body = response.body();
             var is = body.asInputStream()) {
            if (is != null) {
                return this.reader.read(is);
            }

            try {
                var bytes = body.asArrayStage().toCompletableFuture().get();
                return this.reader.read(bytes);
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

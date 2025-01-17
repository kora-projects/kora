package ru.tinkoff.kora.avro.module.http;

import jakarta.annotation.Nullable;
import org.apache.avro.specific.SpecificRecord;
import ru.tinkoff.kora.avro.common.AvroWriter;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.flow.LazySingleSubscription;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

public final class AvroHttpBodyOutput<T extends SpecificRecord> implements HttpBodyOutput {

    private final AvroWriter<T> writer;
    private final Context context;
    @Nullable
    private final T value;

    public AvroHttpBodyOutput(AvroWriter<T> writer, Context context, @Nullable T value) {
        this.writer = writer;
        this.value = value;
        this.context = context;
    }

    @Override
    public long contentLength() {
        return -1;
    }

    @Override
    public String contentType() {
        return "application/avro";
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new LazySingleSubscription<>(subscriber, context, () -> {
            var resultBytes = this.writer.writeBytes(value);
            return ByteBuffer.wrap(resultBytes);
        }));
    }

    @Override
    public void write(OutputStream os) throws IOException {
        this.writer.writeBytes(this.value);
    }

    @Override
    public void close() {

    }
}

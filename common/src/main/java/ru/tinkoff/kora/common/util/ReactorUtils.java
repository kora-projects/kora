package ru.tinkoff.kora.common.util;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import ru.tinkoff.kora.common.Context;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ReactorUtils {
    private static final AtomicReference<Scheduler> CACHED_ELASTIC = new AtomicReference<>();

    private static Scheduler ioScheduler() {
        var s = CACHED_ELASTIC.get();
        if (s != null) {
            return s;
        }
        var maxThreads = Math.min(Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8, 64);
        s = Schedulers.newBoundedElastic(maxThreads, Integer.MAX_VALUE, "kora-io-", 60, true);
        if (CACHED_ELASTIC.compareAndSet(null, s)) {
            return s;
        }
        s.dispose();
        return CACHED_ELASTIC.get();
    }

    public static <T> Mono<T> ioMono(Supplier<T> function) {
        return Mono.create(sink -> ioScheduler().schedule(() -> {
            var oldCtx = Context.current();
            try {
                Context.Reactor.current(sink.contextView()).inject();
                var result = function.get();
                sink.success(result);
            } catch (Throwable e) {
                sink.error(e);
            } finally {
                oldCtx.inject();
            }
        }));
    }

    public static Mono<Void> ioMono(Runnable function) {
        return ReactorUtils.ioMono(() -> {
            function.run();
            return null;
        });
    }

    public static Mono<ByteBuffer> toByteBufferMono(Flux<ByteBuffer> flux) {
        return flux.reduce(ByteBuffer.allocate(0), (bytes, byteBuffer) -> ByteBuffer.allocate(bytes.remaining() + byteBuffer.remaining())
            .put(bytes)
            .put(byteBuffer)
            .rewind()
        );
    }

    public static Mono<ByteBuffer> toByteBufferMono(Publisher<ByteBuffer> flux) {
        return Flux.from(flux).reduce(ByteBuffer.allocate(0), (bytes, byteBuffer) -> ByteBuffer.allocate(bytes.remaining() + byteBuffer.remaining())
            .put(bytes)
            .put(byteBuffer)
            .rewind()
        );
    }

    public static Mono<byte[]> toByteArrayMono(Flux<ByteBuffer> flux) {
        return flux.reduce(new byte[0], (bytes, byteBuffer) -> {
            var newArr = Arrays.copyOf(bytes, bytes.length + byteBuffer.remaining());
            byteBuffer.get(newArr, bytes.length, byteBuffer.remaining());
            return newArr;
        });
    }

    public static Mono<byte[]> toByteArrayMono(Flux<ByteBuffer> flux, int limit) {
        return flux.reduce(new byte[0], (bytes, byteBuffer) -> {
            if (bytes.length >= limit) {
                return bytes;
            }
            var newLength = Math.min(bytes.length + byteBuffer.remaining(), limit);
            var newArr = Arrays.copyOf(bytes, newLength);
            byteBuffer.get(newArr, bytes.length, byteBuffer.remaining());
            return newArr;
        });
    }

    public static Mono<byte[]> toByteArrayMono(Publisher<ByteBuffer> flux) {
        return Flux.from(flux).reduce(new byte[0], (bytes, byteBuffer) -> {
            var newArr = Arrays.copyOf(bytes, bytes.length + byteBuffer.remaining());
            byteBuffer.get(newArr, bytes.length, byteBuffer.remaining());
            return newArr;
        });
    }
}

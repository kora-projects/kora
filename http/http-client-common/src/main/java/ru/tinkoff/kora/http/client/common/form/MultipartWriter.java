package ru.tinkoff.kora.http.client.common.form;

import ru.tinkoff.kora.common.util.flow.FromCallablePublisher;
import ru.tinkoff.kora.common.util.flow.OnePublisher;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.form.FormMultipart;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class MultipartWriter {
    private static final ByteBuffer RN_BUF = StandardCharsets.US_ASCII.encode("\r\n");

    public static HttpBodyOutput write(List<? extends FormMultipart.FormPart> parts) {
        return write("blob:" + UUID.randomUUID(), parts);
    }

    public static HttpBodyOutput write(String boundary, List<? extends FormMultipart.FormPart> parts) {
        return HttpBodyOutput.of("multipart/form-data;boundary=\"" + boundary + "\"", new MultipartBodyFlow(boundary, parts));
    }

    private static final class MultipartBodyFlow implements Flow.Publisher<ByteBuffer> {
        private final String boundary;
        private final List<? extends FormMultipart.FormPart> parts;

        private MultipartBodyFlow(String boundary, List<? extends FormMultipart.FormPart> parts) {
            this.boundary = boundary;
            this.parts = parts;

        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            var s = new MultipartBodyFlowSubscription(subscriber, boundary, parts);
            subscriber.onSubscribe(s);
        }
    }

    private static final class MultipartBodyFlowSubscription implements Flow.Subscription, Flow.Subscriber<ByteBuffer> {
        private static final AtomicLongFieldUpdater<MultipartBodyFlowSubscription> DEMAND = AtomicLongFieldUpdater.newUpdater(MultipartBodyFlowSubscription.class, "demand");
        private volatile long demand = 0;

        private volatile int index = 0;

        private static final AtomicIntegerFieldUpdater<MultipartBodyFlowSubscription> WIP = AtomicIntegerFieldUpdater.newUpdater(MultipartBodyFlowSubscription.class, "wip");
        private volatile int wip;

        private volatile int content = 0;
        private volatile Flow.Subscription contentSubscription;


        private final ByteBuffer boundaryRN;
        private final ByteBuffer boundaryDD;
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final List<? extends FormMultipart.FormPart> parts;

        private MultipartBodyFlowSubscription(Flow.Subscriber<? super ByteBuffer> subscriber, String boundary, List<? extends FormMultipart.FormPart> parts) {
            this.subscriber = subscriber;
            this.parts = parts;
            var boundaryBuff = StandardCharsets.US_ASCII.encode("--" + boundary);
            this.boundaryRN = ByteBuffer.allocate(boundaryBuff.remaining() + 2)
                .put(boundaryBuff.slice())
                .put(RN_BUF.slice())
                .rewind();
            this.boundaryDD = ByteBuffer.allocate(boundaryBuff.remaining() + 2)
                .put(boundaryBuff.slice())
                .put((byte) '-')
                .put((byte) '-')
                .rewind();
        }

        @Override
        public void request(long n) {
            assert n > 0;
            var oldDemand = DEMAND.accumulateAndGet(this, n, (p, i) -> p + i < 0 ? Long.MAX_VALUE : p + i);
            if (oldDemand == 0) {
                var contentSubscription = this.contentSubscription;
                if (contentSubscription != null) {
                    contentSubscription.request(n);
                }
            }
            if (WIP.compareAndSet(this, 0, 1)) {
                this.drainLoop();
            }
        }

        private void drainLoop() {
            var i = this.index;
            var s = this.subscriber;
            while (i <= this.parts.size() && this.demand > 0 && this.contentSubscription == null) {
                if (this.content == 1) {
                    this.content = 0;
                    demand = DEMAND.decrementAndGet(this);
                    s.onNext(RN_BUF.slice());
                    continue;
                }
                if (i == this.parts.size()) {
                    this.index = i + 1;
                    s.onNext(boundaryDD.slice());
                    s.onComplete();
                    WIP.set(this, 0);
                    return;
                }
                var part = this.parts.get(i);
                if (part instanceof FormMultipart.FormPart.MultipartData data) {
                    var contentDisposition = "content-disposition: form-data; name=\"" + part.name() + "\"\r\n";
                    var contentType = "text/plain; charset=utf-8";

                    var contentDispositionBuff = StandardCharsets.US_ASCII.encode(contentDisposition);
                    var contentTypeBuff = StandardCharsets.US_ASCII.encode("content-type: " + contentType + "\r\n");
                    var contentBuf = StandardCharsets.UTF_8.encode(data.content());

                    var buf = ByteBuffer.allocate(boundaryRN.remaining() + contentDispositionBuff.remaining() + contentTypeBuff.remaining() + RN_BUF.remaining() + contentBuf.remaining() + RN_BUF.remaining())
                        .put(boundaryRN.slice())
                        .put(contentDispositionBuff)
                        .put(contentTypeBuff)
                        .put(RN_BUF.slice())
                        .put(contentBuf)
                        .put(RN_BUF.slice())
                        .rewind();
                    s.onNext(buf);
                } else if (part instanceof FormMultipart.FormPart.MultipartFile file) {
                    var contentDisposition = file.fileName() != null
                        ? "content-disposition: form-data; name=\"" + part.name() + "\"; filename=\"" + file.fileName() + "\"\r\n"
                        : "content-disposition: form-data; name=\"" + part.name() + "\"\r\n";
                    var contentType = file.contentType() != null
                        ? file.contentType()
                        : "application/octet-stream";

                    var contentDispositionBuff = StandardCharsets.US_ASCII.encode(contentDisposition);
                    var contentTypeBuff = StandardCharsets.US_ASCII.encode("content-type: " + contentType + "\r\n");
                    var contentBuf = ByteBuffer.wrap(file.content());

                    var buf = ByteBuffer.allocate(boundaryRN.remaining() + contentDispositionBuff.remaining() + contentTypeBuff.remaining() + RN_BUF.remaining() + contentBuf.remaining() + RN_BUF.remaining())
                        .put(boundaryRN.slice())
                        .put(contentDispositionBuff)
                        .put(contentTypeBuff)
                        .put(RN_BUF.slice())
                        .put(contentBuf)
                        .put(RN_BUF.slice())
                        .rewind();

                    s.onNext(buf);
                } else if (part instanceof FormMultipart.FormPart.MultipartFileStream stream) {
                    var contentDisposition = stream.fileName() != null
                        ? "content-disposition: form-data; name=\"" + part.name() + "\"; filename=\"" + stream.fileName() + "\"\r\n"
                        : "content-disposition: form-data; name=\"" + part.name() + "\"\r\n";
                    var contentType = stream.contentType() != null
                        ? stream.contentType()
                        : "application/octet-stream";

                    var contentDispositionBuff = StandardCharsets.US_ASCII.encode(contentDisposition);
                    var contentTypeBuff = StandardCharsets.US_ASCII.encode("content-type: " + contentType + "\r\n");
                    var content = stream.content();
                    if (content instanceof OnePublisher<ByteBuffer> one) {
                        var contentBuf = one.value();
                        var buf = ByteBuffer.allocate(boundaryRN.remaining() + contentDispositionBuff.remaining() + contentTypeBuff.remaining() + RN_BUF.remaining() + contentBuf.remaining() + RN_BUF.remaining())
                            .put(boundaryRN.slice())
                            .put(contentDispositionBuff)
                            .put(contentTypeBuff)
                            .put(RN_BUF.slice())
                            .put(contentBuf)
                            .put(RN_BUF.slice())
                            .rewind();
                        s.onNext(buf);
                    } else if (content instanceof FromCallablePublisher<ByteBuffer> callable) {
                        final ByteBuffer contentBuf;
                        try {
                            contentBuf = callable.callable().call();
                        } catch (Throwable e) {
                            this.index = this.parts.size() + 2;
                            s.onError(e);
                            WIP.set(this, 0);
                            return;
                        }
                        var buf = ByteBuffer.allocate(boundaryRN.remaining() + contentDispositionBuff.remaining() + contentTypeBuff.remaining() + RN_BUF.remaining() + contentBuf.remaining() + RN_BUF.remaining())
                            .put(boundaryRN.slice())
                            .put(contentDispositionBuff)
                            .put(contentTypeBuff)
                            .put(RN_BUF.slice())
                            .put(contentBuf)
                            .put(RN_BUF.slice())
                            .rewind();
                        this.content = 1;
                        s.onNext(buf);
                    } else {
                        this.index = i + 1;
                        this.content = 1;
                        DEMAND.decrementAndGet(this);
                        WIP.set(this, 0);
                        var buf = ByteBuffer.allocate(boundaryRN.remaining() + contentDispositionBuff.remaining() + contentTypeBuff.remaining() + RN_BUF.remaining())
                            .put(boundaryRN.slice())
                            .put(contentDispositionBuff)
                            .put(contentTypeBuff)
                            .put(RN_BUF.slice())
                            .rewind();
                        s.onNext(buf);
                        content.subscribe(this);
                        return;
                    }

                } else {
                    // never gonna happen
                    throw new IllegalStateException("Invalid sealed interface impl: " + part.getClass());
                }
                demand = DEMAND.decrementAndGet(this);
                i++;
            }
            this.index = i;
            WIP.set(this, 0);
        }

        @Override
        public void cancel() {
            this.index = this.parts.size() + 2;
            var contentSubscription = this.contentSubscription;
            if (contentSubscription != null) {
                contentSubscription.cancel();
            }
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.contentSubscription = subscription;
            var demand = DEMAND.get(this);
            if (demand > 0) {
                subscription.request(demand);
            }
        }

        @Override
        public void onNext(ByteBuffer item) {
            DEMAND.decrementAndGet(this);
            this.subscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            this.index = this.parts.size() + 2;
            this.subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            contentSubscription = null;
            if (WIP.compareAndSet(this, 0, 1)) {
                this.drainLoop();
            }
        }
    }
}

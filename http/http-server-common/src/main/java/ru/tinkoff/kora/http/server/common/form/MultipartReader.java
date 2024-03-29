package ru.tinkoff.kora.http.server.common.form;

import ru.tinkoff.kora.http.common.form.FormMultipart.FormPart.MultipartFile;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;

import jakarta.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.regex.Pattern;

public class MultipartReader {
    private static final Pattern boundaryPattern = Pattern.compile(".*(\\s|;)boundary=\"?(?<boundary>[^;\"]+).*");

    public static CompletionStage<List<MultipartFile>> read(HttpServerRequest r) {
        var contentType = r.headers().getFirst("content-type");
        if (contentType == null) {
            throw HttpServerResponseException.of(400, "content-type header is required");
        }
        var m = boundaryPattern.matcher(contentType);
        if (!m.matches()) {
            throw HttpServerResponseException.of(400, "content-type header is invalid");
        }
        var boundary = m.group("boundary");
        var future = new CompletableFuture<List<MultipartFile>>();
        var decoder = new MultipartDecoder(boundary, future);
        r.body().subscribe(decoder);
        return future;
    }

    private static class MultipartDecoder implements Flow.Subscriber<ByteBuffer> {
        private static final Pattern namePattern = Pattern.compile(".*form-data;.*(\\s|;)name=\"(?<name>.*?)\".*", Pattern.CASE_INSENSITIVE);
        private static final Pattern fileNamePattern = Pattern.compile(".*form-data;.*(\\s|;)filename=\"(?<filename>.*?)\".*", Pattern.CASE_INSENSITIVE);
        private static final int SIZE_STEP = 4 * 1024 * 1024; // 4 mb
        private final byte[] boundary;
        private final byte[] boundaryBuf;
        private final CompletableFuture<List<MultipartFile>> future;
        private ByteBuffer buf = null;
        private State state = State.BEGIN;
        private int readPosition = 0;
        private ArrayList<byte[]> currentHeaders;
        private ContentDisposition currentContentDisposition;
        private int lastBodyPosition = 0;

        private final List<MultipartFile> parts = Collections.synchronizedList(new ArrayList<>());

        public MultipartDecoder(String boundary, CompletableFuture<List<MultipartFile>> future) {
            this.boundary = boundary.getBytes(StandardCharsets.US_ASCII);
            this.boundaryBuf = new byte[this.boundary.length];
            this.future = future;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            if (future.isDone()) {
                return;
            }
            this.ensureWritable(byteBuffer);
            this.buf.put(byteBuffer);
            loop:
            for (; ; ) {
                var readPosition = this.readPosition;
                switch (this.state) {
                    case BEGIN -> {
                        if (this.buf.position() - readPosition < this.boundary.length + 4) {
                            break loop;
                        }
                        if (this.buf.get(this.readPosition) != '-' || this.buf.get(this.readPosition + 1) != '-') {
                            future.completeExceptionally(HttpServerResponseException.of(400, "Invalid beginning of multipart body"));
                            return;
                        }
                        readPosition += 2;
                        this.buf.get(readPosition, this.boundaryBuf);
                        if (!Arrays.equals(this.boundary, this.boundaryBuf)) {
                            future.completeExceptionally(HttpServerResponseException.of(400, "Invalid beginning of multipart body"));
                        }
                        readPosition += this.boundary.length;
                        if (this.buf.get(readPosition) != '\r' || this.buf.get(readPosition + 1) != '\n') {
                            future.completeExceptionally(HttpServerResponseException.of(400, "Invalid beginning of multipart body"));
                        }
                        readPosition += 2;
                        this.readPosition = readPosition;
                        this.state = State.READ_HEADERS;
                        this.currentHeaders = new ArrayList<>();
                    }
                    case READ_HEADERS -> {
                        var nextLineBreak = this.findNextLineBreak();
                        if (nextLineBreak < 0) {
                            break loop;
                        }
                        if (nextLineBreak != this.readPosition) {
                            var bytes = new byte[nextLineBreak - this.readPosition];
                            this.buf.get(this.readPosition, bytes);
                            this.currentHeaders.add(bytes);
                            this.readPosition = nextLineBreak + 2;
                        } else {
                            var contentDisposition = this.parseContentDisposition();
                            if (contentDisposition == null) {
                                future.completeExceptionally(HttpServerResponseException.of(400, "Multipart part is missing content-disposition header"));
                                return;
                            }
                            this.currentContentDisposition = contentDisposition;
                            this.state = State.READ_BODY;
                            this.readPosition = this.readPosition + 2;
                            this.lastBodyPosition = readPosition;
                        }
                    }
                    case READ_BODY -> {
                        for (int i = this.lastBodyPosition; i <= this.buf.position() - (6 + this.boundary.length); i++) {
                            if (this.buf.get(i) != '\r' || this.buf.get(i + 1) != '\n' || this.buf.get(i + 2) != '-' || this.buf.get(i + 3) != '-') {
                                this.lastBodyPosition = i + 1;
                                continue;
                            }
                            if ((this.buf.get(i + 4 + this.boundary.length) != '-' || this.buf.get(i + 4 + this.boundary.length + 1) != '-')
                                && (this.buf.get(i + 4 + this.boundary.length) != '\r' || this.buf.get(i + 4 + this.boundary.length + 1) != '\n')) {
                                this.lastBodyPosition = i + 1;
                                continue;
                            }
                            this.buf.get(i + 4, this.boundaryBuf);
                            if (Arrays.equals(this.boundary, this.boundaryBuf)) {
                                var array = new byte[i - this.readPosition];
                                this.buf.get(this.readPosition, array);
                                parts.add(new MultipartFile(
                                    currentContentDisposition.name(),
                                    currentContentDisposition.filename(),
                                    this.parseContentType(),
                                    array
                                ));
                                if (this.buf.get(i + 4 + this.boundary.length) == '-' || this.buf.get(i + 4 + this.boundary.length + 1) == '-') {
                                    this.future.complete(this.parts);
                                    break loop;
                                }
                                this.readPosition = i + 4 + this.boundary.length + 2;
                                this.state = State.READ_HEADERS;
                                this.currentHeaders = new ArrayList<>();
                                this.currentContentDisposition = null;
                                var writePosition = this.buf.position();
                                var newWritePosition = writePosition - this.readPosition;
                                this.buf.position(this.readPosition)
                                    .compact()
                                    .position(newWritePosition);
                                this.readPosition = 0;
                                continue loop;
                            }
                        }
                        break loop;
                    }
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!future.isDone()) {
                future.completeExceptionally(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!future.isDone()) {
                future.complete(this.parts);
            }
        }

        private record ContentDisposition(String name, @Nullable String filename) {}

        @Nullable
        private ContentDisposition parseContentDisposition() {
            for (var header : this.currentHeaders) {
                if (header.length < 19) {
                    continue;
                }
                var headerStr = new String(header, 0, 19);
                if (!headerStr.equalsIgnoreCase("content-disposition")) {
                    continue;
                }
                headerStr = new String(header, 19, header.length - 19);

                var m1 = namePattern.matcher(headerStr);
                if (!m1.matches()) {
                    continue;
                }
                var name = m1.group("name");
                var m2 = fileNamePattern.matcher(headerStr);
                var fileName = m2.matches()
                    ? m2.group("filename")
                    : null;
                return new ContentDisposition(name, fileName);
            }
            return null;
        }

        @Nullable
        private String parseContentType() {
            for (var header : this.currentHeaders) {
                if (header.length < 12) {
                    continue;
                }
                var headerStr = new String(header, 0, 12);
                if (!headerStr.equalsIgnoreCase("content-type")) {
                    continue;
                }
                for (int i = 12; i < header.length; i++) {
                    if (header[i] == ':') {
                        return new String(header, i + 1, header.length - i - 1).trim();
                    }
                }
            }
            return null;
        }

        private int findNextLineBreak() {
            for (int i = this.readPosition; i < this.buf.position() - 1; i++) {
                if (this.buf.get(i) == '\r' && this.buf.get(i + 1) == '\n') {
                    return i;
                }
            }
            return -1;
        }


        private enum State {
            BEGIN, READ_HEADERS, READ_BODY
        }

        private void ensureWritable(ByteBuffer byteBuffer) {
            var bytesToWrite = byteBuffer.remaining();
            if (this.buf == null) {
                var newCapacity = SIZE_STEP;
                while (newCapacity <= bytesToWrite) {
                    newCapacity += SIZE_STEP;
                }
                this.buf = ByteBuffer.allocate(newCapacity);
                return;
            }
            var writableBytes = this.buf.capacity() - this.buf.position();
            if (writableBytes >= bytesToWrite) {
                return;
            }
            var position = this.buf.position();
            var newCapacity = this.buf.capacity();
            while (newCapacity <= bytesToWrite + position) {
                newCapacity += SIZE_STEP;
            }
            this.buf = ByteBuffer.allocate(newCapacity)
                .put(this.buf.position(0))
                .position(position);
        }
    }
}

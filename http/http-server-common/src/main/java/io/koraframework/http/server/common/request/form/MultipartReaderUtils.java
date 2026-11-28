package io.koraframework.http.server.common.request.form;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.form.FormMultipart.FormPart.MultipartFile;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class MultipartReaderUtils {

    private MultipartReaderUtils() { }

    private static final Pattern BOUNDARY_PATTERN = Pattern.compile(".*(\\s|;)boundary=\"?(?<boundary>[^;\"]+).*");

    public static List<MultipartFile> read(HttpServerRequest r) throws IOException {
        var contentType = r.headers().getFirst("content-type");
        if (contentType == null) {
            throw HttpServerResponseException.of(400, "content-type header is required");
        }
        var m = BOUNDARY_PATTERN.matcher(contentType);
        if (!m.matches()) {
            throw HttpServerResponseException.of(400, "content-type header is invalid");
        }
        var boundary = m.group("boundary");
        var decoder = new MultipartDecoder(boundary);
        try (var body = r.body();
             var is = body.asInputStream()) {
            var buf = new byte[16 * 1024];
            while (true) {
                var read = is.read(buf, 0, buf.length);
                if (read < 0) {
                    if (decoder.state != MultipartDecoder.State.COMPLETED) {
                        throw HttpServerResponseException.of(400, "Unexpected end of stream");
                    } else {
                        return decoder.parts;
                    }
                }
                if (read == 0) {
                    continue;
                }
                decoder.onNext(buf, 0, read);
                if (decoder.state == MultipartDecoder.State.COMPLETED) {
                    return decoder.parts;
                }
            }
        }
    }

    private static class MultipartDecoder {
        private static final Pattern namePattern = Pattern.compile(".*form-data;.*(\\s|;)name=\"(?<name>.*?)\".*", Pattern.CASE_INSENSITIVE);
        private static final Pattern fileNamePattern = Pattern.compile(".*form-data;.*(\\s|;)filename=\"(?<filename>.*?)\".*", Pattern.CASE_INSENSITIVE);
        private static final int SIZE_STEP = 4 * 1024 * 1024; // 4 mb
        private final byte[] boundary;
        private final byte[] boundaryBuf;
        private ByteBuffer buf = null;
        private State state = State.BEGIN;
        private int readPosition = 0;
        private ArrayList<byte[]> currentHeaders;
        private ContentDisposition currentContentDisposition;
        private int lastBodyPosition = 0;

        private final List<MultipartFile> parts = new ArrayList<>();

        public MultipartDecoder(String boundary) {
            this.boundary = boundary.getBytes(StandardCharsets.US_ASCII);
            this.boundaryBuf = new byte[boundary.length()];
        }

        public void onNext(byte[] buf, int offset, int len) {
            ensureWritable(len);
            this.buf.put(buf, offset, len);
            loop:
            for (; ; ) {
                var currentReadPosition = readPosition;
                if (state == State.BEGIN) {
                    if (this.buf.position() - currentReadPosition < boundary.length + 4) {
                        break loop;
                    }
                    if (this.buf.get(readPosition) != '-' || this.buf.get(readPosition + 1) != '-') {
                        throw HttpServerResponseException.of(400, "Invalid beginning of multipart body");
                    }
                    currentReadPosition += 2;
                    this.buf.get(currentReadPosition, boundaryBuf);
                    if (!Arrays.equals(boundary, boundaryBuf)) {
                        throw HttpServerResponseException.of(400, "Invalid beginning of multipart body");
                    }
                    currentReadPosition += boundary.length;
                    if (this.buf.get(currentReadPosition) != '\r' || this.buf.get(currentReadPosition + 1) != '\n') {
                        throw HttpServerResponseException.of(400, "Invalid beginning of multipart body");
                    }
                    currentReadPosition += 2;
                    readPosition = currentReadPosition;
                    state = State.READ_HEADERS;
                    currentHeaders = new ArrayList<>();
                } else if (state == State.READ_HEADERS) {
                    var nextLineBreak = findNextLineBreak();
                    if (nextLineBreak < 0) {
                        break loop;
                    }
                    if (nextLineBreak != readPosition) {
                        var bytes = new byte[nextLineBreak - readPosition];
                        this.buf.get(readPosition, bytes);
                        currentHeaders.add(bytes);
                        readPosition = nextLineBreak + 2;
                    } else {
                        var contentDisposition = parseContentDisposition();
                        if (contentDisposition == null) {
                            throw HttpServerResponseException.of(400, "Multipart part is missing content-disposition header");
                        }
                        currentContentDisposition = contentDisposition;
                        state = State.READ_BODY;
                        readPosition = readPosition + 2;
                        lastBodyPosition = readPosition;
                    }
                } else if (state == State.READ_BODY) {
                    for (var i = lastBodyPosition; i <= this.buf.position() - (6 + boundary.length); i++) {
                        if (this.buf.get(i) != '\r' || this.buf.get(i + 1) != '\n' || this.buf.get(i + 2) != '-' || this.buf.get(i + 3) != '-') {
                            lastBodyPosition = i + 1;
                            continue;
                        }
                        if ((this.buf.get(i + 4 + boundary.length) != '-' || this.buf.get(i + 4 + boundary.length + 1) != '-')
                            && (this.buf.get(i + 4 + boundary.length) != '\r' || this.buf.get(i + 4 + boundary.length + 1) != '\n')) {
                            lastBodyPosition = i + 1;
                            continue;
                        }
                        this.buf.get(i + 4, boundaryBuf);
                        if (Arrays.equals(boundary, boundaryBuf)) {
                            var array = new byte[i - readPosition];
                            this.buf.get(readPosition, array);
                            parts.add(new MultipartFile(
                                currentContentDisposition.name(),
                                currentContentDisposition.filename(),
                                parseContentType(),
                                array
                            ));
                            if (this.buf.get(i + 4 + boundary.length) == '-' || this.buf.get(i + 4 + boundary.length + 1) == '-') {
                                state = State.COMPLETED;
                                break loop;
                            }
                            readPosition = i + 4 + boundary.length + 2;
                            state = State.READ_HEADERS;
                            currentHeaders = new ArrayList<>();
                            currentContentDisposition = null;
                            var writePosition = this.buf.position();
                            var newWritePosition = writePosition - readPosition;
                            this.buf.position(readPosition)
                                .compact()
                                .position(newWritePosition);
                            readPosition = 0;
                            continue loop;
                        }
                    }
                    break loop;
                }
            }
        }

        private record ContentDisposition(String name, @Nullable String filename) {}

        @Nullable
        private ContentDisposition parseContentDisposition() {
            for (var header : currentHeaders) {
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
            for (var header : currentHeaders) {
                if (header.length < 12) {
                    continue;
                }
                var headerStr = new String(header, 0, 12);
                if (!headerStr.equalsIgnoreCase("content-type")) {
                    continue;
                }
                for (var i = 12; i < header.length; i++) {
                    if (header[i] == ':') {
                        return new String(header, i + 1, header.length - i - 1).trim();
                    }
                }
            }
            return null;
        }

        private int findNextLineBreak() {
            for (var i = readPosition; i < buf.position() - 1; i++) {
                if (buf.get(i) == '\r' && buf.get(i + 1) == '\n') {
                    return i;
                }
            }
            return -1;
        }

        private enum State {
            BEGIN, READ_HEADERS, READ_BODY, COMPLETED
        }

        private void ensureWritable(int len) {
            if (buf == null) {
                var newCapacity = SIZE_STEP;
                while (newCapacity <= len) {
                    newCapacity += SIZE_STEP;
                }
                buf = ByteBuffer.allocate(newCapacity);
                return;
            }
            var writableBytes = buf.capacity() - buf.position();
            if (writableBytes >= len) {
                return;
            }
            var position = buf.position();
            var newCapacity = buf.capacity();
            while (newCapacity <= len + position) {
                newCapacity += SIZE_STEP;
            }
            buf = ByteBuffer.allocate(newCapacity)
                .put(buf.position(0))
                .position(position);
        }
    }
}

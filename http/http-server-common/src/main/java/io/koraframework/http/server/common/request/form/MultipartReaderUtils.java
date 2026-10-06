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

    private static final Pattern BOUNDARY_PATTERN = Pattern.compile(".*(\\s|;)boundary=\"?(?<boundary>[^;\"]+).*", Pattern.CASE_INSENSITIVE);

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
        private static final Pattern namePattern = Pattern.compile(".*form-data\\s*;(?:.*;)?\\s*name=\"(?<name>.*?)\".*", Pattern.CASE_INSENSITIVE);
        private static final Pattern fileNamePattern = Pattern.compile(".*form-data\\s*;(?:.*;)?\\s*filename=\"(?<filename>.*?)\".*", Pattern.CASE_INSENSITIVE);
        private static final int SIZE_STEP = 4 * 1024 * 1024; // 4 mb
        private final byte[] boundary;
        private final byte[] boundaryBuf;
        private ByteBuffer buf = null;
        private State state = State.BEGIN;
        private int readPosition = 0;
        private ArrayList<byte[]> currentHeaders;
        private ContentDisposition currentContentDisposition;
        private int lastBodyPosition = 0;
        private int paddingScanPosition = 0;

        private final List<MultipartFile> parts = new ArrayList<>();

        public MultipartDecoder(String boundary) {
            this.boundary = boundary.getBytes(StandardCharsets.US_ASCII);
            this.boundaryBuf = new byte[this.boundary.length];
        }

        public void onNext(byte[] buf, int offset, int len) {
            this.ensureWritable(len);
            this.buf.put(buf, offset, len);
            loop:
            for (; ; ) {
                var readPosition = this.readPosition;
                switch (this.state) {
                    case BEGIN -> {
                        // at the start of a line: either the first delimiter or a preamble line (RFC 2046 5.1.1)
                        var position = this.buf.position();
                        if (position - readPosition < this.boundary.length + 2) {
                            break loop;
                        }
                        if (this.buf.get(readPosition) == '-' && this.buf.get(readPosition + 1) == '-' && this.isBoundaryAt(readPosition + 2)) {
                            var lineEnd = this.skipTransportPadding(readPosition + 2 + this.boundary.length);
                            if (lineEnd + 1 >= position) {
                                break loop;
                            }
                            if (this.buf.get(lineEnd) == '\r' && this.buf.get(lineEnd + 1) == '\n') {
                                this.readPosition = lineEnd + 2;
                                this.state = State.READ_HEADERS;
                                this.currentHeaders = new ArrayList<>();
                                continue loop;
                            }
                        }
                        this.state = State.PREAMBLE;
                    }
                    case PREAMBLE -> {
                        var nextLineBreak = this.findNextLineBreak();
                        if (nextLineBreak < 0) {
                            this.readPosition = Math.max(this.readPosition, this.buf.position() - 1);
                            break loop;
                        }
                        this.readPosition = nextLineBreak + 2;
                        this.state = State.BEGIN;
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
                                throw HttpServerResponseException.of(400, "Multipart part is missing content-disposition header");
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
                            if (!this.isBoundaryAt(i + 4)) {
                                this.lastBodyPosition = i + 1;
                                continue;
                            }
                            var lineEnd = i + 4 + this.boundary.length;
                            var close = this.buf.get(lineEnd) == '-' && this.buf.get(lineEnd + 1) == '-';
                            if (!close) {
                                lineEnd = this.skipTransportPadding(lineEnd);
                                if (lineEnd + 1 >= this.buf.position()) {
                                    this.lastBodyPosition = i;
                                    break loop;
                                }
                                if (this.buf.get(lineEnd) != '\r' || this.buf.get(lineEnd + 1) != '\n') {
                                    this.lastBodyPosition = i + 1;
                                    continue;
                                }
                            }
                            var array = new byte[i - this.readPosition];
                            this.buf.get(this.readPosition, array);
                            parts.add(new MultipartFile(
                                currentContentDisposition.name(),
                                currentContentDisposition.filename(),
                                this.parseContentType(),
                                array
                            ));
                            if (close) {
                                state = State.COMPLETED;
                                break loop;
                            }
                            this.readPosition = lineEnd + 2;
                            this.state = State.READ_HEADERS;
                            this.currentHeaders = new ArrayList<>();
                            this.currentContentDisposition = null;
                            var writePosition = this.buf.position();
                            var newWritePosition = writePosition - this.readPosition;
                            this.buf.position(this.readPosition)
                                .compact()
                                .position(newWritePosition);
                            this.readPosition = 0;
                            this.paddingScanPosition = 0;
                            continue loop;
                        }
                        break loop;
                    }
                }
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

        private boolean isBoundaryAt(int index) {
            this.buf.get(index, this.boundaryBuf);
            return Arrays.equals(this.boundary, this.boundaryBuf);
        }

        /**
         * Skips transport padding (SP / HTAB) after a boundary, RFC 2046 5.1.1.
         * Resumes from where the previous chunk stopped, so a long padding is scanned once.
         */
        private int skipTransportPadding(int index) {
            index = Math.max(index, this.paddingScanPosition);
            while (index < this.buf.position() && (this.buf.get(index) == ' ' || this.buf.get(index) == '\t')) {
                index++;
            }
            this.paddingScanPosition = index;
            return index;
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
            BEGIN, PREAMBLE, READ_HEADERS, READ_BODY, COMPLETED
        }

        private void ensureWritable(int len) {
            if (this.buf == null) {
                var newCapacity = SIZE_STEP;
                while (newCapacity <= len) {
                    newCapacity += SIZE_STEP;
                }
                this.buf = ByteBuffer.allocate(newCapacity);
                return;
            }
            var writableBytes = this.buf.capacity() - this.buf.position();
            if (writableBytes >= len) {
                return;
            }
            var position = this.buf.position();
            var newCapacity = this.buf.capacity();
            while (newCapacity <= len + position) {
                newCapacity += SIZE_STEP;
            }
            this.buf = ByteBuffer.allocate(newCapacity)
                .put(this.buf.position(0))
                .position(position);
        }
    }
}

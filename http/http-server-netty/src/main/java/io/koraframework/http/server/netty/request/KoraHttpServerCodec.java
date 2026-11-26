package io.koraframework.http.server.netty.request;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.*;
import io.netty.util.concurrent.*;
import io.netty.util.internal.*;
import org.jspecify.annotations.*;

import java.lang.invoke.*;
import java.nio.*;
import java.util.*;

import static io.koraframework.http.server.netty.handler.KoraRequestHandler.*;

public final class KoraHttpServerCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder> implements HttpServerUpgradeHandler.SourceCodec {

    static final int DEFAULT_MAX_PIPELINE_DEPTH = 128;

    private static final byte METHOD_FLAG_HEAD = 1;
    private static final byte METHOD_FLAG_CONNECT = 2;
    private static final byte METHOD_FLAG_OTHER = 3;
    private static final byte METHOD_MASK = 0x3;
    private static final byte FLAG_CLOSE_AFTER = 0x4;

    private static final int ENTRY_BITS = 3;
    private static final int ENTRY_MASK = 0x7;
    private static final int INLINE_QUEUE_CAPACITY = Long.SIZE / ENTRY_BITS; // 21

    private static final long SWAR_BROADCAST_COLON = 0x3A3A3A3A3A3A3A3AL; // ':'
    private static final long SWAR_BROADCAST_LF = 0x0A0A0A0A0A0A0A0AL;    // '\n'
    private static final long SWAR_LSB = 0x0101010101010101L;
    private static final long SWAR_MSB = 0x8080808080808080L;

    private static final AsciiString USER_AGENT = AsciiString.cached("User-Agent");

    private long methodQueue;
    private int methodQueueSize;
    @Nullable
    private Queue<Byte> methodOverflowQueue;
    private final int maxPipelineDepth;

    public KoraHttpServerCodec(HttpDecoderConfig config) {
        this(config, DEFAULT_MAX_PIPELINE_DEPTH);
    }

    public KoraHttpServerCodec(HttpDecoderConfig config, int maxPipelineDepth) {
        this.maxPipelineDepth = ObjectUtil.checkPositive(maxPipelineDepth, "maxPipelineDepth");
        init(new KoraServerRequestDecoder(config), new KoraServerResponseEncoder());
    }

    @Override
    public void upgradeFrom(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(this);
    }

    private boolean enqueueMethod(HttpMethod method, boolean closeAfter) {
        Queue<Byte> overflowQueue = methodOverflowQueue;
        int currentDepth = methodQueueSize + (overflowQueue != null ? overflowQueue.size() : 0);
        if (currentDepth >= maxPipelineDepth) {
            return false;
        }

        byte entry;
        if (HttpMethod.HEAD.equals(method)) {
            entry = METHOD_FLAG_HEAD;
        } else if (HttpMethod.CONNECT.equals(method)) {
            entry = METHOD_FLAG_CONNECT;
        } else {
            entry = METHOD_FLAG_OTHER;
        }
        if (closeAfter) {
            entry |= FLAG_CLOSE_AFTER;
        }

        if (overflowQueue != null) {
            overflowQueue.add(entry);
            return true;
        }

        if (methodQueueSize < INLINE_QUEUE_CAPACITY) {
            methodQueue |= (long) entry << (methodQueueSize * ENTRY_BITS);
            methodQueueSize++;
        } else {
            overflowQueue = new ArrayDeque<>(4);
            overflowQueue.add(entry);
            methodOverflowQueue = overflowQueue;
        }
        return true;
    }

    private void markLastQueuedCloseAfter() {
        Queue<Byte> overflowQueue = methodOverflowQueue;
        if (overflowQueue instanceof ArrayDeque<Byte> deque && !deque.isEmpty()) {
            Byte last = deque.removeLast();
            deque.addLast((byte) (last | FLAG_CLOSE_AFTER));
            return;
        }
        if (methodQueueSize > 0) {
            int shift = (methodQueueSize - 1) * ENTRY_BITS;
            methodQueue |= ((long) FLAG_CLOSE_AFTER << shift);
        }
    }

    private byte pollMethod() {
        if (methodQueueSize != 0) {
            byte entry = (byte) (methodQueue & ENTRY_MASK);
            methodQueue >>>= ENTRY_BITS;
            methodQueueSize--;
            return entry;
        }

        Queue<Byte> overflowQueue = methodOverflowQueue;
        if (overflowQueue != null) {
            Byte entry = overflowQueue.poll();
            if (overflowQueue.isEmpty()) {
                methodOverflowQueue = null;
            }
            return entry != null ? entry : METHOD_FLAG_OTHER;
        }

        return METHOD_FLAG_OTHER;
    }

    private static final class FastKoraHttpRequest extends DefaultHttpRequest implements KoraHttpRequest {
        private final long contentLength;
        private final String koraMethod;

        FastKoraHttpRequest(HttpVersion version, HttpMethod method, String koraMethod, String uri, HttpHeaders headers, long contentLength) {
            super(version, method, uri, headers);
            this.contentLength = contentLength;
            this.koraMethod = koraMethod;
        }

        @Override
        public String koraMethod() {
            return koraMethod;
        }

        @Override
        public long knownContentLength() {
            return contentLength;
        }
    }

    private final class KoraServerRequestDecoder extends HttpRequestDecoder {
        private static final int FAST_HEADER_INITIAL_COPY = 512;
        private static final int FAST_HEADER_SCRATCH_SIZE = 4096;
        private static final FastThreadLocal<byte[]> HEADER_SCRATCH = new FastThreadLocal<>() {
            @Override
            protected byte[] initialValue() {
                return new byte[FAST_HEADER_SCRATCH_SIZE];
            }
        };

        private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
        private static final VarHandle INT_LE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
        private static final VarHandle SHORT_LE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

        private static final AsciiString APPLICATION_JSON = HttpHeaderValues.APPLICATION_JSON;
        private static final AsciiString APPLICATION_JSON_UTF8 = AsciiString.cached("application/json; charset=utf-8");
        private static final AsciiString APPLICATION_JSON_UTF8_COMPACT = AsciiString.cached("application/json;charset=utf-8");
        private static final AsciiString KEEP_ALIVE = HttpHeaderValues.KEEP_ALIVE;
        private static final AsciiString CLOSE = HttpHeaderValues.CLOSE;
        private static final AsciiString ACCEPT_ALL = AsciiString.cached("*/*");

        private static final int POST_LE = 0x54534f50;                  // "POST"
        private static final int HEAD_LE = 0x44414548;                  // "HEAD"
        private static final int PATC_LE = 0x43544150;                  // "PATC"
        private static final int TRAC_LE = 0x43415254;                  // "TRAC"
        private static final int QUER_LE = 0x52455551;                  // "QUER"
        private static final long OPTIONS_7_LE = 0x00534e4f4954504fL;   // "OPTIONS"
        private static final long CONNECT_7_LE = 0x005443454e4e4f43L;   // "CONNECT"
        private static final int GET_3_LE = 0x00544547;   // "GET"
        private static final int PUT_3_LE = 0x00545550;   // "PUT"
        private static final int DELE_LE = 0x454c4544;    // "DELE"
        private static final short TE_LE = 0x4554;        // "TE"

        private static final long HTTP_1_1_LE = 0x312e312f50545448L;            // "HTTP/1.1"
        private static final int HOST_LOWER_LE = 0x74736f68;                      // "host"
        private static final long ACCEPT_LOWER_6_LE = 0x0000747065636361L;        // "accept"
        private static final long USER_AGE_LOWER_LE = 0x6567612d72657375L;        // "user-age"
        private static final short NT_LOWER_LE = 0x746e;                          // "nt"
        private static final long EXPECT_LOWER_6_LE = 0x0000746365707865L;        // "expect"
        private static final long CONTENT_DASH_LOWER_LE = 0x2d746e65746e6f63L;    // "content-"
        private static final int TYPE_LOWER_LE = 0x65707974;                      // "type"
        private static final long LENGTH_LOWER_6_LE = 0x00006874676e656cL;        // "length"
        private static final long CONNECTI_LOWER_LE = 0x697463656e6e6f63L;        // "connecti"
        private static final short ON_LOWER_LE = 0x6e6f;                          // "on"
        private static final long TRANSFER_LOWER_LE = 0x726566736e617274L;        // "transfer"
        private static final long ENCODING_LOWER_LE = 0x676e69646f636e65L;        // "encoding"
        private static final long ACCEPT_E_LOWER_LE = 0x652d747065636361L;        // "accept-e"
        private static final long NCODING_LOWER_7_LE = 0x00676e69646f636eL;       // "ncoding" (7 байт)
        private static final int GZIP_LE = 0x70697a67;

        private static final long APP_JSON_0_LE = 0x69746163696c7070L;            // "applicat"
        private static final long APP_JSON_1_LE = 0x6e6f736a2f6e6f69L;            // "ion/json"
        private static final long KEEP_ALI_LE = 0x696c612d7065656bL;              // "keep-ali"
        private static final short VE_LE = 0x6576;                                // "ve"

        private boolean discard;
        private boolean delegatingToSuper;
        private boolean pendingCloseCurrent;
        private long remainingFixedBody = -1L;

        private byte @Nullable [] cachedUriBytes0;
        private @Nullable String cachedUriValue0;
        private byte @Nullable [] cachedUriBytes1;
        private @Nullable String cachedUriValue1;
        private @Nullable AsciiString cachedHostValue;
        private @Nullable AsciiString cachedUserAgentValue;
        private @Nullable AsciiString cachedAcceptEncodingValue;

        private final boolean validateHeaders;
        private final int maxInitialLineLength;
        private final int maxHeaderSize;
        private final int maxChunkSize;

        KoraServerRequestDecoder(HttpDecoderConfig config) {
            super(config);
            this.validateHeaders = config.getHeadersFactory() != REQUEST_HEADERS_FACTORY;
            this.maxInitialLineLength = config.getMaxInitialLineLength() > 0
                ? config.getMaxInitialLineLength()
                : DEFAULT_MAX_INITIAL_LINE_LENGTH;
            this.maxHeaderSize = config.getMaxHeaderSize() > 0
                ? config.getMaxHeaderSize()
                : DEFAULT_MAX_HEADER_SIZE;
            this.maxChunkSize = config.getMaxChunkSize() > 0
                ? config.getMaxChunkSize()
                : DEFAULT_MAX_CHUNK_SIZE;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
            if (discard) {
                buffer.skipBytes(buffer.readableBytes());
                return;
            }
            int oldSize = out.size();
            boolean wasDelegating = delegatingToSuper;
            if (wasDelegating) {
                super.decode(ctx, buffer, out);
            } else if (remainingFixedBody > 0) {
                readFixedBody(buffer, out);
            } else {
                if (!tryFastDecode(buffer, out)) {
                    delegatingToSuper = true;
                    wasDelegating = true;
                    super.decode(ctx, buffer, out);
                }
            }
            int size = out.size();
            for (int i = oldSize; i < size; i++) {
                Object obj = out.get(i);
                if (obj instanceof HttpRequest req) {
                    boolean closeAfter = pendingCloseCurrent;
                    pendingCloseCurrent = false;
                    if (!enqueueMethod(req.method(), closeAfter)) {
                        markLastQueuedCloseAfter();
                        discard = true;
                        ReferenceCountUtil.release(obj);
                        while (++i < size) {
                            ReferenceCountUtil.release(out.get(i));
                        }
                        out.subList(oldSize, out.size()).clear();
                        throw new IllegalStateException("maxPipelineDepth exceeded: " + maxPipelineDepth);
                    }
                }
                if (wasDelegating && obj instanceof LastHttpContent) {
                    delegatingToSuper = false;
                }
            }
        }

        private void readFixedBody(ByteBuf buffer, List<Object> out) {
            int readable = buffer.readableBytes();
            if (readable == 0) {
                return;
            }
            int toRead = (int) Math.min(remainingFixedBody, readable);
            ByteBuf slice = buffer.readRetainedSlice(toRead);
            remainingFixedBody -= toRead;
            if (remainingFixedBody == 0) {
                remainingFixedBody = -1L;
                out.add(new DefaultLastHttpContent(slice, EmptyHttpHeaders.INSTANCE));
            } else {
                out.add(new DefaultHttpContent(slice));
            }
        }

        private boolean tryFastDecode(ByteBuf buffer, List<Object> out) {
            if (validateHeaders) {
                return false;
            }
            final int startReaderIdx = buffer.readerIndex();
            final int readable = buffer.writerIndex() - startReaderIdx;
            if (readable <= 0) {
                return true;
            }

            final byte[] arr;
            final int base;
            int limit;
            if (buffer.hasArray()) {
                arr = buffer.array();
                base = buffer.arrayOffset() + startReaderIdx;
                limit = base + readable;
            } else {
                int toCopy = Math.min(readable, FAST_HEADER_INITIAL_COPY);
                arr = HEADER_SCRATCH.get();
                buffer.getBytes(startReaderIdx, arr, 0, toCopy);
                base = 0;
                limit = toCopy;
            }

            int idx = base;
            while (idx < limit) {
                byte b = arr[idx];
                if (b == '\r' || b == '\n') {
                    idx++;
                } else {
                    break;
                }
            }
            if (idx >= limit) {
                buffer.readerIndex(startReaderIdx + (idx - base));
                return true;
            }

            int lineEnd = findLineEnd(arr, idx, limit);
            if (lineEnd == -1 && !buffer.hasArray() && limit < readable && limit < FAST_HEADER_SCRATCH_SIZE) {
                int toCopy = Math.min(readable, FAST_HEADER_SCRATCH_SIZE);
                buffer.getBytes(startReaderIdx + limit, arr, limit, toCopy - limit);
                limit = toCopy;
                lineEnd = findLineEnd(arr, idx, limit);
            }
            if (lineEnd == -2) {
                return false;
            }
            if (lineEnd == -1) {
                return readable < FAST_HEADER_SCRATCH_SIZE;
            }
            if (lineEnd - idx > maxInitialLineLength) {
                return false;
            }

            int sp1 = findByte(arr, idx, lineEnd, (byte) ' ');
            if (sp1 <= idx) {
                return false;
            }
            MatchedMethod matchedMethod = matchMethod(arr, idx, sp1 - idx, limit);
            if (matchedMethod == null) {
                return false;
            }

            int uriStart = sp1 + 1;
            int sp2 = findByte(arr, uriStart, lineEnd, (byte) ' ');
            if (sp2 <= uriStart) {
                return false;
            }
            int uriLen = sp2 - uriStart;

            int verStart = sp2 + 1;
            if (lineEnd - verStart != 8 || (long) LONG_LE.get(arr, verStart) != HTTP_1_1_LE) {
                return false;
            }

            HttpHeaders headers = headersFactory.newHeaders();
            long contentLength = -1L;
            int pos = lineEnd + 2;
            int totalHeaderBytes = 0;

            while (true) {
                int hEnd = findLineEnd(arr, pos, limit);
                if (hEnd == -1 && !buffer.hasArray() && limit < readable && limit < FAST_HEADER_SCRATCH_SIZE) {
                    int toCopy = Math.min(readable, FAST_HEADER_SCRATCH_SIZE);
                    buffer.getBytes(startReaderIdx + limit, arr, limit, toCopy - limit);
                    limit = toCopy;
                    hEnd = findLineEnd(arr, pos, limit);
                }
                if (hEnd == -2) {
                    return false;
                }
                if (hEnd == -1) {
                    return readable < FAST_HEADER_SCRATCH_SIZE;
                }
                int hLen = hEnd - pos;
                if (hLen == 0) {
                    pos = hEnd + 2;
                    break;
                }
                totalHeaderBytes += hLen;
                if (totalHeaderBytes > maxHeaderSize) {
                    return false;
                }
                byte first = arr[pos];
                if (first == ' ' || first == '\t') {
                    return false;
                }

                int colon = findByte(arr, pos, hEnd, (byte) ':');
                if (colon <= pos) {
                    return false;
                }
                byte beforeColon = arr[colon - 1];
                if (beforeColon == ' ' || beforeColon == '\t') {
                    return false;
                }
                int nameLen = colon - pos;
                int valStart = colon + 1;
                while (valStart < hEnd && (arr[valStart] == ' ' || arr[valStart] == '\t')) {
                    valStart++;
                }
                int valEnd = hEnd;
                while (valEnd > valStart && (arr[valEnd - 1] == ' ' || arr[valEnd - 1] == '\t')) {
                    valEnd--;
                }
                int valLen = valEnd - valStart;

                AsciiString headerName = matchHeaderName(arr, pos, nameLen, limit);
                if (headerName == HttpHeaderNames.TRANSFER_ENCODING || headerName == HttpHeaderNames.EXPECT) {
                    return false;
                }

                if (headerName == HttpHeaderNames.CONTENT_LENGTH) {
                    if (contentLength != -1L) {
                        return false;
                    }
                    long parsed = parsePositiveLong(arr, valStart, valEnd);
                    if (parsed < 0) {
                        return false;
                    }
                    contentLength = parsed;
                    if (parsed <= Integer.MAX_VALUE) {
                        headers.addInt(HttpHeaderNames.CONTENT_LENGTH, (int) parsed);
                    } else {
                        headers.add(HttpHeaderNames.CONTENT_LENGTH, parsed);
                    }
                } else {
                    if (headerName == null) {
                        headerName = new AsciiString(Arrays.copyOfRange(arr, pos, pos + nameLen), false);
                    }
                    AsciiString headerValue = matchHeaderValue(headerName, arr, valStart, valLen);
                    headers.add(headerName, headerValue);
                }

                pos = hEnd + 2;
            }

//            if (contentLength > maxChunkSize) {
//                return false;
//            }

            String uri = resolveUri(arr, uriStart, uriLen);
            buffer.readerIndex(startReaderIdx + (pos - base));

            var req = new FastKoraHttpRequest(
                HttpVersion.HTTP_1_1,
                matchedMethod.netty,
                matchedMethod.kora,
                uri,
                headers,
                contentLength
            );
            out.add(req);

            if (contentLength <= 0) {
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
            } else {
                int available = buffer.readableBytes();
                if (available >= contentLength) {
                    int len = (int) contentLength;
                    ByteBuf bodyBuf;
                    if (len <= 512) {
                        byte[] copy = new byte[len];
                        buffer.readBytes(copy);
                        bodyBuf = Unpooled.wrappedBuffer(copy);
                    } else {
                        bodyBuf = buffer.readRetainedSlice(len);
                    }
                    out.add(new DefaultLastHttpContent(bodyBuf, EmptyHttpHeaders.INSTANCE));
                } else if (available > 0) {
                    ByteBuf slice = buffer.readRetainedSlice(available);
                    remainingFixedBody = contentLength - available;
                    out.add(new DefaultHttpContent(slice));
                } else {
                    remainingFixedBody = contentLength;
                }
            }
            return true;
        }

        private static int findByte(byte[] arr, int from, int to, byte target) {
            if (target == ':') {
                int i = from;
                int limit8 = to - 8;
                while (i <= limit8) {
                    long word = (long) LONG_LE.get(arr, i) ^ SWAR_BROADCAST_COLON;
                    long hasZero = (word - SWAR_LSB) & ~word & SWAR_MSB;
                    if (hasZero != 0L) {
                        return i + (Long.numberOfTrailingZeros(hasZero) >>> 3);
                    }
                    i += 8;
                }
                while (i < to) {
                    if (arr[i] == ':') {
                        return i;
                    }
                    i++;
                }
                return -1;
            }
            for (int i = from; i < to; i++) {
                if (arr[i] == target) {
                    return i;
                }
            }
            return -1;
        }

        private static int findLineEnd(byte[] arr, int from, int to) {
            int i = from;
            int limit8 = to - 8;
            while (i <= limit8) {
                long word = (long) LONG_LE.get(arr, i) ^ SWAR_BROADCAST_LF;
                long hasZero = (word - SWAR_LSB) & ~word & SWAR_MSB;
                if (hasZero != 0L) {
                    int lfIdx = i + (Long.numberOfTrailingZeros(hasZero) >>> 3);
                    if (lfIdx == from || arr[lfIdx - 1] != '\r') {
                        return -2;
                    }
                    return lfIdx - 1;
                }
                i += 8;
            }
            while (i < to) {
                if (arr[i] == '\n') {
                    if (i == from || arr[i - 1] != '\r') {
                        return -2;
                    }
                    return i - 1;
                }
                i++;
            }
            return -1;
        }

        private static @Nullable MatchedMethod matchMethod(byte[] arr, int off, int len, int limit) {
            switch (len) {
                case 3:
                    if (off + 4 <= limit) {
                        int m3 = (int) INT_LE.get(arr, off) & 0x00FFFFFF;
                        if (m3 == GET_3_LE) {
                            return M_GET;
                        }
                        if (m3 == PUT_3_LE) {
                            return M_PUT;
                        }
                        return null;
                    }
                    byte b0 = arr[off];
                    byte b1 = arr[off + 1];
                    byte b2 = arr[off + 2];
                    if (b0 == 'G' && b1 == 'E' && b2 == 'T') {
                        return M_GET;
                    }
                    if (b0 == 'P' && b1 == 'U' && b2 == 'T') {
                        return M_PUT;
                    }
                    return null;
                case 4:
                    int m4 = (int) INT_LE.get(arr, off);
                    if (m4 == POST_LE) {
                        return M_POST;
                    }
                    if (m4 == HEAD_LE) {
                        return M_HEAD;
                    }
                    return null;
                case 5:
                    int m5 = (int) INT_LE.get(arr, off);
                    byte last = arr[off + 4];
                    if (m5 == PATC_LE && last == 'H') {
                        return M_PATCH;
                    }
                    if (m5 == TRAC_LE && last == 'E') {
                        return M_TRACE;
                    }
                    if (m5 == QUER_LE && last == 'Y') {
                        return M_QUERY;
                    }
                    return null;
                case 6:
                    if ((int) INT_LE.get(arr, off) == DELE_LE && (short) SHORT_LE.get(arr, off + 4) == TE_LE) {
                        return M_DELETE;
                    }
                    return null;
                case 7:
                    if (off + 8 <= limit) {
                        long m7 = (long) LONG_LE.get(arr, off) & 0x00FFFFFFFFFFFFFFL;
                        if (m7 == OPTIONS_7_LE) {
                            return M_OPTIONS;
                        }
                        if (m7 == CONNECT_7_LE) {
                            return M_CONNECT;
                        }
                    }
                    return null;
                default:
                    return null;
            }
        }

        private static @Nullable AsciiString matchHeaderName(byte[] arr, int off, int len, int limit) {
            int firstLower = arr[off] | 0x20;
            switch (firstLower) {
                case 'h':
                    if (len == 4 && ((int) INT_LE.get(arr, off) | 0x20202020) == HOST_LOWER_LE) {
                        return HttpHeaderNames.HOST;
                    }
                    return null;
                case 'a':
                    if (len == 6 && off + 8 <= limit
                        && (((long) LONG_LE.get(arr, off) | 0x2020202020202020L) & 0x0000FFFFFFFFFFFFL) == ACCEPT_LOWER_6_LE) {
                        return HttpHeaderNames.ACCEPT;
                    }
                    if (len == 15 && off + 16 <= limit
                        && ((long) LONG_LE.get(arr, off) | 0x2020202020202020L) == ACCEPT_E_LOWER_LE
                        && (((long) LONG_LE.get(arr, off + 8) | 0x2020202020202020L) & 0x00FFFFFFFFFFFFFFL) == NCODING_LOWER_7_LE) {
                        return HttpHeaderNames.ACCEPT_ENCODING;
                    }
                    return null;
                case 'u':
                    if (len == 10
                        && ((long) LONG_LE.get(arr, off) | 0x2020202020202020L) == USER_AGE_LOWER_LE
                        && ((short) SHORT_LE.get(arr, off + 8) | 0x2020) == NT_LOWER_LE) {
                        return USER_AGENT;
                    }
                    return null;
                case 'e':
                    if (len == 6 && off + 8 <= limit
                        && (((long) LONG_LE.get(arr, off) | 0x2020202020202020L) & 0x0000FFFFFFFFFFFFL) == EXPECT_LOWER_6_LE) {
                        return HttpHeaderNames.EXPECT;
                    }
                    return null;
                case 't':
                    if (len == 17
                        && ((long) LONG_LE.get(arr, off) | 0x2020202020202020L) == TRANSFER_LOWER_LE
                        && arr[off + 8] == '-'
                        && ((long) LONG_LE.get(arr, off + 9) | 0x2020202020202020L) == ENCODING_LOWER_LE) {
                        return HttpHeaderNames.TRANSFER_ENCODING;
                    }
                    return null;
                case 'c':
                    switch (len) {
                        case 10:
                            if (((long) LONG_LE.get(arr, off) | 0x2020202020202020L) == CONNECTI_LOWER_LE
                                && ((short) SHORT_LE.get(arr, off + 8) | 0x2020) == ON_LOWER_LE) {
                                return HttpHeaderNames.CONNECTION;
                            }
                            return null;
                        case 12:
                            if (((long) LONG_LE.get(arr, off) | 0x2020202020202020L) == CONTENT_DASH_LOWER_LE
                                && ((int) INT_LE.get(arr, off + 8) | 0x20202020) == TYPE_LOWER_LE) {
                                return HttpHeaderNames.CONTENT_TYPE;
                            }
                            return null;
                        case 14:
                            if (((long) LONG_LE.get(arr, off) | 0x2020202020202020L) == CONTENT_DASH_LOWER_LE
                                && off + 16 <= limit
                                && (((long) LONG_LE.get(arr, off + 8) | 0x2020202020202020L) & 0x0000FFFFFFFFFFFFL) == LENGTH_LOWER_6_LE) {
                                return HttpHeaderNames.CONTENT_LENGTH;
                            }
                            return null;
                        default:
                            return null;
                    }
                default:
                    return null;
            }
        }

        private AsciiString matchHeaderValue(AsciiString name, byte[] arr, int off, int len) {
            if (len == 0) {
                return AsciiString.EMPTY_STRING;
            }
            if (name == HttpHeaderNames.CONTENT_TYPE || name == HttpHeaderNames.ACCEPT) {
                if (len == 16 && (long) LONG_LE.get(arr, off) == APP_JSON_0_LE && (long) LONG_LE.get(arr, off + 8) == APP_JSON_1_LE) {
                    return APPLICATION_JSON;
                }
                if (len == 31 && matchesAscii(arr, off, "application/json; charset=utf-8")) {
                    return APPLICATION_JSON_UTF8;
                }
                if (len == 30 && matchesAscii(arr, off, "application/json;charset=utf-8")) {
                    return APPLICATION_JSON_UTF8_COMPACT;
                }
                if (len == 3 && arr[off] == '*' && arr[off + 1] == '/' && arr[off + 2] == '*') {
                    return ACCEPT_ALL;
                }
            } else if (name == HttpHeaderNames.HOST) {
                return resolveHost(arr, off, len);
            } else if (name == USER_AGENT) {
                return resolveUserAgent(arr, off, len);
            } else if (name == HttpHeaderNames.ACCEPT_ENCODING) {
                if (len == 4 && (int) INT_LE.get(arr, off) == GZIP_LE) {
                    return HttpHeaderValues.GZIP;
                }
                AsciiString cached = cachedAcceptEncodingValue;
                if (cached != null && cached.length() == len && matchesAsciiBytes(cached, arr, off, len)) {
                    return cached;
                }
                var val = new AsciiString(Arrays.copyOfRange(arr, off, off + len), false);
                cachedAcceptEncodingValue = val;
                return val;
            } else if (name == HttpHeaderNames.CONNECTION) {
                if (len == 10 && (long) LONG_LE.get(arr, off) == KEEP_ALI_LE && (short) SHORT_LE.get(arr, off + 8) == VE_LE) {
                    return KEEP_ALIVE;
                }
                if (len == 5 && arr[off] == 'c' && arr[off + 1] == 'l' && arr[off + 2] == 'o' && arr[off + 3] == 's' && arr[off + 4] == 'e') {
                    return CLOSE;
                }
            }
            return new AsciiString(Arrays.copyOfRange(arr, off, off + len), false);
        }

        private String resolveUri(byte[] arr, int off, int len) {
            byte[] c0 = cachedUriBytes0;
            if (c0 != null && c0.length == len && Arrays.equals(c0, 0, len, arr, off, off + len)) {
                return Objects.requireNonNull(cachedUriValue0);
            }
            byte[] c1 = cachedUriBytes1;
            if (c1 != null && c1.length == len && Arrays.equals(c1, 0, len, arr, off, off + len)) {
                String hit = Objects.requireNonNull(cachedUriValue1);
                cachedUriBytes1 = c0;
                cachedUriValue1 = cachedUriValue0;
                cachedUriBytes0 = c1;
                cachedUriValue0 = hit;
                return hit;
            }
            byte[] fresh = Arrays.copyOfRange(arr, off, off + len);
            String uri = new String(fresh, CharsetUtil.US_ASCII);
            cachedUriBytes1 = c0;
            cachedUriValue1 = cachedUriValue0;
            cachedUriBytes0 = fresh;
            cachedUriValue0 = uri;
            return uri;
        }

        private AsciiString resolveHost(byte[] arr, int off, int len) {
            AsciiString cached = cachedHostValue;
            if (cached != null && cached.length() == len && matchesAsciiBytes(cached, arr, off, len)) {
                return cached;
            }
            AsciiString host = new AsciiString(Arrays.copyOfRange(arr, off, off + len), false);
            cachedHostValue = host;
            return host;
        }

        private AsciiString resolveUserAgent(byte[] arr, int off, int len) {
            AsciiString cached = cachedUserAgentValue;
            if (cached != null && cached.length() == len && matchesAsciiBytes(cached, arr, off, len)) {
                return cached;
            }
            AsciiString ua = new AsciiString(Arrays.copyOfRange(arr, off, off + len), false);
            cachedUserAgentValue = ua;
            return ua;
        }

        private static boolean matchesAsciiBytes(AsciiString cached, byte[] arr, int off, int len) {
            byte[] cArr = cached.array();
            int cOff = cached.arrayOffset();
            return Arrays.equals(cArr, cOff, cOff + len, arr, off, off + len);
        }

        private static long parsePositiveLong(byte[] arr, int start, int end) {
            if (start >= end) {
                return -1L;
            }
            long val = 0;
            for (int i = start; i < end; i++) {
                int d = arr[i] - '0';
                if (d < 0 || d > 9) {
                    return -1L;
                }
                val = val * 10 + d;
                if (val < 0) {
                    return -1L;
                }
            }
            return val;
        }

        private static boolean matchesAscii(byte[] arr, int off, String ascii) {
            int len = ascii.length();
            for (int i = 0; i < len; i++) {
                if (arr[off + i] != (byte) ascii.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected void handleTransferEncodingChunkedWithContentLength(HttpMessage message) {
            super.handleTransferEncodingChunkedWithContentLength(message);
            pendingCloseCurrent = true;
        }
    }

    // 5. В KoraServerResponseEncoder
    private final class KoraServerResponseEncoder extends HttpResponseEncoder {
        private static final byte[] STATUS_200_OK = "HTTP/1.1 200 OK\r\n".getBytes(CharsetUtil.US_ASCII);
        private static final byte[] STATUS_204_NO_CONTENT = "HTTP/1.1 204 No Content\r\n".getBytes(CharsetUtil.US_ASCII);

        private byte methodFlag;

        @Override
        protected void encodeInitialLine(ByteBuf buf, HttpResponse response) throws Exception {
            if (response.protocolVersion() == HttpVersion.HTTP_1_1) {
                HttpResponseStatus status = response.status();
                if (status == HttpResponseStatus.OK) {
                    buf.writeBytes(STATUS_200_OK);
                    return;
                }
                if (status == HttpResponseStatus.NO_CONTENT) {
                    buf.writeBytes(STATUS_204_NO_CONTENT);
                    return;
                }
            }
            super.encodeInitialLine(buf, response);
        }

        @Override
        protected void encodeHeaders(HttpHeaders headers, ByteBuf buf) {
            if (headers instanceof FlatHttpHeaders flat) {
                flat.encodeTo(buf);
            } else {
                super.encodeHeaders(headers, buf);
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if ((methodFlag & FLAG_CLOSE_AFTER) != 0 && msg instanceof LastHttpContent) {
                methodFlag = 0;
                promise = promise.unvoid().addListener(ChannelFutureListener.CLOSE);
            }
            super.write(ctx, msg, promise);
        }

        @Override
        protected void sanitizeHeadersBeforeEncode(HttpResponse msg, boolean isAlwaysEmpty) {
            if (!isAlwaysEmpty && (methodFlag & METHOD_MASK) == METHOD_FLAG_CONNECT
                && msg.status().codeClass() == HttpStatusClass.SUCCESS) {
                msg.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                return;
            }
            super.sanitizeHeadersBeforeEncode(msg, isAlwaysEmpty);
        }

        @Override
        protected boolean isContentAlwaysEmpty(HttpResponse msg) {
            if (msg.status().codeClass() == HttpStatusClass.INFORMATIONAL) {
                return super.isContentAlwaysEmpty(msg);
            }
            methodFlag = pollMethod();
            return (methodFlag & METHOD_MASK) == METHOD_FLAG_HEAD || super.isContentAlwaysEmpty(msg);
        }
    }

    private static final class MatchedMethod {
        final io.netty.handler.codec.http.HttpMethod netty;
        final String kora;

        MatchedMethod(io.netty.handler.codec.http.HttpMethod netty, String kora) {
            this.netty = netty;
            this.kora = kora;
        }
    }

    private static final MatchedMethod M_GET = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.GET,
        io.koraframework.http.common.HttpMethod.GET
    );
    private static final MatchedMethod M_POST = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.POST,
        io.koraframework.http.common.HttpMethod.POST
    );
    private static final MatchedMethod M_PUT = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.PUT,
        io.koraframework.http.common.HttpMethod.PUT
    );
    private static final MatchedMethod M_DELETE = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.DELETE,
        io.koraframework.http.common.HttpMethod.DELETE
    );
    private static final MatchedMethod M_HEAD = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.HEAD,
        io.koraframework.http.common.HttpMethod.HEAD
    );
    private static final MatchedMethod M_PATCH = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.PATCH,
        io.koraframework.http.common.HttpMethod.PATCH
    );
    private static final MatchedMethod M_OPTIONS = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.OPTIONS,
        io.koraframework.http.common.HttpMethod.OPTIONS
    );
    private static final MatchedMethod M_CONNECT = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.CONNECT,
        io.koraframework.http.common.HttpMethod.CONNECT
    );
    private static final MatchedMethod M_TRACE = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.TRACE,
        io.koraframework.http.common.HttpMethod.TRACE
    );
    private static final MatchedMethod M_QUERY = new MatchedMethod(
        io.netty.handler.codec.http.HttpMethod.valueOf(io.koraframework.http.common.HttpMethod.QUERY),
        io.koraframework.http.common.HttpMethod.QUERY
    );
}

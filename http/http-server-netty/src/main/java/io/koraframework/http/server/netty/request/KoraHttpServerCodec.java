package io.koraframework.http.server.netty.request;

import io.koraframework.http.server.netty.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

public final class KoraHttpServerCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder> implements HttpServerUpgradeHandler.SourceCodec {

    static final int DEFAULT_MAX_PIPELINE_DEPTH = 128;

    private static final byte METHOD_FLAG_HEAD = 1;
    private static final byte METHOD_FLAG_CONNECT = 2;
    private static final byte METHOD_FLAG_OTHER = 3;

    private static final int METHOD_FLAG_BITS = 2;
    private static final int INLINE_QUEUE_CAPACITY = Long.SIZE / METHOD_FLAG_BITS;

    private static final AsciiString USER_AGENT = AsciiString.cached("User-Agent");
    private static final long USER_AGENT_LONG_0 = 'U' | 's' << 8 | 'e' << 16 | 'r' << 24 |
            (long) '-' << 32 | (long) 'A' << 40 | (long) 'g' << 48 | (long) 'e' << 56;
    private static final short USER_AGENT_SHORT_1 = 'n' | 't' << 8;

    private long methodQueue;
    private int methodQueueSize;
    @Nullable
    private Queue<Byte> methodOverflowQueue;
    private final int maxPipelineDepth;
    private boolean mustCloseAfterResponse;

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

    private boolean enqueueMethod(HttpMethod method) {
        Queue<Byte> overflowQueue = methodOverflowQueue;
        int currentDepth = methodQueueSize + (overflowQueue != null ? overflowQueue.size() : 0);
        if (currentDepth >= maxPipelineDepth) {
            return false;
        }

        final byte flag;
        if (HttpMethod.HEAD.equals(method)) {
            flag = METHOD_FLAG_HEAD;
        } else if (HttpMethod.CONNECT.equals(method)) {
            flag = METHOD_FLAG_CONNECT;
        } else {
            flag = METHOD_FLAG_OTHER;
        }

        if (overflowQueue != null) {
            overflowQueue.add(flag);
            return true;
        }

        if (methodQueueSize < INLINE_QUEUE_CAPACITY) {
            methodQueue |= (long) flag << (methodQueueSize << 1);
            methodQueueSize++;
        } else {
            overflowQueue = new ArrayDeque<>(4);
            overflowQueue.add(flag);
            methodOverflowQueue = overflowQueue;
        }
        return true;
    }

    private byte pollMethod() {
        if (methodQueueSize != 0) {
            byte flag = (byte) (methodQueue & 0x3L);
            methodQueue >>>= METHOD_FLAG_BITS;
            methodQueueSize--;
            return flag;
        }

        Queue<Byte> overflowQueue = methodOverflowQueue;
        if (overflowQueue != null) {
            Byte flag = overflowQueue.poll();
            if (overflowQueue.isEmpty()) {
                methodOverflowQueue = null;
            }
            return flag != null ? flag : METHOD_FLAG_OTHER;
        }

        return METHOD_FLAG_OTHER;
    }

    private final class KoraServerRequestDecoder extends HttpRequestDecoder {
        private boolean discard;
        private byte @Nullable [] cachedUriBytes;
        private @Nullable String cachedUriValue;

        KoraServerRequestDecoder(HttpDecoderConfig config) {
            super(config);
        }

        @Override
        protected String splitSecondWordInitialLine(final byte[] asciiContent, final int start, final int length) {
            byte[] cached = cachedUriBytes;
            if (cached != null && cached.length == length && Arrays.equals(cached, 0, length, asciiContent, start, start + length)) {
                return cachedUriValue;
            }
            String uri = super.splitSecondWordInitialLine(asciiContent, start, length);
            cachedUriBytes = Arrays.copyOfRange(asciiContent, start, start + length);
            cachedUriValue = uri;
            return uri;
        }

        @Override
        protected AsciiString splitHeaderName(final byte[] sb, final int start, final int length) {
            if (length == 10 && sb[start] == 'U' && isUserAgent(sb, start)) {
                return USER_AGENT;
            }
            return super.splitHeaderName(sb, start, length);
        }

        private static boolean isUserAgent(final byte[] sb, final int start) {
            final long first8 = sb[start] |
                    sb[start + 1] << 8 |
                    sb[start + 2] << 16 |
                    sb[start + 3] << 24 |
                    (long) sb[start + 4] << 32 |
                    (long) sb[start + 5] << 40 |
                    (long) sb[start + 6] << 48 |
                    (long) sb[start + 7] << 56;
            if (first8 != USER_AGENT_LONG_0) {
                return false;
            }
            final short last2 = (short) (sb[start + 8] | sb[start + 9] << 8);
            return last2 == USER_AGENT_SHORT_1;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
            if (discard) {
                buffer.skipBytes(buffer.readableBytes());
                return;
            }
            int oldSize = out.size();
            super.decode(ctx, buffer, out);
            int size = out.size();
            for (int i = oldSize; i < size; i++) {
                Object obj = out.get(i);
                if (obj instanceof HttpRequest req) {
                    if (!enqueueMethod(req.method())) {
                        mustCloseAfterResponse = true;
                        discard = true;
                        ReferenceCountUtil.release(obj);
                        while (++i < size) {
                            ReferenceCountUtil.release(out.get(i));
                        }
                        out.clear();
                        throw new IllegalStateException("maxPipelineDepth exceeded: " + maxPipelineDepth);
                    }
                }
            }
        }

        @Override
        protected void handleTransferEncodingChunkedWithContentLength(HttpMessage message) {
            super.handleTransferEncodingChunkedWithContentLength(message);
            mustCloseAfterResponse = true;
        }
    }

    private final class KoraServerResponseEncoder extends HttpResponseEncoder {
        private byte methodFlag;

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
            if (mustCloseAfterResponse && msg instanceof LastHttpContent) {
                mustCloseAfterResponse = false;
                promise = promise.unvoid().addListener(ChannelFutureListener.CLOSE);
            }
            super.write(ctx, msg, promise);
        }

        @Override
        protected void sanitizeHeadersBeforeEncode(HttpResponse msg, boolean isAlwaysEmpty) {
            if (!isAlwaysEmpty && methodFlag == METHOD_FLAG_CONNECT
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
            return methodFlag == METHOD_FLAG_HEAD || super.isContentAlwaysEmpty(msg);
        }
    }
}

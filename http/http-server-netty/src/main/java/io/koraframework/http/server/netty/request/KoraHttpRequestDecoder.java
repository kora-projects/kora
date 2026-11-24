package io.koraframework.http.server.netty.request;

import io.netty.handler.codec.http.HttpDecoderConfig;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.util.AsciiString;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public final class KoraHttpRequestDecoder extends HttpRequestDecoder {
    private static final AsciiString USER_AGENT = AsciiString.cached("User-Agent");

    private static final long USER_AGENT_LONG_0 = 'U' | 's' << 8 | 'e' << 16 | 'r' << 24 |
        (long) '-' << 32 | (long) 'A' << 40 | (long) 'g' << 48 | (long) 'e' << 56;
    private static final short USER_AGENT_SHORT_1 = 'n' | 't' << 8;

    private byte @Nullable [] cachedUriBytes;
    private @Nullable String cachedUriValue;

    public KoraHttpRequestDecoder(HttpDecoderConfig config) {
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
}

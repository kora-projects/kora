package io.koraframework.http.server.netty.body;

/**
 * Body size limits.
 *
 * @param maxBodySize max body size
 * @param maxBufferSize max buffer size
 */
public record BodySizeLimits(
    long maxBodySize,
    long maxBufferSize
) {
    public static final BodySizeLimits UNLIMITED = new BodySizeLimits(Long.MAX_VALUE, Integer.MAX_VALUE);
}

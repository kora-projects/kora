package io.koraframework.http.server.netty.compression;

import io.netty.handler.codec.http.*;

import java.util.*;

public final class DefaultHttpCompressionStrategy {

    private final int compressionThreshold;
    private final int compressionLevel;
    private final int maxZstdEncodeSize;

    public DefaultHttpCompressionStrategy(int compressionThreshold, int compressionLevel, int maxZstdEncodeSize) {
        this.compressionThreshold = compressionThreshold;
        this.compressionLevel = compressionLevel;
        this.maxZstdEncodeSize = maxZstdEncodeSize;
    }

    public boolean isEnabled() {
        return compressionThreshold >= 0;
    }

    public boolean shouldCompress(HttpResponse response) {
        if (!isEnabled()) {
            return false;
        }
        return shouldCompress(response, HttpUtil.getContentLength(response, -1));
    }

    public boolean shouldCompress(HttpResponse response, long contentLength) {
        if (!isEnabled()) {
            return false;
        }
        if (contentLength != -1 && contentLength < compressionThreshold) {
            return false;
        }
        var contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        return contentType != null && isTextBased(contentType);
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public int getMaxZstdEncodeSize() {
        return maxZstdEncodeSize;
    }

    private static boolean isTextBased(String contentType) {
        int end = contentType.indexOf(';');
        if (end == -1) {
            end = contentType.length();
        }
        int start = 0;
        while (start < end && contentType.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && contentType.charAt(end - 1) <= ' ') {
            end--;
        }
        int len = end - start;
        if (len == 16 && contentType.regionMatches(true, start, "application/json", 0, 16)) {
            return true;
        }
        if (len > 5 && contentType.regionMatches(true, start, "text/", 0, 5)) {
            return true;
        }
        var mime = contentType.substring(start, end).toLowerCase(Locale.ENGLISH);
        return mime.endsWith("+json")
            || mime.equals("application/javascript")
            || mime.equals("application/xml")
            || mime.endsWith("+xml")
            || mime.equals("application/xhtml+xml")
            || mime.equals("image/svg+xml");
    }
}

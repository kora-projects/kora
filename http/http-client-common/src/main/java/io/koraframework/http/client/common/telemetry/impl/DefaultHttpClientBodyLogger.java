package io.koraframework.http.client.common.telemetry.impl;

import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class DefaultHttpClientBodyLogger {

    @Nullable
    public String convertResponseBody(ByteBuffer body, @Nullable String contentType) {
        return convertBody(body, contentType);
    }

    @Nullable
    public String convertRequestBody(ByteBuffer body, @Nullable String contentType) {
        return convertBody(body, contentType);
    }

    @Nullable
    protected String convertBody(ByteBuffer body, @Nullable String contentType) {
        var charset = detectCharset(contentType);
        if (charset == null) {
            return null;
        }

        try {
            return charset
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith("?")
                .decode(body)
                .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    @Nullable
    protected Charset detectCharset(String contentType) {
        if (contentType == null) {
            return null;
        }

        contentType = contentType.toLowerCase(Locale.ROOT);

        var split = contentType.split("; ?charset=", 2);
        if (split.length == 2) {
            try {
                var charsetName = split[1].strip();
                int end = charsetName.indexOf(';');
                if (end != -1) {
                    charsetName = charsetName.substring(0, end);
                }
                if (charsetName.startsWith("\"") && charsetName.endsWith("\"")) {
                    charsetName = charsetName.substring(1, charsetName.length() - 1);
                }

                return Charset.forName(charsetName);
            } catch (Exception e) {
                // ignore
                return null;
            }
        }

        var mimeType = split[0].strip();
        if (mimeType.contains("text/")
            || mimeType.contains("json")
            || mimeType.contains("xml")) {
            return StandardCharsets.UTF_8;
        }

        if (mimeType.contains("application/x-www-form-urlencoded")) {
            return StandardCharsets.US_ASCII;
        }
        return null;
    }
}

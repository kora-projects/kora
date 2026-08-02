package io.koraframework.http.client.common.util;

import java.net.URLEncoder;
import java.nio.charset.Charset;

public final class EncoderUtils {

    private EncoderUtils() {}

    /**
     * @param value                    {@code String} to be translated.
     * @param charset                  the given charset
     * @param disableEncodeSpaceToPlus disable encoding space as + char
     * @return the translated {@code String}.
     * @throws NullPointerException if {@code value} or {@code charset} is {@code null}.
     */
    public static String encode(String value, Charset charset, boolean disableEncodeSpaceToPlus) {
        var encoded = URLEncoder.encode(value, charset);
        if (disableEncodeSpaceToPlus) {
            encoded = encoded.replace("+", "%20");
        }
        return encoded;
    }
}

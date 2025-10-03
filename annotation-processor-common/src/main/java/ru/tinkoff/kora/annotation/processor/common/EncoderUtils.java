package ru.tinkoff.kora.annotation.processor.common;

import java.net.URLEncoder;
import java.nio.charset.Charset;

public class EncoderUtils {

    private EncoderUtils() {}


    /**
     * @param s {@code String} to be translated.
     * @param charset the given charset
     * @param disableEncodeSpaceToPlus disable encoding space as + char
     * @return  the translated {@code String}.
     * @throws NullPointerException if {@code s} or {@code charset} is {@code null}.
     */
    public static String encode(String s, Charset charset, boolean disableEncodeSpaceToPlus) {
        var encoded = URLEncoder.encode(s, charset);
        if (disableEncodeSpaceToPlus) {
            encoded = encoded.replace("+", "%20");
        }
        return encoded;
    }
}

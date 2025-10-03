package ru.tinkoff.kora.ksp.common

import java.net.URLEncoder
import java.nio.charset.Charset

object EncoderUtils {

    /**
     * @param s {@code String} to be translated.
     * @param charset the given charset
     * @param disableEncodeSpaceToPlus disable encoding space as + char
     * @return  the translated {@code String}.
     */
    fun encode(s: String, charset: Charset, disableEncodeSpaceToPlus: Boolean): String {
        var encoded = URLEncoder.encode(s, charset)
        if (disableEncodeSpaceToPlus) {
            encoded = encoded.replace("+", "%20")
        }
        return encoded
    }
}

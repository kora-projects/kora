package ru.tinkoff.kora.ksp.common

import java.net.URLEncoder
import java.nio.charset.Charset

object EncoderUtils {

    /**
     * @param s {@code String} to be translated.
     * @param charset the given charset
     * @param encodeSpaceToPlus encode space as + char
     * @return  the translated {@code String}.
     */
    fun encode(s: String, charset: Charset, escapeSpaceToPlus: Boolean): String {
        var encoded = URLEncoder.encode(s, charset)
        if (!escapeSpaceToPlus) {
            encoded = encoded.replace("+", "%20")
        }
        return encoded
    }
}

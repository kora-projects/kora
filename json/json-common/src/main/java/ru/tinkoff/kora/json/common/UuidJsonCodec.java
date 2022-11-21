package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * @see com.fasterxml.jackson.databind.deser.std.UUIDDeserializer
 * @see com.fasterxml.jackson.databind.ser.std.UUIDSerializer
 */
public class UuidJsonCodec implements JsonReader<UUID>, JsonWriter<UUID> {
    final static char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    final static int[] HEX_DIGITS = new int[127];

    static {
        Arrays.fill(HEX_DIGITS, -1);
        for (int i = 0; i < 10; ++i) {HEX_DIGITS['0' + i] = i;}
        for (int i = 0; i < 6; ++i) {
            HEX_DIGITS['a' + i] = 10 + i;
            HEX_DIGITS['A' + i] = 10 + i;
        }
    }

    @Override
    public void write(JsonGenerator gen, @Nullable UUID object) throws IOException {
        if (object == null) {
            gen.writeNull();
            return;
        }
        final char[] ch = new char[36];
        final long msb = object.getMostSignificantBits();
        _appendInt((int) (msb >> 32), ch, 0);
        ch[8] = '-';
        int i = (int) msb;
        _appendShort(i >>> 16, ch, 9);
        ch[13] = '-';
        _appendShort(i, ch, 14);
        ch[18] = '-';

        final long lsb = object.getLeastSignificantBits();
        _appendShort((int) (lsb >>> 48), ch, 19);
        ch[23] = '-';
        _appendShort((int) (lsb >>> 32), ch, 24);
        _appendInt((int) lsb, ch, 28);

        gen.writeString(ch, 0, 36);
    }

    @Nullable
    @Override
    public UUID read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        var id = parser.getValueAsString();
        if (id.length() != 36) {
            /* 14-Sep-2013, tatu: One trick we do allow, Base64-encoding, since we know
             *   length it must have...
             */
            if (id.length() == 24) {
                byte[] stuff = Base64Variants.getDefaultVariant().decode(id);
                return _fromBytes(stuff, parser);
            }
            return _badFormat(id, parser);
        }

        // verify hyphens first:
        if ((id.charAt(8) != '-') || (id.charAt(13) != '-')
            || (id.charAt(18) != '-') || (id.charAt(23) != '-')) {
            _badFormat(id, parser);
        }
        long l1 = intFromChars(id, 0, parser);
        l1 <<= 32;
        long l2 = ((long) shortFromChars(id, 9, parser)) << 16;
        l2 |= shortFromChars(id, 14, parser);
        long hi = l1 + l2;

        int i1 = (shortFromChars(id, 19, parser) << 16) | shortFromChars(id, 24, parser);
        l1 = i1;
        l1 <<= 32;
        l2 = intFromChars(id, 28, parser);
        l2 = (l2 << 32) >>> 32; // sign removal, Java-style. Ugh.
        long lo = l1 | l2;

        return new UUID(hi, lo);
    }

    private static void _appendInt(int bits, char[] ch, int offset) {
        _appendShort(bits >> 16, ch, offset);
        _appendShort(bits, ch, offset + 4);
    }

    private static void _appendShort(int bits, char[] ch, int offset) {
        ch[offset] = HEX_CHARS[(bits >> 12) & 0xF];
        ch[++offset] = HEX_CHARS[(bits >> 8) & 0xF];
        ch[++offset] = HEX_CHARS[(bits >> 4) & 0xF];
        ch[++offset] = HEX_CHARS[bits & 0xF];
    }

    private UUID _badFormat(String id, JsonParser parser) throws JsonParseException {
        throw new JsonParseException(
            parser,
            "UUID has to be represented by standard 36-char representation, got '%s'".formatted(id)
        );
    }


    private int intFromChars(String str, int index, JsonParser ctxt) throws JsonParseException {
        return (byteFromChars(str, index, ctxt) << 24)
               + (byteFromChars(str, index + 2, ctxt) << 16)
               + (byteFromChars(str, index + 4, ctxt) << 8)
               + byteFromChars(str, index + 6, ctxt);
    }

    private int shortFromChars(String str, int index, JsonParser ctxt) throws JsonParseException {
        return (byteFromChars(str, index, ctxt) << 8) + byteFromChars(str, index + 2, ctxt);
    }

    private int byteFromChars(String str, int index, JsonParser ctxt) throws JsonParseException {
        final char c1 = str.charAt(index);
        final char c2 = str.charAt(index + 1);

        if (c1 <= 127 && c2 <= 127) {
            int hex = (HEX_DIGITS[c1] << 4) | HEX_DIGITS[c2];
            if (hex >= 0) {
                return hex;
            }
        }
        if (c1 > 127 || HEX_DIGITS[c1] < 0) {
            return _badChar(str, index, ctxt, c1);
        }
        return _badChar(str, index + 1, ctxt, c2);
    }

    private int _badChar(String uuidStr, int index, JsonParser ctxt, char c) throws JsonParseException {
        // 15-May-2016, tatu: Ideally should not throw, but call `handleWeirdStringValue`...
        //   however, control flow is gnarly here, so for now just throw
        throw new JsonParseException(ctxt, String.format(
            "Non-hex character '%c' (value 0x%s), not valid for UUID String (%s)",
            c, Integer.toHexString(c), uuidStr
        ));
    }

    private UUID _fromBytes(byte[] bytes, JsonParser ctxt) throws JsonParseException {
        if (bytes.length != 16) {
            throw new JsonParseException(ctxt,
                "Can only construct UUIDs from byte[16]; got " + bytes.length + " bytes");
        }
        return new UUID(_long(bytes, 0), _long(bytes, 8));
    }

    private static long _long(byte[] b, int offset) {
        long l1 = ((long) _int(b, offset)) << 32;
        long l2 = _int(b, offset + 4);
        // faster to just do it than check if it has sign
        l2 = (l2 << 32) >>> 32; // to get rid of sign
        return l1 | l2;
    }

    private static int _int(byte[] b, int offset) {
        return (b[offset] << 24) | ((b[offset + 1] & 0xFF) << 16) | ((b[offset + 2] & 0xFF) << 8) | (b[offset + 3] & 0xFF);
    }

}

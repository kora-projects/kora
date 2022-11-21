package ru.tinkoff.kora.soap.client.common;


import org.apache.commons.codec.binary.Base64InputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MultipartParser {
    private static final byte[] end = "--".getBytes(UTF_8);
    private static final byte[] br = "\r\n".getBytes(UTF_8);
    private static final Pattern BOUNDARY_PATTERN = Pattern.compile(".*boundary=(?<boundary>.*?[;$]).*");
    private static final Pattern START_PATTERN = Pattern.compile(".*start=(?<start>.*?[;$]).*");

    public record MultipartMeta(String boundary, String start) {}

    public static MultipartMeta parseMeta(String contentType) {
        var boundary = BOUNDARY_PATTERN.matcher(contentType);
        var start = START_PATTERN.matcher(contentType);
        if (!boundary.matches() || !start.matches()) {
            throw new IllegalStateException();
        }
        var boundaryStr = boundary.group("boundary");
        var startStr = start.group("start");
        if (boundaryStr.endsWith(";")) {
            boundaryStr = boundaryStr.substring(0, boundaryStr.length() - 1);
        }
        if (boundaryStr.startsWith("\"") && boundaryStr.endsWith("\"")) {
            boundaryStr = boundaryStr.substring(1, boundaryStr.length() - 1);
        }
        if (startStr.endsWith(";")) {
            startStr = startStr.substring(0, startStr.length() - 1);
        }
        if (startStr.startsWith("\"") && startStr.endsWith("\"")) {
            startStr = startStr.substring(1, startStr.length() - 1);
        }
        return new MultipartMeta(
            boundaryStr,
            startStr
        );
    }

    private static Part parse(byte[] data, int offset, int length) {
        var start = offset;
        int i = 0;
        String contentType = null;
        String contentId = null;
        while (i < length) {
            var buf = "\r\n".getBytes(UTF_8);
            if (isMatch(data, buf, i + offset)) {
                var line = new String(data, start, i + offset - start, UTF_8);
                if (!line.isEmpty()) {
                    var header = line.split(": ");
                    if (header[0].equalsIgnoreCase("content-type")) {
                        contentType = header[1];
                    } else if (header[0].equalsIgnoreCase("content-id")) {
                        contentId = header[1];
                    }
                    start = i + offset + 2;
                } else {
                    break;
                }
            }
            i++;
        }
        return new Part(contentId, contentType, data, i + offset + 2, length - (i + 2));
    }

    public static Map<String, Part> parse(byte[] body, String boundaryString) {
        var boundary = ("--" + boundaryString).getBytes();
        var result = new HashMap<String, Part>();
        var pos = isMatch(body, br, 0)
            ? 2
            : 0;
        if (!isMatch(body, boundary, pos)) {
            throw new IllegalStateException("Can't read message: invalid start");
        }
        int blockStart = boundary.length + pos;
        while (true) {
            var buf = new byte[2];
            buf[0] = body[blockStart];
            buf[1] = body[blockStart + 1];
            if (Arrays.equals(buf, br)) {
                blockStart = readPart(body, blockStart + 2, boundary, result);
            } else if (Arrays.equals(buf, end)) {
                return result;
            } else {
                throw new IllegalStateException("Can't read message: invalid separation symbol");
            }
        }
    }

    private static int readPart(byte[] body, int i, byte[] boundary, Map<String, Part> result) {
        for (int j = i; j < body.length; j++) {
            if (isMatch(body, boundary, j)) {
                var part = parse(body, i, j - i);
                result.put(part.contentId, part);
                return j + boundary.length;
            }
        }
        throw new IllegalStateException("Can't read message: no ending symbol");
    }

    public static boolean isMatch(byte[] input, byte[] pattern, int pos) {
        if (pos == input.length - 1) {
            return true;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i] != input[pos + i]) {
                return false;
            }
        }
        return true;
    }

    public static void writeContentReplacedWithCids(List<MultipartParser.Part> parts, OutputStream os) throws IOException {
        var xml = parts.get(0);
        var cids = findCids(parts);
        if (cids.isEmpty()) {
            copyLarge(xml.getContentStream(), os);
            return;
        }
        var currentIndex = xml.offset();
        for (var cid : cids) {
            os.write(xml.data(), currentIndex, cid.startIndex() - currentIndex);
            var sidIs = cid.cidPart().getContentStream();
            copyLarge(new Base64InputStream(sidIs, true, -1, null), os);
            currentIndex = cid.endIndex();
        }
        var endIndex = xml.offset() + xml.length();
        var remainLength = endIndex - currentIndex;
        os.write(xml.data(), currentIndex, remainLength);
    }


    private static long copyLarge(InputStream input, OutputStream output) throws IOException {
        long count = 0L;
        byte[] buffer = new byte[8092];
        int n;
        if (input != null) {
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
                count += n;
            }
        }

        return count;
    }


    private static List<CidHref> findCids(List<Part> parts) {
        var xml = parts.get(0);
        var searchFor = "<xop:Include xmlns:xop=\"http://www.w3.org/2004/08/xop/include\" href=\"".getBytes(UTF_8);
        var result = new ArrayList<CidHref>(4);
        var lastCid = xml.offset();
        int endOfXml = xml.offset() + xml.length();
        for (int start = indexOf(xml.data(), searchFor, lastCid, endOfXml); start > 0; start = indexOf(xml.data(), searchFor, lastCid, endOfXml)) {
            var end = indexOf(xml.data(), "\"/>".getBytes(UTF_8), start, endOfXml);
            var cidBytes = Arrays.copyOfRange(xml.data(), start + searchFor.length + 4, end);
            var cid = URLDecoder.decode(new String(cidBytes, UTF_8), UTF_8);
            var cidPart = parts.stream()
                .filter(part -> part.contentId().equals(String.format("<%s>", cid)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No part with cid " + cid));
            lastCid = end + 3;
            result.add(new CidHref(start, end + 3, cid, cidPart));
        }
        return result;
    }

    private record CidHref(int startIndex, int endIndex, String cid, Part cidPart) {}

    private static int indexOf(byte[] array, byte[] smallerArray, int startFrom, int endOnExclusive) {
        for (int i = startFrom; i < endOnExclusive && i < array.length - smallerArray.length + 1; ++i) {
            boolean found = true;
            for (int j = 0; j < smallerArray.length; ++j) {
                if (array[i + j] != smallerArray[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }

    public record Part(String contentId, String contentType, byte[] data, int offset, int length) {

        public InputStream getContentStream() {
            return new ByteArrayInputStream(data, offset, length - 2);
        }

        public byte[] getContentArray() {
            return Arrays.copyOfRange(data, offset, length - 2);
        }
    }
}

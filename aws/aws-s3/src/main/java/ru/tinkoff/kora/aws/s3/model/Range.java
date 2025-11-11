package ru.tinkoff.kora.aws.s3.model;


/**
 * Represents byte range of an object.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-range">rfc9110</a>
 */
public sealed interface Range {
    String toRangeValue();

    /**
     * @param firstPosition An integer indicating the start position of the request range, inclusive
     * @param lastPosition  An integer indicating the end position of the requested range, inclusive
     */
    record FromTo(long firstPosition, long lastPosition) implements Range {
        @Override
        public String toRangeValue() {
            return "bytes=" + firstPosition + "-" + lastPosition;
        }
    }

    /**
     * @param firstPosition An integer indicating the start position of the request range, inclusive
     */
    record StartFrom(long firstPosition) implements ru.tinkoff.kora.aws.s3.model.Range {
        @Override
        public String toRangeValue() {
            return "bytes=" + firstPosition + "-";
        }

    }

    /**
     * @param bytes An integer indicating the number of bytes at the end of the resource, can be larger than resource size
     */
    record LastN(long bytes) implements ru.tinkoff.kora.aws.s3.model.Range {
        @Override
        public String toRangeValue() {
            return "bytes=-" + bytes;
        }
    }
}


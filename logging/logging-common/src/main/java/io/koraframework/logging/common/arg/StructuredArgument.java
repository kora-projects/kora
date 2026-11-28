package io.koraframework.logging.common.arg;

import io.koraframework.json.common.*;
import org.jspecify.annotations.*;
import org.slf4j.*;
import tools.jackson.core.*;

import java.util.*;

public interface StructuredArgument extends StructuredArgumentWriter {

    String fieldName();

    static <T> StructuredArgument arg(String fieldName, @Nullable T value, JsonWriter<T> writer) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, writer);
    }

    static StructuredArgument arg(String fieldName, StructuredArgumentWriter writer) {
        return new ArgumentWithWriter(fieldName, writer);
    }

    static StructuredArgument arg(String fieldName, String value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, StringJsonWriter.INSTANCE);
    }

    static StructuredArgument arg(String fieldName, Integer value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, IntegerJsonWriter.INSTANCE);
    }

    static StructuredArgument arg(String fieldName, Long value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, LongJsonWriter.INSTANCE);
    }

    static StructuredArgument arg(String fieldName, Boolean value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, BooleanJsonWriter.INSTANCE);
    }

    static StructuredArgument arg(String fieldName, Map<String, String> value) {
        return new ArgumentWithValueAndWriter<>(fieldName, value, MapJsonWriter.INSTANCE);
    }

    static <T> Marker marker(String fieldName, @Nullable T value, JsonWriter<T> writer) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, writer);
    }

    static Marker marker(String fieldName, StructuredArgumentWriter writer) {
        return new ArgumentMarkerWithWriter(fieldName, writer);
    }

    static Marker marker(String fieldName, String value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, StringJsonWriter.INSTANCE);
    }

    static Marker marker(String fieldName, Integer value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, IntegerJsonWriter.INSTANCE);
    }

    static Marker marker(String fieldName, Long value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, LongJsonWriter.INSTANCE);
    }

    static Marker marker(String fieldName, Boolean value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, BooleanJsonWriter.INSTANCE);
    }

    static Marker marker(String fieldName, Map<String, String> value) {
        return new ArgumentMarkerWithValueAndWriter<>(fieldName, value, MapJsonWriter.INSTANCE);
    }

    static StructuredArgumentWriter value(StructuredArgumentWriter w) {
        return w;
    }

    final class StringJsonWriter implements JsonWriter<String> {
        public static final StringJsonWriter INSTANCE = new StringJsonWriter();

        private StringJsonWriter() {}

        @Override
        public void write(JsonGenerator gen, String object) {
            try {
                if (object == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(object);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    final class IntegerJsonWriter implements JsonWriter<Integer> {
        public static final IntegerJsonWriter INSTANCE = new IntegerJsonWriter();

        private IntegerJsonWriter() {}

        @Override
        public void write(JsonGenerator gen, Integer object) {
            try {
                if (object == null) {
                    gen.writeNull();
                } else {
                    gen.writeNumber(object.intValue());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    final class LongJsonWriter implements JsonWriter<Long> {
        public static final LongJsonWriter INSTANCE = new LongJsonWriter();

        private LongJsonWriter() {}

        @Override
        public void write(JsonGenerator gen, Long object) {
            try {
                if (object == null) {
                    gen.writeNull();
                } else {
                    gen.writeNumber(object.longValue());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    final class BooleanJsonWriter implements JsonWriter<Boolean> {
        public static final BooleanJsonWriter INSTANCE = new BooleanJsonWriter();

        private BooleanJsonWriter() {}

        @Override
        public void write(JsonGenerator gen, Boolean object) {
            try {
                if (object == null) {
                    gen.writeNull();
                } else {
                    gen.writeBoolean(object.booleanValue());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    final class MapJsonWriter implements JsonWriter<Map<String, String>> {
        public static final MapJsonWriter INSTANCE = new MapJsonWriter();

        private MapJsonWriter() {}

        @Override
        public void write(JsonGenerator gen, Map<String, String> object) {
            try {
                if (object == null) {
                    gen.writeNull();
                    return;
                }
                gen.writeStartObject(object);
                if (object instanceof SequencedMap || object instanceof TreeMap) {
                    for (Map.Entry<String, String> entry : object.entrySet()) {
                        gen.writeName(entry.getKey());
                        gen.writeString(entry.getValue());
                    }
                } else {
                    for (Map.Entry<String, String> entry : object.entrySet()) {
                        gen.writeName(entry.getKey());
                        gen.writeString(entry.getValue());
                    }
                }
                gen.writeEndObject();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

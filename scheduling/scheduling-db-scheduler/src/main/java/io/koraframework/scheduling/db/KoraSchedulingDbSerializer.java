package io.koraframework.scheduling.db;

import com.github.kagkarlsson.scheduler.serializer.Serializer;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;

import java.util.HashMap;
import java.util.Map;

public class KoraSchedulingDbSerializer implements Serializer {

    private static final byte[] VOID_PAYLOAD = new byte[]{0};

    private final Serializer fallback;
    private final Map<Class<?>, SchedulingDbCodec<?>> codecs;

    public KoraSchedulingDbSerializer(All<ValueOf<SchedulingDbCodec<?>>> codecs) {
        this(codecs, Serializer.DEFAULT_JAVA_SERIALIZER);
    }

    public KoraSchedulingDbSerializer(All<ValueOf<SchedulingDbCodec<?>>> codecs, Serializer fallback) {
        this.fallback = fallback;
        var codecsByType = new HashMap<Class<?>, SchedulingDbCodec<?>>();
        for (var codecValue : codecs) {
            var codec = codecValue.get();
            var previous = codecsByType.put(codec.typeRef().getRawType(), codec);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate scheduling-db codec for type: " + codec.typeRef());
            }
        }
        this.codecs = Map.copyOf(codecsByType);
    }

    @Override
    public byte[] serialize(Object data) {
        if (data == null || Void.class == data) {
            return VOID_PAYLOAD;
        }

        var codec = this.codecs.get(data.getClass());
        if (codec == null) {
            return this.fallback.serialize(data);
        }

        return serialize(codec, data);
    }

    @Override
    public <T> T deserialize(Class<T> clazz, byte[] serializedData) {
        if (clazz == Void.class || clazz == Void.TYPE) {
            return null;
        }

        var codec = this.codecs.get(clazz);
        if (codec == null) {
            return this.fallback.deserialize(clazz, serializedData);
        }

        return deserialize(codec, serializedData);
    }

    @SuppressWarnings("unchecked")
    private static <T> byte[] serialize(SchedulingDbCodec<T> codec, Object data) {
        return codec.serialize((T) data);
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(SchedulingDbCodec<?> codec, byte[] serializedData) {
        return ((SchedulingDbCodec<T>) codec).deserialize(serializedData);
    }
}

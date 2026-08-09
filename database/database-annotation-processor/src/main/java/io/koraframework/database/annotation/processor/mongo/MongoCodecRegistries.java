package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Codec registries a repository needs to hand the driver a typed {@code MongoCollection}. One registry is built per
 * codec, in the constructor, so nothing is allocated per call.
 */
final class MongoCodecRegistries {

    private final TypeSpec.Builder type;
    private final MethodSpec.Builder constructor;
    private final Map<String, String> byCodecField = new HashMap<>();

    private int counter;

    MongoCodecRegistries(TypeSpec.Builder type, MethodSpec.Builder constructor) {
        this.type = type;
        this.constructor = constructor;
    }

    String forCodec(String codecField) {
        return this.byCodecField.computeIfAbsent(codecField, field -> {
            var name = "_registry_" + (++this.counter);
            this.type.addField(MongoTypes.CODEC_REGISTRY, name, Modifier.PRIVATE, Modifier.FINAL);
            this.constructor.addStatement("this.$N = $T.fromRegistries($T.fromCodecs(this.$N), $T.getDefaultCodecRegistry())",
                name, MongoTypes.CODEC_REGISTRIES, MongoTypes.CODEC_REGISTRIES, field, MongoTypes.MONGO_CLIENT_SETTINGS);
            return name;
        });
    }
}

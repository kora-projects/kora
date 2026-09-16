package io.koraframework.logging.common.masking;

import io.koraframework.common.annotation.Mapping;

import java.util.Map;

/**
 * Describes JSON field masking rules for values logged through {@code @Mask}.
 * <p>
 * The rule syntax and the matching are those of {@link MaskingPathRules}, this adds the logged type the rules were
 * written for, which is what lets the graph resolve them next to a {@code JsonWriter} of the same type.
 * <p>
 * Annotation processors generate a default {@code MaskingRules<T>} component for types annotated with {@code @Mask}.
 * A custom rules implementation may extend this class and be selected for a logged parameter or result with
 * {@code @Mapping(CustomRules.class)}.
 *
 * @param <T> root type these rules are intended for
 */
public class MaskingRules<T> extends MaskingPathRules implements Mapping.MappingFunction {

    private final Class<T> type;

    public MaskingRules(Class<T> type, Map<String, MaskingStrategy> strategyMap) {
        this(type, rulesOf(strategyMap));
    }

    private MaskingRules(Class<T> type, MaskingPathRules rules) {
        super(rules.fields(), rules.paths());
        this.type = type;
    }

    private static MaskingPathRules rulesOf(Map<String, MaskingStrategy> strategyMap) {
        var builder = MaskingPathRules.builder();
        strategyMap.forEach(builder::mask);
        return builder.build();
    }

    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }

    public Class<T> type() {
        return this.type;
    }

    public static final class Builder<T> extends MaskingPathRules.Builder<Builder<T>> {

        private final Class<T> type;

        private Builder(Class<T> type) {
            this.type = type;
        }

        @Override
        public MaskingRules<T> build() {
            return new MaskingRules<>(this.type, new MaskingPathRules(this.fields, this.paths));
        }
    }
}

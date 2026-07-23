package io.koraframework.logging.common.masking;

import io.koraframework.common.annotation.Mapping;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Describes JSON field masking rules for values logged through {@code @Mask}.
 * <p>
 * A rule can target either a field name or a path from the logged root object:
 * <ul>
 *     <li>{@code password} masks every JSON field named {@code password} wherever it appears;</li>
 *     <li>{@code user.password} masks only {@code password} fields reached through the {@code user} field;</li>
 *     <li>{@code users.*.password} can be used for map-like paths where {@code *} matches one dynamic path segment.</li>
 * </ul>
 * Annotation processors generate a default {@code MaskingRules<T>} component for types annotated with {@code @Mask}.
 * A custom rules implementation may extend this class and be selected for a logged parameter or result with
 * {@code @Mapping(CustomRules.class)}.
 *
 * @param <T> root type these rules are intended for
 */
public class MaskingRules<T> implements Mapping.MappingFunction {
    private final Class<T> type;
    private final List<Rule> fields;
    private final List<Rule> paths;

    public MaskingRules(Class<T> type, Map<String, MaskingStrategy> strategyMap) {
        var builder = builder(type);
        strategyMap.forEach(builder::mask);
        var rules = builder.build();
        this(type, rules.fields, rules.paths);
    }

    private MaskingRules(Class<T> type, List<Rule> fields, List<Rule> paths) {
        this.type = type;
        this.fields = List.copyOf(fields);
        this.paths = List.copyOf(paths);
    }

    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }

    public Class<T> type() {
        return this.type;
    }

    @Nullable
    public MaskingStrategy strategy(List<String> path, String fieldName) {
        for (var rule : this.paths) {
            if (rule.matches(path)) {
                return rule.strategy();
            }
        }
        for (var rule : this.fields) {
            if (rule.matchesField(fieldName)) {
                return rule.strategy();
            }
        }
        return null;
    }

    public static final class Builder<T> {
        private final Class<T> type;
        private final List<Rule> fields = new ArrayList<>();
        private final List<Rule> paths = new ArrayList<>();

        private Builder(Class<T> type) {
            this.type = type;
        }

        /**
         * Adds a masking rule for a JSON field name or a dotted JSON path.
         * <p>
         * A single segment such as {@code password} is matched by field name globally.
         * A multi-segment value such as {@code user.password} is matched from the logged root object.
         * The {@code *} segment is a wildcard that matches exactly one path segment, which is useful for map values
         * whose JSON object field names are not known ahead of time.
         *
         * @param fieldOrPath field name or dotted path
         * @param strategy strategy used to replace matched values
         * @return this builder
         */
        public Builder<T> mask(String fieldOrPath, MaskingStrategy strategy) {
            String[] splitted = fieldOrPath.split("\\.");
            if (splitted.length == 1) {
                this.fields.add(new Rule(List.of(splitted), strategy));
            } else {
                this.paths.add(new Rule(List.of(splitted), strategy));
            }
            return this;
        }

        public MaskingRules<T> build() {
            return new MaskingRules<>(this.type, this.fields, this.paths);
        }
    }

    private record Rule(List<String> path, MaskingStrategy strategy) {

        private boolean matches(List<String> path) {
            if (this.path.size() != path.size()) {
                return false;
            }
            for (int i = 0; i < this.path.size(); i++) {
                var segment = this.path.get(i);
                if (!segment.equals("*") && !segment.equals(path.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private boolean matchesField(String field) {
            return this.path.size() == 1 && this.path.get(0).equals(field);
        }
    }
}

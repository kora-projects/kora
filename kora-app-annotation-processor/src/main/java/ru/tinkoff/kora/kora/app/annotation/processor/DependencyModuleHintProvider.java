package ru.tinkoff.kora.kora.app.annotation.processor;

import com.fasterxml.jackson.core.*;
import com.squareup.javapoet.TypeName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DependencyModuleHintProvider {

    private static final Logger log = LoggerFactory.getLogger(DependencyModuleHintProvider.class);

    private final ProcessingEnvironment processingEnvironment;
    private final List<KoraHint> hints;

    public DependencyModuleHintProvider(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        try (var r = DependencyModuleHintProvider.class.getResourceAsStream("/kora-hints.json");
             var parser = new JsonFactory(new JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_FIELD_NAMES)).createParser(r)) {
            this.hints = KoraHint.parseList(parser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    sealed interface Hint {

        String message();

        record ModuleHint(TypeName type, Set<String> tags, String artifact, String module) implements DependencyModuleHintProvider.Hint {
            public String message() {
                if (tags.isEmpty()) {
                    return """
                        Missing component: %s
                            Component can be provided by standard Kora module you may forgot to plug it:
                                Gradle dependency:  implementation("%s")
                                Module interface:  %s
                        """.formatted(type, artifact, module);
                } else {
                    String tagForMsg;
                    if (this.tags.equals(Set.of("ru.tinkoff.kora.json.common.annotation.Json"))) {
                        tagForMsg = "@ru.tinkoff.kora.json.common.annotation.Json";
                    } else if (this.tags.size() == 1) {
                        tagForMsg = "@Tag(" + this.tags.iterator().next() + ".class)";
                    } else {
                        tagForMsg = this.tags.stream().map(s -> s + ".class")
                            .collect(Collectors.joining(", ", "@Tag({", "})"));
                    }

                    return """
                        Missing component: %s with %s
                            Component is provided by standard Kora module you may forgot to plug it:
                                Gradle dependency:  implementation("%s")
                                Module interface:  %s
                        """.formatted(type, tagForMsg, artifact, module);
                }
            }
        }

        record TipHint(TypeName type, Set<String> tags, String tip) implements Hint {
            public String message() {
                return """
                    %s
                    """.formatted(tip);
            }
        }
    }

    List<Hint> findHints(TypeMirror missingType, Set<String> missingTag) {
        TypeName typeName = TypeName.get(missingType);
        log.trace("Checking hints for {}/{}", missingTag, typeName);
        var result = new ArrayList<Hint>();
        for (var hint : this.hints) {
            var matcher = hint.typeRegex().matcher(typeName.toString());
            if (matcher.matches()) {
                log.trace("Hint {} matched!", hint);
                if (this.tagMatches(missingTag, hint.tags())) {
                    if (hint instanceof KoraHint.KoraModuleHint h) {
                        result.add(new Hint.ModuleHint(typeName, h.tags(), h.artifact(), h.module()));
                    } else if (hint instanceof KoraHint.KoraTipHint t) {
                        result.add(new Hint.TipHint(typeName, t.tags(), t.tip()));
                    } else {
                        throw new UnsupportedOperationException("Unknown hint type: " + hint);
                    }
                }
            } else {
                log.trace("Hint {} doesn't match because of regex", hint);
            }
        }
        return result;
    }

    private boolean tagMatches(Set<String> missingTags, Set<String> hintTags) {
        if (missingTags.isEmpty() && hintTags.isEmpty()) {
            return true;
        }

        if (missingTags.size() != hintTags.size()) {
            return false;
        }

        for (var missingTag : missingTags) {
            if (!hintTags.contains(missingTag)) {
                return false;
            }
        }
        return true;
    }

    sealed interface KoraHint {

        Pattern typeRegex();

        Set<String> tags();

        record KoraModuleHint(Pattern typeRegex, Set<String> tags, String artifact, String module) implements KoraHint {}

        record KoraTipHint(Pattern typeRegex, Set<String> tags, String tip) implements KoraHint {}

        static List<KoraHint> parseList(JsonParser p) throws IOException {
            var token = p.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new JsonParseException(p, "Expecting START_ARRAY token, got " + token);
            }
            token = p.nextToken();
            if (token == JsonToken.END_ARRAY) {
                return List.of();
            }
            var result = new ArrayList<KoraHint>(16);
            while (token != JsonToken.END_ARRAY) {
                var element = parse(p);
                result.add(element);
                token = p.nextToken();
            }
            return result;
        }

        static KoraHint parse(JsonParser p) throws IOException {
            assert p.currentToken() == JsonToken.START_OBJECT;
            var next = p.nextToken();
            String typeRegex = null;
            Set<String> tags = new HashSet<>();
            String moduleName = null;
            String artifact = null;
            String tip = null;
            while (next != JsonToken.END_OBJECT) {
                if (next != JsonToken.FIELD_NAME) {
                    throw new JsonParseException(p, "expected FIELD_NAME, got " + next);
                }
                var name = p.currentName();
                switch (name) {
                    case "tags" -> {
                        if (p.nextToken() != JsonToken.START_ARRAY) {
                            throw new JsonParseException(p, "expected START_ARRAY, got " + next);
                        }
                        next = p.nextToken();
                        while (next != JsonToken.END_ARRAY) {
                            if (next != JsonToken.VALUE_STRING) {
                                throw new JsonParseException(p, "expected VALUE_STRING, got " + next);
                            }
                            tags.add(p.getValueAsString());
                            next = p.nextToken();
                        }
                    }
                    case "typeRegex" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new JsonParseException(p, "expected VALUE_STRING, got " + next);
                        }
                        typeRegex = p.getValueAsString();
                    }
                    case "moduleName" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new JsonParseException(p, "expected VALUE_STRING, got " + next);
                        }
                        moduleName = p.getValueAsString();
                    }
                    case "artifact" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new JsonParseException(p, "expected VALUE_STRING, got " + next);
                        }
                        artifact = p.getValueAsString();
                    }
                    case "tip" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new JsonParseException(p, "expected VALUE_STRING, got " + next);
                        }
                        tip = p.getValueAsString();
                    }
                    default -> {
                        p.nextToken();
                        p.skipChildren();
                    }
                }
                next = p.nextToken();
            }

            if (typeRegex == null) {
                throw new JsonParseException(p, "Some required fields missing: typeRegex=%s".formatted(typeRegex));
            } else if (!(moduleName != null && artifact != null || tip != null)) {
                throw new JsonParseException(p, "Some required fields missing: module=%s, artifact=%s, tip=%s".formatted(
                    moduleName, artifact, tip));
            }

            if (tip != null) {
                return new KoraHint.KoraTipHint(Pattern.compile(typeRegex), tags, tip);
            } else {
                return new KoraHint.KoraModuleHint(Pattern.compile(typeRegex), tags, moduleName, artifact);
            }
        }
    }
}

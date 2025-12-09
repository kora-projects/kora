package ru.tinkoff.kora.kora.app.annotation.processor;

import com.palantir.javapoet.TypeName;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.json.JsonFactoryBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class DependencyModuleHintProvider {

    private static final Logger log = LoggerFactory.getLogger(DependencyModuleHintProvider.class);

    private final List<KoraHint> hints;

    public DependencyModuleHintProvider(ProcessingEnvironment processingEnvironment) {
        var hints = List.<KoraHint>of();
        try (var r = DependencyModuleHintProvider.class.getResourceAsStream("/kora-hints.json");
             var parser = new JsonFactoryBuilder().build().createParser(r)) {
            hints = KoraHint.parseList(parser);
        } catch (IOException e) {}
        this.hints = hints;
    }


    public sealed interface Hint {

        String message();

        record ModuleHint(TypeName type, @Nullable String tag, String artifact, String module) implements DependencyModuleHintProvider.Hint {
            public String message() {
                if (tag == null) {
                    return """
                        Missing component: %s
                            Component is provided by standard Kora module you may forgot to plug it:
                                Gradle dependency:  implementation("%s")
                                Module interface:  %s
                        """.formatted(type, artifact, module);
                } else {
                    String tagForMsg;
                    if (tag.equals("ru.tinkoff.kora.json.common.annotation.Json")) {
                        tagForMsg = "@ru.tinkoff.kora.json.common.annotation.Json";
                    } else  {
                        tagForMsg = "@Tag(" + tag + ".class)";
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

        record TipHint(TypeName type, @Nullable String tag, String tip) implements Hint {
            public String message() {
                return """
                    %s
                    """.formatted(tip);
            }
        }
    }

    List<Hint> findHints(TypeMirror missingType, @Nullable String missingTag) {
        var typeName = TypeName.get(missingType);
        log.trace("Checking hints for {}/{}", missingTag, typeName);
        var result = new ArrayList<Hint>();
        for (var hint : this.hints) {
            var matcher = hint.typeRegex().matcher(typeName.toString());
            if (matcher.matches()) {
                log.trace("Hint {} matched!", hint);
                if (this.tagMatches(missingTag, hint.tag())) {
                    if (hint instanceof KoraHint.KoraModuleHint h) {
                        result.add(new Hint.ModuleHint(typeName, h.tag(), h.artifact(), h.module()));
                    } else if (hint instanceof KoraHint.KoraTipHint t) {
                        result.add(new Hint.TipHint(typeName, t.tag(), t.tip()));
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

    private boolean tagMatches(@Nullable String missingTags, @Nullable String hintTags) {
        if (missingTags == null && hintTags == null) {
            return true;
        }
        if (missingTags == null) {
            return false;
        }
        return missingTags.equals(hintTags);
    }

    sealed interface KoraHint {

        Pattern typeRegex();

        @Nullable
        String tag();

        record KoraModuleHint(Pattern typeRegex, @Nullable String tag, String artifact, String module) implements KoraHint {}

        record KoraTipHint(Pattern typeRegex, @Nullable String tag, String tip) implements KoraHint {}

        static List<KoraHint> parseList(JsonParser p) throws IOException {
            var token = p.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new StreamReadException(p, "Expecting START_ARRAY token, got " + token);
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
            String tag = null;
            while (next != JsonToken.END_OBJECT) {
                if (next != JsonToken.PROPERTY_NAME) {
                    throw new StreamReadException(p, "expected PROPERTY_NAME, got " + next);
                }
                var name = p.currentName();
                switch (name) {
                    case "tags" -> {
                        if (p.nextToken() != JsonToken.START_ARRAY) {
                            throw new StreamReadException(p, "expected START_ARRAY, got " + next);
                        }
                        next = p.nextToken();
                        while (next != JsonToken.END_ARRAY) {
                            if (next != JsonToken.VALUE_STRING) {
                                throw new StreamReadException(p, "expected VALUE_STRING, got " + next);
                            }
                            tags.add(p.getValueAsString());
                            next = p.nextToken();
                        }
                        if (!tags.isEmpty() && tags.size() != 1) {
                            throw new IllegalArgumentException("More than one tag found in hint: " + tags);
                        }
                    }
                    case "tag" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new StreamReadException(p, "expected VALUE_STRING, got " + next, p.currentLocation());
                        }
                        tag = p.getValueAsString();
                    }
                    case "typeRegex" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new StreamReadException(p, "expected VALUE_STRING, got " + next);
                        }
                        typeRegex = p.getValueAsString();
                    }
                    case "moduleName" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new StreamReadException(p, "expected VALUE_STRING, got " + next);
                        }
                        moduleName = p.getValueAsString();
                    }
                    case "artifact" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new StreamReadException(p, "expected VALUE_STRING, got " + next);
                        }
                        artifact = p.getValueAsString();
                    }
                    case "tip" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new StreamReadException(p, "expected VALUE_STRING, got " + next);
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
                throw new StreamReadException(p, "Some required fields missing: typeRegex=%s".formatted(typeRegex));
            } else if (!(moduleName != null && artifact != null || tip != null)) {
                throw new StreamReadException(p, "Some required fields missing: module=%s, artifact=%s, tip=%s".formatted(
                    moduleName, artifact, tip));
            }
            var finalTag = tag;
            if (finalTag == null && !tags.isEmpty()) {
                finalTag = tags.iterator().next();
            }

            if (tip != null) {
                return new KoraHint.KoraTipHint(Pattern.compile(typeRegex), finalTag, tip);
            } else {
                return new KoraHint.KoraModuleHint(Pattern.compile(typeRegex), finalTag, moduleName, artifact);
            }
        }
    }
}

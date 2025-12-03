package ru.tinkoff.kora.kora.app.annotation.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;

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
    private final List<ModuleHint> hints;

    public DependencyModuleHintProvider(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        List<ModuleHint> hints;
        try (var r = DependencyModuleHintProvider.class.getResourceAsStream("/kora-modules.json");
             var parser = new JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_PROPERTY_NAMES).build().createParser(ObjectReadContext.empty(), r)) {
            hints = ModuleHint.parseList(parser);
        } catch (IOException e) {
            log.warn("Error while reading hint list", e);
            hints = List.of();
        }
        this.hints = hints;
    }


    sealed interface Hint {

        String message();

        record SimpleHint(TypeMirror type, String artifact, String module) implements DependencyModuleHintProvider.Hint {
            public String message() {
                return "Missing component of type %s which can be provided by kora module, you may forgot to plug it\nModule class: %s \nArtifact dependency: %s\n".formatted(
                    type, module, artifact
                );
            }
        }

        record HintWithTag(TypeMirror type, String artifact, String module, Set<String> tags) implements DependencyModuleHintProvider.Hint {
            public String message() {
                return "Missing component of type %s which can be provided by kora module, you may forgot to plug it\nModule class: %s (required tags: `@Tag(%s)`)\nArtifact dependency: %s\n".formatted(
                    type, module, tags.stream().map(s -> s + ".class").collect(Collectors.joining(", ", "{", "}")), artifact
                );
            }
        }
    }

    List<Hint> findHints(TypeMirror missingType, Set<String> missingTag) {
        log.trace("Checking hints for {}/{}", missingTag, missingType);
        var result = new ArrayList<Hint>();
        for (var hint : this.hints) {
            var matcher = hint.typeRegex.matcher(missingType.toString());
            if (matcher.matches()) {
                log.trace("Hint {} matched!", hint);
                if (this.tagMatches(missingTag, hint.tags())) {
                    if (hint.tags.isEmpty()) {
                        result.add(new Hint.SimpleHint(missingType, hint.artifact, hint.moduleName));
                    } else {
                        result.add(new Hint.HintWithTag(missingType, hint.artifact, hint.moduleName, hint.tags));
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

    record ModuleHint(Set<String> tags, Pattern typeRegex, String moduleName, String artifact) {
        static List<ModuleHint> parseList(JsonParser p) throws IOException {
            var token = p.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new StreamReadException(p, "Expecting START_ARRAY token, got " + token, p.currentLocation());
            }
            token = p.nextToken();
            if (token == JsonToken.END_ARRAY) {
                return List.of();
            }
            var result = new ArrayList<ModuleHint>(16);
            while (token != JsonToken.END_ARRAY) {
                var element = parse(p);
                result.add(element);
                token = p.nextToken();
            }
            return result;
        }

        static ModuleHint parse(JsonParser p) throws IOException {
            assert p.currentToken() == JsonToken.START_OBJECT;
            var next = p.nextToken();
            String typeRegex = null;
            Set<String> tags = new HashSet<>();
            String moduleName = null;
            String artifact = null;
            while (next != JsonToken.END_OBJECT) {
                if (next != JsonToken.PROPERTY_NAME) {
                    throw new StreamReadException(p, "expected PROPERTY_NAME, got " + next, p.currentLocation());
                }
                var name = p.currentName();
                switch (name) {
                    case "tags" -> {
                        if (p.nextToken() != JsonToken.START_ARRAY) {
                            throw new StreamReadException(p, "expected START_ARRAY, got " + next, p.currentLocation());
                        }
                        next = p.nextToken();
                        while (next != JsonToken.END_ARRAY) {
                            if (next != JsonToken.VALUE_STRING) {
                                throw new StreamReadException(p, "expected VALUE_STRING, got " + next, p.currentLocation());
                            }
                            tags.add(p.getValueAsString());
                            next = p.nextToken();
                        }
                    }
                    case "typeRegex" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new StreamReadException(p, "expected VALUE_STRING, got " + next, p.currentLocation());
                        }
                        typeRegex = p.getValueAsString();
                    }
                    case "moduleName" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new StreamReadException(p, "expected VALUE_STRING, got " + next, p.currentLocation());
                        }
                        moduleName = p.getValueAsString();
                    }
                    case "artifact" -> {
                        if (p.nextToken() != JsonToken.VALUE_STRING) {
                            throw new StreamReadException(p, "expected VALUE_STRING, got " + next, p.currentLocation());
                        }
                        artifact = p.getValueAsString();
                    }
                    default -> {
                        p.nextToken();
                        p.skipChildren();
                    }
                }
                next = p.nextToken();
            }
            if (typeRegex == null || moduleName == null || artifact == null) {
                throw new StreamReadException(p, "Some required fields missing", p.currentLocation());
            }
            return new ModuleHint(tags, Pattern.compile(typeRegex), moduleName, artifact);
        }
    }
}

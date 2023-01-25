package ru.tinkoff.kora.kora.app.annotation.processor;

import com.fasterxml.jackson.core.*;
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
    private final List<ModuleHint> hints;

    public DependencyModuleHintProvider(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        try (var r = DependencyModuleHintProvider.class.getResourceAsStream("/kora-modules.json");
             var parser = new JsonFactory(new JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_FIELD_NAMES)).createParser(r)) {
            this.hints = ModuleHint.parseList(parser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    sealed interface Hint {
        String message();

        record SimpleHint(TypeMirror type, String artifact, String module) implements DependencyModuleHintProvider.Hint {
            public String message() {
                return "Missing component of type %s can be provided by module %s from artifact %s".formatted(
                    type, module, artifact
                );
            }
        }

        record HintWithTag(TypeMirror type, String artifact, String module, Set<String> tags) implements DependencyModuleHintProvider.Hint {
            public String message() {
                return "Missing component of type %s can be provided by module %s from artifact %s (required tags: `@Tag(%s)`)".formatted(
                    type, module, artifact, tags.stream().map(s -> s + ".class").collect(Collectors.joining(", ", "{", "}"))
                );
            }
        }

    }

    public List<Hint> findHints(TypeMirror missingType, Set<String> missingTag) {
        log.trace("Checking hints for {}/{}", missingTag, missingType);
        var result = new ArrayList<Hint>();
        for (var hint : this.hints) {
            if (!this.tagMatches(missingTag, hint.tags())) {
                log.trace("Hint {} doesn't match because of tag", hint);
                continue;
            }
            var matcher = hint.typeRegex.matcher(missingType.toString());
            if (matcher.matches()) {
                log.trace("Hint {} matched!", hint);
                if (this.tagMatches(missingTag, hint.tags())) {
                    result.add(new Hint.SimpleHint(missingType, hint.artifact, hint.moduleName));
                } else {
                    result.add(new Hint.HintWithTag(missingType, hint.artifact, hint.moduleName, hint.tags));
                }
            }
            log.trace("Hint {} doesn't match because of regex", hint);
        }
        return result;
    }

    private boolean tagMatches(Set<String> missingTag, Set<String> tags) {
        if (missingTag.isEmpty() && tags.isEmpty()) {
            return true;
        }
        if (missingTag.size() >= tags.size()) {
            return false;
        }
        for (var tag : missingTag) {
            if (!tags.contains(tag)) {
                return false;
            }
        }
        return true;
    }

    record ModuleHint(Set<String> tags, Pattern typeRegex, String moduleName, String artifact) {
        static List<ModuleHint> parseList(JsonParser p) throws IOException {
            var token = p.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new JsonParseException(p, "Expecting START_ARRAY token, got " + token);
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
                    default -> {
                        p.nextToken();
                        p.skipChildren();
                    }
                }
                next = p.nextToken();
            }
            if (typeRegex == null || moduleName == null || artifact == null) {
                throw new JsonParseException(p, "Some required fields missing");
            }
            return new ModuleHint(tags, Pattern.compile(typeRegex), moduleName, artifact);
        }
    }
}

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
    private final List<ModuleHint> hints;
    private final List<ManualTip> tips;

    public DependencyModuleHintProvider(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        try (var r = DependencyModuleHintProvider.class.getResourceAsStream("/kora-modules.json");
             var parser = new JsonFactory(new JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_FIELD_NAMES)).createParser(r)) {
            this.hints = ModuleHint.parseList(parser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (var r = DependencyModuleHintProvider.class.getResourceAsStream("/kora-tips.json");
             var parser = new JsonFactory(new JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_FIELD_NAMES)).createParser(r)) {
            this.tips = ManualTip.parseList(parser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    sealed interface Hint {

        String message();

        record SimpleHint(TypeName type, String artifact, String module) implements DependencyModuleHintProvider.Hint {
            public String message() {
                return """
                    Missing component: %s
                        Component can be provided by standard Kora module you may forgot to plug it:
                            Gradle dependency:  implementation("%s")
                            Module interface:  %s
                    """.formatted(type, artifact, module);
            }
        }

        record HintWithTag(TypeName type, String artifact, String module, Set<String> tags) implements DependencyModuleHintProvider.Hint {
            public String message() {
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

    sealed interface Tip {

        String message();

        record TipNoTag(String tip, TypeName type) implements DependencyModuleHintProvider.Tip {
            public String message() {
                return """
                    %s
                    """.formatted(tip);
            }
        }

        record TipWithTag(String tip, TypeName type, Set<String> tags) implements DependencyModuleHintProvider.Tip {
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
            var matcher = hint.typeRegex.matcher(typeName.toString());
            if (matcher.matches()) {
                log.trace("Hint {} matched!", hint);
                if (this.tagMatches(missingTag, hint.tags())) {
                    if (hint.tags.isEmpty()) {
                        result.add(new Hint.SimpleHint(typeName, hint.artifact, hint.moduleName));
                    } else {
                        result.add(new Hint.HintWithTag(typeName, hint.artifact, hint.moduleName, hint.tags));
                    }
                }
            } else {
                log.trace("Hint {} doesn't match because of regex", hint);
            }
        }
        return result;
    }

    List<Tip> findTips(TypeMirror missingType, Set<String> missingTag) {
        TypeName typeName = TypeName.get(missingType);
        log.trace("Checking tips for {}/{}", missingTag, typeName);
        var result = new ArrayList<Tip>();
        for (var tip : this.tips) {
            var matcher = tip.typeRegex.matcher(typeName.toString());
            if (matcher.matches()) {
                log.trace("Tip {} matched!", tip);
                if (this.tagMatches(missingTag, tip.tags())) {
                    if (tip.tags.isEmpty()) {
                        result.add(new Tip.TipNoTag(tip.tip, typeName));
                    } else {
                        result.add(new Tip.TipWithTag(tip.tip, typeName, tip.tags));
                    }
                }
            } else {
                log.trace("Tip {} doesn't match because of regex", tip);
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

    record ManualTip(Set<String> tags, Pattern typeRegex, String tip) {
        static List<ManualTip> parseList(JsonParser p) throws IOException {
            var token = p.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new JsonParseException(p, "Expecting START_ARRAY token, got " + token);
            }
            token = p.nextToken();
            if (token == JsonToken.END_ARRAY) {
                return List.of();
            }
            var result = new ArrayList<ManualTip>(16);
            while (token != JsonToken.END_ARRAY) {
                var element = parse(p);
                result.add(element);
                token = p.nextToken();
            }
            return result;
        }

        static ManualTip parse(JsonParser p) throws IOException {
            assert p.currentToken() == JsonToken.START_OBJECT;
            var next = p.nextToken();
            String typeRegex = null;
            Set<String> tags = new HashSet<>();
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

            if (typeRegex == null || tip == null) {
                throw new JsonParseException(p, "Some required fields missing: typeRegex=%s, tip=%s".formatted(typeRegex, tip));
            }
            return new ManualTip(tags, Pattern.compile(typeRegex), tip);
        }
    }
}

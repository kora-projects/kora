package ru.tinkoff.kora.kora.app.annotation.processor.extension;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public record Extensions(List<KoraExtension> extensions) {
    private static final Logger log = LoggerFactory.getLogger(Extensions.class);

    public static Extensions load(ClassLoader classLoader, ProcessingEnvironment processingEnvironment) {
        var serviceLoader = ServiceLoader.load(ExtensionFactory.class, classLoader);
        var extensions = serviceLoader.stream()
            .map(ServiceLoader.Provider::get)
            .flatMap(f -> f.create(processingEnvironment).stream())
            .toList();

        if (!extensions.isEmpty() && log.isInfoEnabled()) {
            String out = extensions.stream()
                .map(e -> e.getClass().getCanonicalName())
                .collect(Collectors.joining("\n"))
                .indent(4);

            log.info("Extensions found:\n{}", out);
        }

        return new Extensions(extensions);
    }


    @Nullable
    public KoraExtension.KoraExtensionDependencyGenerator findExtension(RoundEnvironment roundEnvironment, TypeMirror typeMirror, @Nullable String tag) {
        var extensions = new ArrayList<KoraExtension.KoraExtensionDependencyGenerator>();
        for (var extension : this.extensions) {
            var generator = extension.getDependencyGenerator(roundEnvironment, typeMirror, tag);
            if (generator != null) {
                log.trace("Extension '{}' is suitable generating for type: {}", extension.getClass().getCanonicalName(), typeMirror);
                extensions.add(generator);
            }
        }
        if (extensions.isEmpty()) {
            return null;
        }
        if (extensions.size() > 1) {
            // todo print warning
        }
        return extensions.get(0);
    }


}

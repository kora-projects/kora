package ru.tinkoff.kora.kora.app.annotation.processor.extension;

import javax.annotation.Nullable;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;

public interface KoraExtension {
    @Nullable
    KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror);

    interface KoraExtensionDependencyGenerator {
        ExtensionResult generateDependency() throws IOException;
    }
}

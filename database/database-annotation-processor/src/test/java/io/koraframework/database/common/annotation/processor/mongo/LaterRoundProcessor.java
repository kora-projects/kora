package io.koraframework.database.common.annotation.processor.mongo;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

/**
 * Generates a source file in the first round, like another processor whose type a Mongo entity or repository refers to.
 */
final class LaterRoundProcessor extends AbstractProcessor {

    private final String packageName;
    private final String className;
    private final String body;
    private boolean done;

    LaterRoundProcessor(String packageName, String className, String body) {
        this.packageName = packageName;
        this.className = className;
        this.body = body;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (this.done) {
            return false;
        }
        this.done = true;
        try (var writer = this.processingEnv.getFiler().createSourceFile(this.packageName + "." + this.className).openWriter()) {
            writer.write("package " + this.packageName + ";\n" + this.body + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }
}

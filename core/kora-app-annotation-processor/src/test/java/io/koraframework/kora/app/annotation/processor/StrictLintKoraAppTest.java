package io.koraframework.kora.app.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractStrictLintTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

public class StrictLintKoraAppTest extends AbstractStrictLintTest {

    @Test
    public void minimalKoraAppUnderWerror() throws Exception {
        assertLintClean(compileStrict(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default String root() { return "root"; }
            }
            """));
    }

    @Test
    public void emptyKoraSubmoduleUnderWerror() throws Exception {
        assertLintClean(compileStrict(List.of(new KoraSubmoduleProcessor()), """
            @KoraSubmodule
            public interface LibModule {}
            """));
    }

    @Test
    public void koraAppWithSubmoduleOfSameCompilationUnderWerror() throws Exception {
        assertLintClean(compileStrict(List.of(new KoraAppProcessor(), new KoraSubmoduleProcessor()), """
            @KoraSubmodule
            public interface LibModule {
                default Integer value() { return 1; }
            }
            """, """
            @KoraApp
            public interface ExampleApplication extends LibModule {
                @Root
                default String root(Integer value) { return "root" + value; }
            }
            """));
    }

    @Test
    public void submoduleIsWaitedForWhenAnotherProcessorDelaysTheRound() throws Exception {
        // round 1 carries a non-DI annotation, round 2 has only a generated source without annotations:
        // the graph must still wait for LibModuleSubmoduleImpl, which is generated in round 2
        assertLintClean(compileStrict(List.of(new KoraAppProcessor(), new KoraSubmoduleProcessor(), new GeneratingProcessor(testPackage(), 1, "GeneratedType", """
                public final class GeneratedType {}
                """)), """
            public @interface Marker {}
            """, """
            @Marker
            @KoraSubmodule
            public interface LibModule {}
            """, """
            @KoraApp
            public interface ExampleApplication extends LibModule {
                @Root
                default String root() { return "root"; }
            }
            """));
    }

    @Test
    public void componentGeneratedAfterGraphIsReported() throws Exception {
        var result = compileStrict(List.of(new KoraAppProcessor(), new GeneratingProcessor(testPackage(), 2, "LateComponent", """
            @Component
            public final class LateComponent {}
            """)), """
            @KoraApp
            public interface ExampleApplication {
                @Root
                default String root() { return "root"; }
            }
            """);
        Assertions.assertThat(result.errors())
            .anyMatch(e -> e.contains("Component or module was generated after Kora had already written the application graph") && e.contains("LateComponent"));
    }

    /**
     * Stands for a third-party processor: writes one source in the given round.
     */
    static final class GeneratingProcessor extends AbstractProcessor {
        private final String pkg;
        private final int round;
        private final String name;
        private final String body;
        private int current = 0;

        GeneratingProcessor(String pkg, int round, String name, String body) {
            this.pkg = pkg;
            this.round = round;
            this.name = name;
            this.body = body;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (++current != round) {
                return false;
            }
            try (var w = processingEnv.getFiler().createSourceFile(pkg + "." + name).openWriter()) {
                w.write("package " + pkg + ";\nimport io.koraframework.common.annotation.*;\n" + body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return false;
        }
    }
}

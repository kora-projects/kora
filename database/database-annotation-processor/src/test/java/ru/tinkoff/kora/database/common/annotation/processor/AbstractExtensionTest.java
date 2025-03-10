package ru.tinkoff.kora.database.common.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.RefreshableGraph;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import javax.annotation.processing.Processor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class AbstractExtensionTest extends AbstractAnnotationProcessorTest {

    protected ApplicationGraphDraw draw;
    protected RefreshableGraph graph;

    @Override
    protected String commonImports() {
        return """
            import ru.tinkoff.kora.common.KoraApp;
            import ru.tinkoff.kora.common.annotation.Root;
            import jakarta.annotation.Nullable;

            """;
    }

    protected ClassName className(String simpleName, String... names) {
        return ClassName.get(this.testPackage(), simpleName, names);
    }


    protected RefreshableGraph compile(TypeName expectedGeneratedType, List<TypeName> requiredMocks, @Language("java") String... additionalSources) {
        return compile(expectedGeneratedType, requiredMocks, List.of(), additionalSources);
    }

    protected RefreshableGraph compile(TypeName expectedGeneratedType, List<TypeName> requiredMocks, List<Processor> additionalProcessors, @Language("java") String... additionalSources) {
        var sources = Arrays.copyOf(additionalSources, additionalSources.length + 1);
        var app = new StringBuilder()
            .append("@KoraApp\n")
            .append("public interface ExampleApplication {\n")
            .append("  @Root\n")
            .append("  default ExampleApplication root(").append(expectedGeneratedType.toString()).append(" fromExtension) { return org.mockito.Mockito.mock(ExampleApplication.class); }\n");
        var mocks = 0;
        for (var requiredMock : requiredMocks) {
            app.append("  default ").append(requiredMock.toString()).append(" mock").append(mocks++).append("() { org.mockito.Mockito.mock(");
            if (requiredMock instanceof ParameterizedTypeName ptn) {
                app.append(ptn.rawType());
            } else {
                app.append(requiredMock);
            }
        }
        app.append("}\n");
        sources[additionalSources.length] = app.toString();

        var processors = new ArrayList<>(additionalProcessors);
        processors.add(new KoraAppProcessor());
        var compileResult = compile(processors, sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }

        try {
            var appClass = compileResult.loadClass("ExampleApplicationGraph");
            var object = (Supplier<ApplicationGraphDraw>) appClass.getConstructor().newInstance();
            this.draw = object.get();
            this.graph = this.draw.init();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return this.graph;
    }

}

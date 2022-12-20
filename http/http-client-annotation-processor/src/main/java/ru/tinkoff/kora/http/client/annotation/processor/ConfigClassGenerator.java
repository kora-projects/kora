package ru.tinkoff.kora.http.client.annotation.processor;

import ru.tinkoff.kora.http.client.common.declarative.DeclarativeHttpClientConfig;
import ru.tinkoff.kora.http.client.common.declarative.HttpClientOperationConfig;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.time.Duration;
import java.util.Objects;

public class ConfigClassGenerator {
    private final ProcessingEnvironment processingEnv;

    public ConfigClassGenerator(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public record ConfigClass(String name, String content) {}

    public ConfigClass generate(TypeElement element) {
        var methods = element.getEnclosedElements().stream()
            .filter(m -> m.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(m -> !m.getModifiers().contains(Modifier.STATIC))
            .filter(m -> !m.getModifiers().contains(Modifier.DEFAULT))
            .map(ExecutableElement::getSimpleName)
            .map(Objects::toString)
            .toList();

        var packageName = this.processingEnv.getElementUtils().getPackageOf(element);
        var typeName = HttpClientUtils.configName(element);
        var type = """
            package %s;
                        
            import %s;
            import %s;
            import %s;
            import %s;
                        
            public record %s(String url, @Nullable Integer requestTimeout,
            """.formatted(packageName.getQualifiedName(),
            Nullable.class.getCanonicalName(), HttpClientOperationConfig.class.getCanonicalName(),
            DeclarativeHttpClientConfig.class.getCanonicalName(), Duration.class.getCanonicalName(),
            typeName
        );
        var b = new StringBuilder(type);
        for (var iterator = methods.iterator(); iterator.hasNext(); ) {
            var method = iterator.next();
            b.append("  @Nullable ").append(HttpClientOperationConfig.class.getSimpleName()).append(" ").append(method).append("Config");
            if (iterator.hasNext()) {
                b.append(',');
            }
            b.append('\n');
        }
        b.append(") implements ").append(DeclarativeHttpClientConfig.class.getName()).append(" {\n");

        b.append("\tpublic ").append(typeName).append("(String url, @Nullable Duration requestTimeout");
        for (String method : methods) {
            b.append(',').append(" ");
            b.append("@Nullable ").append(HttpClientOperationConfig.class.getSimpleName()).append(" ").append(method).append("Config");
        }

        b.append(") {\n")
            .append("\t\tthis(url, (requestTimeout == null) ? null : ((Long) requestTimeout.toMillis()).intValue()");

        for (String method : methods) {
            b.append(',').append(" ").append(method).append("Config");
        }

        b.append(");").append("\n\t}\n").append("}");
        return new ConfigClass(typeName, b.toString());
    }
}

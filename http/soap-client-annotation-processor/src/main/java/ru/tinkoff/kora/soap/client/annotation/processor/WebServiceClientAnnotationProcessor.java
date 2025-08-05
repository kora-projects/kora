package ru.tinkoff.kora.soap.client.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WebServiceClientAnnotationProcessor extends AbstractKoraProcessor {
    private SoapClientImplGenerator generator;
    private static final ClassName JAKARTA_WEB_SERVICE = new SoapClasses.JakartaClasses().webServiceType();
    private static final ClassName JAVAX_WEB_SERVICE = new SoapClasses.JavaxClasses().webServiceType();

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(
            JAKARTA_WEB_SERVICE,
            JAVAX_WEB_SERVICE
        );
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.generator = new SoapClientImplGenerator(processingEnv);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotated : annotatedElements.getOrDefault(JAKARTA_WEB_SERVICE, List.of())) {
            var jakartaClasses = new SoapClasses.JakartaClasses();
            var service = annotated.element();
            this.processService(service, jakartaClasses);
        }
        for (var annotated : annotatedElements.getOrDefault(JAVAX_WEB_SERVICE, List.of())) {
            var javaxClasses = new SoapClasses.JavaxClasses();
            var service = annotated.element();
            this.processService(service, javaxClasses);
        }
    }

    private void processService(Element service, SoapClasses soapClasses) {
        var typeSpec = this.generator.generate(service, soapClasses);
        var typeJavaFile = JavaFile.builder(this.elements.getPackageOf(service).getQualifiedName().toString(), typeSpec)
            .build();

        var moduleSpec = this.generator.generateModule(service, soapClasses);
        var moduleJavaFile = JavaFile.builder(this.elements.getPackageOf(service).getQualifiedName().toString(), moduleSpec)
            .build();

        CommonUtils.safeWriteTo(this.processingEnv, typeJavaFile);
        CommonUtils.safeWriteTo(this.processingEnv, moduleJavaFile);
    }
}

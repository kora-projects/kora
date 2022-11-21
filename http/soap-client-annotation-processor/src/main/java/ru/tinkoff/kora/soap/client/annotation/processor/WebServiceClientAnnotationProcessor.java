package ru.tinkoff.kora.soap.client.annotation.processor;

import com.squareup.javapoet.JavaFile;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.Set;

public class WebServiceClientAnnotationProcessor extends AbstractKoraProcessor {
    private SoapClientImplGenerator generator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
            "jakarta.jws.WebService",
            "javax.jws.WebService"
        );
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.generator = new SoapClientImplGenerator(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var jakartaWebService = this.elements.getTypeElement("jakarta.jws.WebService");
        var javaxWebService = this.elements.getTypeElement("javax.jws.WebService");
        if (jakartaWebService != null) {
            var jakartaClasses = new SoapClasses.JakartaClasses(this.types, this.elements);
            var webServices = roundEnv.getElementsAnnotatedWith(jakartaWebService);
            for (var service : webServices) {
                try {
                    this.processService(service, jakartaClasses);
                } catch (IOException e) {
                    throw new RuntimeException(e);// todo
                }
            }
        }
        if (javaxWebService != null) {
            var javaxClasses = new SoapClasses.JavaxClasses(this.types, this.elements);
            var webServices = roundEnv.getElementsAnnotatedWith(javaxWebService);
            for (var service : webServices) {
                try {
                    this.processService(service, javaxClasses);
                } catch (IOException e) {
                    throw new RuntimeException(e);// todo
                }
            }
        }
        return false;
    }

    private void processService(Element service, SoapClasses soapClasses) throws IOException {
        var typeSpec = this.generator.generate(service, soapClasses);

        var javaFile = JavaFile.builder(this.elements.getPackageOf(service).getQualifiedName().toString(), typeSpec)
            .build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }
}

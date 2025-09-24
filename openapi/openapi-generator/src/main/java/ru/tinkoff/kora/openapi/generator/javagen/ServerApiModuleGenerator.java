package ru.tinkoff.kora.openapi.generator.javagen;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import org.openapitools.codegen.model.OperationsMap;
import ru.tinkoff.kora.openapi.generator.DelegateMethodBodyMode;

import javax.lang.model.element.Modifier;

public class ServerApiModuleGenerator extends AbstractJavaGenerator<OperationsMap> {
    @Override
    public JavaFile generate(OperationsMap ctx) {
        var b = TypeSpec.interfaceBuilder(ctx.get("classname") + "Module")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated())
            .addAnnotation(Classes.module);
        if (params.delegateMethodBodyMode != DelegateMethodBodyMode.NONE) {
            var delegateClass = ClassName.get(apiPackage, ctx.get("classname") + "Delegate");
            b.addMethod(MethodSpec.methodBuilder("default" + ctx.get("classname") + "Delegate")
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addStatement("return new $T() {}", delegateClass)
                .returns(delegateClass)
                .build());
        }

        return JavaFile.builder(apiPackage, b.build()).build();

    }
}

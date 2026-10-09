package io.koraframework.http.client.annotation.processor;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.NameUtils;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

public class HttpClientUtils {
    /**
     * Client implementation and config are generated as nested types of the client module,
     * e.g. <code>$MyClient_Module.Impl</code> and <code>$MyClient_Module.Config</code>
     */
    public static final String MODULE_POSTFIX = "Module";
    public static final String CLIENT_NAME = "Impl";
    public static final String CONFIG_NAME = "Config";

    /**
     * @return name of the top level client class that was generated before client was moved into the module
     */
    public static String clientName(TypeElement httpClientType) {
        return NameUtils.generatedType(httpClientType, "ClientImpl");
    }

    public static String moduleName(TypeElement httpClientType) {
        return NameUtils.generatedType(httpClientType, MODULE_POSTFIX);
    }

    public static ClassName moduleClassName(Elements elements, TypeElement httpClientType) {
        var packageName = elements.getPackageOf(httpClientType).getQualifiedName().toString();
        return ClassName.get(packageName, moduleName(httpClientType));
    }

    public static ClassName clientClassName(Elements elements, TypeElement httpClientType) {
        return moduleClassName(elements, httpClientType).nestedClass(CLIENT_NAME);
    }

    public static ClassName configClassName(Elements elements, TypeElement httpClientType) {
        return moduleClassName(elements, httpClientType).nestedClass(CONFIG_NAME);
    }
}

package io.koraframework.test.extension.junit5;

import org.junit.jupiter.api.extension.ExtensionConfigurationException;

final class TestExtensionErrors {

    private TestExtensionErrors() {}

    static ExtensionConfigurationException componentNotFound(String operation, GraphCandidate candidate) {
        return new ExtensionConfigurationException("""
            Cannot %s Kora component:
              %s

            Problem:
              No matching component was found in the application graph.

            Check:
              - the component exists in the application graph;
              - that @Tag on the test field or parameter matches the component tag;
              - @KoraAppTest components/modules include the required graph root;
              - Or component is specified in @KoraAppTest(components = {...});
              - Or component is provided from a module listed in @KoraAppTest(modules = {...}).
            """.formatted(operation, candidate));
    }

    static ExtensionConfigurationException unsupportedMockType(String annotation, String engine, GraphCandidate candidate) {
        return new ExtensionConfigurationException("""
            Cannot create %s using %s for component:
              %s

            Problem:
              Component type does not resolve to a raw class.

            Fix:
              Declare %s on a class or parameterized class type.
            """.formatted(annotation, engine, candidate, annotation));
    }
}

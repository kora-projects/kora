package io.koraframework.kora.app.annotation.processor.app;

import io.koraframework.common.annotation.KoraApp;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.annotation.Tag;

@KoraApp
public interface AppWithTagMismatchDependency {

    @Root
    default Object root(@Tag(RequiredTag.class) TestService service) {
        return service;
    }

    default TestService service() {
        return new TestService();
    }

    final class RequiredTag {}

    final class TestService {}
}

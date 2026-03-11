package io.koraframework.validation.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;

public class AbstractValidationAnnotationProcessorTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() +
               """
                   import java.util.concurrent.CompletableFuture;
                   import java.util.concurrent.CompletionStage;
                   import io.koraframework.json.common.JsonNullable;
                   import org.jspecify.annotations.NonNull;
                   import org.jspecify.annotations.Nullable;
                   import io.koraframework.common.KoraApp;
                   import io.koraframework.common.Component;
                   import io.koraframework.common.annotation.Root;
                   import io.koraframework.validation.common.annotation.*;
                   import io.koraframework.validation.common.Validator;
                   import io.koraframework.validation.common.constraint.ValidatorModule;
                   """;
    }
}

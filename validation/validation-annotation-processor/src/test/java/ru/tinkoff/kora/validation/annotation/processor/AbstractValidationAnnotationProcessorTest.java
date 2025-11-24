package ru.tinkoff.kora.validation.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;

public class AbstractValidationAnnotationProcessorTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() +
               """
                   import java.util.concurrent.CompletableFuture;
                   import java.util.concurrent.CompletionStage;
                   import ru.tinkoff.kora.json.common.JsonNullable;
                   import jakarta.annotation.Nonnull;
                   import jakarta.annotation.Nullable;
                   import ru.tinkoff.kora.common.KoraApp;
                   import ru.tinkoff.kora.common.Component;
                   import ru.tinkoff.kora.common.annotation.Root;
                   import ru.tinkoff.kora.validation.common.annotation.*;
                   import ru.tinkoff.kora.validation.common.Validator;
                   import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;
                   """;
    }
}

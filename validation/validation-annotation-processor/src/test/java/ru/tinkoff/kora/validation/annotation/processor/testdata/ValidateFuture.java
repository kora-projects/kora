package ru.tinkoff.kora.validation.annotation.processor.testdata;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.validation.common.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class ValidateFuture {

    public static final String IGNORED = "ops";

    @Validate
    public CompletionStage<Integer> validatedInput(@Range(from = 1, to = Integer.MAX_VALUE) int c1,
                                                   @Nullable @NotEmpty @Pattern(".*") String c2,
                                                   @Nullable @Valid ValidTaz c3) {
        return CompletableFuture.completedFuture(c1);
    }

    @Validate
    public CompletableFuture<Void> validatedInputVoid(@Range(from = 1, to = 5) int c1,
                                                      @Nullable @NotEmpty String c2,
                                                      @Nullable @Valid ValidTaz c3) {
        return CompletableFuture.completedFuture(null);
    }

    @Range(from = 1, to = 2)
    @Validate
    public CompletionStage<Integer> validatedOutputSimple(@Nullable ValidTaz c4) {
        return (c4 == null)
                ? CompletableFuture.completedFuture(0)
                : CompletableFuture.completedFuture(1);
    }

    @Range(from = 1, to = 2)
    @Validate
    public CompletionStage<@org.jetbrains.annotations.Nullable Integer> validatedOutputNull(@Nullable ValidTaz c4) {
        return (c4 == null)
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.completedFuture(1);
    }

    @Size(min = 1, max = 1)
    @Nullable
    @Valid
    @Validate
    public CompletionStage<List<ValidTaz>> validatedOutput(ValidTaz c3,
                                                           @Nullable ValidTaz c4) {
        return (c4 == null)
                ? CompletableFuture.completedFuture(List.of(c3))
                : CompletableFuture.completedFuture(List.of(c3, c4));
    }

    @Size(min = 1, max = 1)
    @Nullable
    @Valid
    @Validate
    public CompletionStage<List<ValidTaz>> validatedInputAndOutput(@Range(from = 1, to = 5) int c1,
                                                                   @Nullable @NotEmpty @Pattern(".*") String c2,
                                                                   @Valid ValidTaz c3,
                                                                   @Nullable ValidTaz c4) {
        return (c4 == null)
                ? CompletableFuture.completedFuture(List.of(c3))
                : CompletableFuture.completedFuture(List.of(c3, c4));
    }

    @Size(min = 1, max = 1)
    @Nullable
    @Valid
    @Validate(failFast = true)
    public CompletionStage<List<ValidTaz>> validatedInputAndOutputAndFailFast(@Range(from = 1, to = 5) int c1,
                                                                              @Nullable @NotEmpty String c2,
                                                                              @Valid ValidTaz c3,
                                                                              @Nullable ValidTaz c4) {
        return (c4 == null)
                ? CompletableFuture.completedFuture(List.of(c3))
                : CompletableFuture.completedFuture(List.of(c3, c4));
    }
}

package ru.tinkoff.kora.validation.annotation.processor.testdata;

import reactor.core.publisher.Flux;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.validation.common.annotation.*;

import javax.annotation.Nullable;
import java.util.List;

@Component
public class ValidateFlux {

    public static final String IGNORED = "ops";

    @Validate
    public Flux<Void> validatedInput(@Range(from = 1, to = 5) int c1,
                                     @Nullable @NotEmpty String c2,
                                     @Nullable @Valid ValidTaz c3) {
        return Flux.empty();
    }

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    public Flux<List<ValidTaz>> validatedOutput(ValidTaz c3,
                                                @Nullable ValidTaz c4) {
        return (c4 == null)
            ? Flux.just(List.of(c3))
            : Flux.just(List.of(c3, c4));
    }

    @Range(from = 1, to = 2)
    @Nullable
    @Validate
    public Flux<Integer> validatedOutputSimple(@Nullable ValidTaz c4) {
        return (c4 == null)
            ? Flux.just(0)
            : Flux.just(1);
    }

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    public Flux<List<ValidTaz>> validatedInputAndOutput(@Range(from = 1, to = 5) int c1,
                                                        @Nullable @NotEmpty String c2,
                                                        @Valid ValidTaz c3,
                                                        @Nullable ValidTaz c4) {
        return (c4 == null)
            ? Flux.just(List.of(c3))
            : Flux.just(List.of(c3, c4));
    }

    @Size(min = 1, max = 1)
    @Valid
    @Validate(failFast = true)
    public Flux<List<ValidTaz>> validatedInputAndOutputAndFailFast(@Range(from = 1, to = 5) int c1,
                                                                   @Nullable @NotEmpty String c2,
                                                                   @Valid ValidTaz c3,
                                                                   @Nullable ValidTaz c4) {
        return (c4 == null)
            ? Flux.just(List.of(c3))
            : Flux.just(List.of(c3, c4));
    }
}

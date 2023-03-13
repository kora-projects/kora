package ru.tinkoff.kora.validation.annotation.processor.testdata;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.validation.common.annotation.*;

import javax.annotation.Nullable;
import java.util.List;

@Component
public class ValidateFlux {

    @Validate
    public Flux<Void> validatedInput(@Range(from = 1, to = 5) int c1,
                                     @NotEmpty String c2,
                                     @Valid @Nullable ValidTaz c3) {
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

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    public Flux<List<ValidTaz>> validatedInputAndOutput(@Range(from = 1, to = 5) int c1,
                                                        @NotEmpty String c2,
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
                                                                   @NotEmpty String c2,
                                                                   @Valid ValidTaz c3,
                                                                   @Nullable ValidTaz c4) {
        return (c4 == null)
            ? Flux.just(List.of(c3))
            : Flux.just(List.of(c3, c4));
    }
}

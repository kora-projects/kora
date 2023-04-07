package ru.tinkoff.kora.validation.annotation.processor.testdata;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.validation.common.annotation.*;

import javax.annotation.Nullable;
import java.util.List;

@Component
public class ValidateMono {

    public static final String IGNORED = "ops";

    @Validate
    public Mono<Integer> validatedInput(@Range(from = 1, to = 5) int c1,
                                        @Nullable @NotEmpty String c2,
                                        @Nullable @Valid ValidTaz c3) {
        return Mono.just(c1);
    }

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    public Mono<List<ValidTaz>> validatedOutput(ValidTaz c3,
                                                @Nullable ValidTaz c4) {
        return (c4 == null)
            ? Mono.just(List.of(c3))
            : Mono.just(List.of(c3, c4));
    }

    @Range(from = 1, to = 2)
    @Nullable
    @Validate
    public Mono<Integer> validatedOutputSimple(@Nullable ValidTaz c4) {
        return (c4 == null)
            ? Mono.just(0)
            : Mono.just(1);
    }

    @Size(min = 1, max = 1)
    @Valid
    @Validate
    public Mono<List<ValidTaz>> validatedInputAndOutput(@Range(from = 1, to = 5) int c1,
                                                        @Nullable @NotEmpty String c2,
                                                        @Valid ValidTaz c3,
                                                        @Nullable ValidTaz c4) {
        return (c4 == null)
            ? Mono.just(List.of(c3))
            : Mono.just(List.of(c3, c4));
    }
}

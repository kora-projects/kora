package io.koraframework.config.common.extractor;

import io.koraframework.common.util.Either;
import io.koraframework.config.common.ConfigValue;

public class EitherConfigExtractor<A, B> implements ConfigValueExtractor<Either<A, B>> {

    private final ConfigValueExtractor<A> leftExtractor;
    private final ConfigValueExtractor<B> rightExtractor;

    public EitherConfigExtractor(ConfigValueExtractor<A> leftExtractor, ConfigValueExtractor<B> rightExtractor) {
        this.leftExtractor = leftExtractor;
        this.rightExtractor = rightExtractor;
    }

    @Override
    public Either<A, B> extract(ConfigValue<?> value) {
        try {
            return Either.left(leftExtractor.extract(value));
        } catch (Exception e) {
            return Either.right(rightExtractor.extract(value));
        }
    }
}

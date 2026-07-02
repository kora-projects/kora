package io.koraframework.config.common.mapper;

import io.koraframework.common.Either;
import io.koraframework.config.common.ConfigValue;

public class EitherConfigMapper<A, B> implements ConfigValueMapper<Either<A, B>> {

    private final ConfigValueMapper<A> leftExtractor;
    private final ConfigValueMapper<B> rightExtractor;

    public EitherConfigMapper(ConfigValueMapper<A> leftExtractor, ConfigValueMapper<B> rightExtractor) {
        this.leftExtractor = leftExtractor;
        this.rightExtractor = rightExtractor;
    }

    @Override
    public Either<A, B> map(ConfigValue<?> value) {
        try {
            return Either.left(leftExtractor.map(value));
        } catch (Exception e) {
            return Either.right(rightExtractor.map(value));
        }
    }
}

package ru.tinkoff.kora.s3.client;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

public interface S3ClientModule {

    default S3Config s3Config(Config config, ConfigValueExtractor<S3Config> extractor) {
        var value = config.get("s3.client");
        return extractor.extract(value);
    }
}

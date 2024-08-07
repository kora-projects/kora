package ru.tinkoff.kora.s3.client;

import org.jetbrains.annotations.ApiStatus;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ApiStatus.Experimental
@ConfigValueExtractor
public interface S3ClientConfig {

    String bucket();
}


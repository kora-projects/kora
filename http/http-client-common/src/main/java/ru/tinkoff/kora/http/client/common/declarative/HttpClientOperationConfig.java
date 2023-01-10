package ru.tinkoff.kora.http.client.common.declarative;

import javax.annotation.Nullable;
import java.time.Duration;

public record HttpClientOperationConfig(Duration requestTimeout) { }

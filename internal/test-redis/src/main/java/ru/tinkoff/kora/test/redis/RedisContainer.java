package ru.tinkoff.kora.test.redis;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.time.Duration;

class RedisContainer extends GenericContainer<RedisContainer> {

    private static final int PORT_DEFAULT = 6379;

    private static final DockerImageName DEFAULT_IMAGE = DockerImageName
        .parse("redis:6.2.7-alpine")
        .asCompatibleSubstituteFor(DockerImageName.parse("redis"));

    public RedisContainer() {
        this(DEFAULT_IMAGE);
    }

    public RedisContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("[REDIS]")));
        waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(1)));
        withExposedPorts(PORT_DEFAULT);
    }

    public Integer getPort() {
        return getMappedPort(PORT_DEFAULT);
    }

    public URI getUri() {
        return URI.create(String.format("redis://%s:%s", getHost(), getPort()));
    }
}

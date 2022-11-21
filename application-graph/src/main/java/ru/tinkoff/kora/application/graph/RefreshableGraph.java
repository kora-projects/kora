package ru.tinkoff.kora.application.graph;

import reactor.core.publisher.Mono;

public interface RefreshableGraph extends Graph, Lifecycle {
    Mono<Void> refresh(Node<?> fromNode);
}

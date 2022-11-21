package ru.tinkoff.kora.application.graph;

public interface Graph {
    ApplicationGraphDraw draw();

    <T> T get(Node<T> node);

    <T> ValueOf<T> valueOf(Node<? extends T> node);

    <T> PromiseOf<T> promiseOf(Node<T> node);

    interface Factory<T> {
        T get(Graph graph) throws Exception;
    }
}

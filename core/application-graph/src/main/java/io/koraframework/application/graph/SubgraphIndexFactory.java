package io.koraframework.application.graph;

public final class SubgraphIndexFactory<T> implements Graph.Factory<T> {
    private final Graph.Factory<? extends T> delegate;
    private final int[] seenMap;

    public SubgraphIndexFactory(Graph.Factory<? extends T> delegate, int[] seenMap) {
        this.delegate = delegate;
        this.seenMap = seenMap;
    }

    @Override
    public T get(RefreshableGraph graph) throws Exception {
        return delegate.get(new IndexedGraphView(graph, seenMap));
    }
}

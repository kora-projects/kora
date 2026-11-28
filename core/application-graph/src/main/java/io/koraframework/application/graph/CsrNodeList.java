package io.koraframework.application.graph;

import io.koraframework.application.graph.internal.*;

import java.util.*;

public final class CsrNodeList extends AbstractList<Node<?>> implements RandomAccess {
    private final NodeImpl<?>[] graphNodes;
    private final int[] edges;
    private final int start;
    private final int size;

    public CsrNodeList(NodeImpl<?>[] graphNodes, int[] edges, int start, int end) {
        this.graphNodes = graphNodes;
        this.edges = edges;
        this.start = start;
        this.size = end - start;
    }

    @Override
    public Node<?> get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return graphNodes[edges[start + index]];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }
}

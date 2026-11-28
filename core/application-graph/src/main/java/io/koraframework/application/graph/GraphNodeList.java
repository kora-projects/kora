package io.koraframework.application.graph;

import io.koraframework.application.graph.internal.*;

import java.util.*;

public final class GraphNodeList extends AbstractList<Node<?>> implements RandomAccess {
    private final NodeImpl<?>[] nodes;
    private final int size;

    public GraphNodeList(NodeImpl<?>[] nodes, int size) {
        this.nodes = nodes;
        this.size = size;
    }

    @Override
    public Node<?> get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        return nodes[index];
    }

    @Override
    public int size() {return size;}

    @Override
    public boolean isEmpty() {return size == 0;}
}

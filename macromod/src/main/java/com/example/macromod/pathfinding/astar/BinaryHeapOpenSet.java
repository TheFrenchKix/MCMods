package com.example.macromod.pathfinding.astar;

import java.util.Arrays;

/**
 * A binary min-heap backed by an array, using intrusive {@link PathNode#heapPosition}
 * for O(log n) decrease-key ({@link #update}) and O(1) membership checks.
 *
 * <p>Modeled after Baritone's BinaryHeapOpenSet: each PathNode knows its position
 * in the heap array, so we can bubble up in-place when a node's cost decreases
 * instead of the insert-and-skip pattern Java's PriorityQueue forces.</p>
 */
public final class BinaryHeapOpenSet {

    private static final int INITIAL_CAPACITY = 1024;

    private PathNode[] array;
    private int size;

    public BinaryHeapOpenSet() {
        this(INITIAL_CAPACITY);
    }

    public BinaryHeapOpenSet(int capacity) {
        this.size = 0;
        this.array = new PathNode[capacity];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Inserts a new node into the heap. The node must not already be in the heap.
     */
    public void insert(PathNode node) {
        if (size >= array.length - 1) {
            array = Arrays.copyOf(array, array.length << 1);
        }
        size++;
        node.heapPosition = size;
        array[size] = node;
        bubbleUp(node);
    }

    /**
     * Decreases a node's key (combinedCost) and restores heap order.
     * The caller must have already updated {@code node.combinedCost} before calling.
     * O(log n) — only bubbles up since cost can only decrease.
     */
    public void update(PathNode node) {
        bubbleUp(node);
    }

    /**
     * Removes and returns the node with the smallest combinedCost.
     * Sets its heapPosition to -1 (closed).
     */
    public PathNode removeLowest() {
        if (size == 0) {
            throw new IllegalStateException("Cannot remove from empty heap");
        }
        PathNode result = array[1];
        PathNode last = array[size];
        array[1] = last;
        last.heapPosition = 1;
        array[size] = null;
        size--;
        result.heapPosition = -1; // mark as closed

        if (size >= 2) {
            bubbleDown(1);
        }
        return result;
    }

    private void bubbleUp(PathNode node) {
        int index = node.heapPosition;
        double cost = node.combinedCost;

        while (index > 1) {
            int parentIdx = index >>> 1;
            PathNode parent = array[parentIdx];
            if (parent.combinedCost <= cost) {
                break;
            }
            // Swap parent down
            array[index] = parent;
            parent.heapPosition = index;
            array[parentIdx] = node;
            node.heapPosition = parentIdx;
            index = parentIdx;
        }
    }

    private void bubbleDown(int index) {
        PathNode node = array[index];
        double cost = node.combinedCost;

        int child = index << 1;
        while (child <= size) {
            PathNode childNode = array[child];
            double childCost = childNode.combinedCost;

            // Pick the smaller child
            if (child < size) {
                PathNode rightChild = array[child + 1];
                if (rightChild.combinedCost < childCost) {
                    child++;
                    childNode = rightChild;
                    childCost = rightChild.combinedCost;
                }
            }

            if (cost <= childCost) {
                break;
            }

            // Swap child up
            array[index] = childNode;
            childNode.heapPosition = index;
            array[child] = node;
            node.heapPosition = child;

            index = child;
            child = index << 1;
        }
    }
}

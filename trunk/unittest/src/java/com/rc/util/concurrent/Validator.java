package com.rc.util.concurrent;

import static java.lang.String.format;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.testng.Assert;

import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionPolicy;
import com.rc.util.concurrent.ConcurrentLinkedHashMap.Node;
import com.rc.util.concurrent.ConcurrentLinkedHashMap.Node.State;

/**
 * Validations for the concurrent data structure.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class Validator extends Assert {
    private final boolean exhaustive;

    /**
     * A validator for the {@link ConcurrentLinkedHashMap}.
     *
     * @param exhaustive Whether to perform deep validations.
     */
    public Validator(boolean exhaustive) {
        this.exhaustive = exhaustive;
    }

    /**
     * @return Whether in exhaustive validation mode.
     */
    public boolean isExhaustive() {
        return exhaustive;
    }

    /**
     * Validates that the map is in a correct state.
     */
    public void state(ConcurrentLinkedHashMap<?, ?> map) {
        assertEquals(map.capacity(), map.capacity.get(), "Tracked capacity != reported capacity");
        assertTrue(map.length.get() <= map.capacity.get(), "The list size is greater than the capacity");
        assertEquals(map.data.size(), map.size(), "Internal size != reported size");
        assertTrue(map.capacity() >= map.size(), format("Overflow: c=%d s=%d", map.capacity(), map.size()));
        assertNotNull(map.head.getNext());
        assertNotNull(map.tail.getPrev());

        if (exhaustive) {
            links(map);
        }
    }

    /**
     * Validates that the linked map is empty.
     */
    public void empty(ConcurrentLinkedHashMap<?, ?> map) {
        assertTrue(map.isEmpty(), "Not empty");
        assertTrue(map.data.isEmpty(), "Internel not empty");

        assertEquals(map.size(), 0, "Size != 0");
        assertEquals(map.size(), map.data.size(), "Internel size != 0");

        assertTrue(map.keySet().isEmpty(), "Not empty key set");
        assertTrue(map.values().isEmpty(), "Not empty value set");
        assertTrue(map.entrySet().isEmpty(), "Not empty entry set");
        assertEquals(map, Collections.emptyMap(), "Not equal to empty map");
        assertEquals(map.hashCode(), Collections.emptyMap().hashCode(), "Not equal hash codes");
        assertEquals(map.toString(), Collections.emptyMap().toString(), "Not equal string representations");
        allNodesDead(map);
    }

    /**
     * Validates that the doubly-linked list running through the map is in a correct state.
     */
    private void links(ConcurrentLinkedHashMap<?, ?> map) {
        sentinelNode(map, map.head, true);
        sentinelNode(map, map.tail, false);

        Map<Node<?, ?>, Object> seen = new IdentityHashMap<Node<?, ?>, Object>();
        Node<?, ?> current = map.head;
        Object dummy = new Object();
        int dead = 0;
        for (;;) {
            assertNull(seen.put(current, dummy), "Loop detected in list: " + current + " seen: " + seen);
            if (current.getValue() == null) {
                dead++;
            }
            if (current.getState() == State.SENTINEL) {
                sentinelNode(map, current, current == map.head);
            } else {
                dataNode(map, current);
            }
            if (current == map.tail) {
                break;
            }
            current = current.getNext();
        }
        assertEquals(map.size(), seen.size()-dead, "Size != active list size");
    }

    /**
     * Validates that the sentinel node is in a proper state.
     *
     * @param node  The sentinel node.
     * @param order The self-linked side - <tt>true</tt> if left, <tt>false</tt> if right.
     */
    public void sentinelNode(ConcurrentLinkedHashMap<?, ?> map, Node<?, ?> node, boolean order) {
        assertEquals(node.getState(), State.SENTINEL);
        assertNull(node.getKey());
        assertNull(node.getValue());
        assertFalse(node.isMarked());
        assertSame(node, order ? node.getPrev() : node.getNext());
        assertNotSame(node, order ? node.getNext() : node.getPrev());
        assertFalse(map.data.containsValue(node));
    }

    /**
     * Validates the the data node is in a proper state.
     *
     * @param node The data node.
     */
    public void dataNode(ConcurrentLinkedHashMap<?, ?> map, Node<?, ?> node) {
        assertEquals(node.getState(), State.LINKED);
        assertNotNull(node.getKey());
        if (node.getValue() == null) {
            if (map.policy != EvictionPolicy.LRU) {
                assertFalse(map.containsKey(node.getKey()), "Dead node referenced by key");
            }
        } else {
            assertTrue(map.containsKey(node.getKey()), "Live node has null value");
            assertTrue(map.containsValue(node.getValue()), format("Could not find value: %s", node.getValue()));
            assertEquals(map.data.get(node.getKey()).getValue(), node.getValue());
            assertSame(map.data.get(node.getKey()), node);
        }
        assertNotNull(node.getPrev());
        assertNotNull(node.getNext());
        assertNotSame(node, node.getPrev());
        assertNotSame(node, node.getNext());
        assertSame(node, node.getPrev().getNext());
        assertSame(node, node.getNext().getPrev());
    }

    /**
     * Validates that all data nodes are marked as specified.
     *
     * @param mark Whether the nodes are saved from eviction.
     */
    public void allNodesMarked(ConcurrentLinkedHashMap<?, ?> map, boolean isMarked) {
        for (Node<?, ?> node : map.data.values()) {
            assertEquals(node.isMarked(), isMarked, format("Node #%d", node.getKey()));
        }
    }

    /**
     * Validates that all data nodes dead.
     */
    public void allNodesDead(ConcurrentLinkedHashMap<?, ?> map) {
        Node<?, ?> current = map.head.getNext();
        while (current != map.tail) {
            assertNull(current.getValue(), format("Node #%d not dead", current.getKey()));
            assertFalse(current.isMarked(), format("Node #%d marked as saved", current.getKey()));
            current = current.getNext();
        }
    }

    /**
     * A string representation of the map's list nodes in the list's current order.
     */
    public String externalizeLinkedList(ConcurrentLinkedHashMap<?, ?> map) {
        StringBuilder builder = new StringBuilder();
        Node<?, ?> current = map.head.getNext();
        while (current != map.tail) {
            builder.append(current).append('\n');
            current = current.getNext();
        }
        return builder.toString();
    }
}

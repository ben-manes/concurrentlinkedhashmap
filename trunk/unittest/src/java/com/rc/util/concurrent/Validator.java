package com.rc.util.concurrent;

import static java.lang.String.format;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.testng.Assert;

import com.rc.util.concurrent.ConcurrentLinkedHashMap.Node;

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
        assertTrue(map.length.get() <= map.capacity.get(), "The list size is greater than the capacity: "
                   + map.length.get() + "/" + map.capacity.get());
        assertEquals(map.data.size(), map.size(), "Internal size != reported size");
        assertTrue(map.capacity() >= map.size(), format("Overflow: c=%d s=%d", map.capacity(), map.size()));
        assertNotNull(map.sentinel.getNext());
        assertNotNull(map.sentinel.getPrev());

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
    }

    /**
     * Validates that the doubly-linked list running through the map is in a correct state.
     */
    private void links(ConcurrentLinkedHashMap<?, ?> map) {
        sentinelNode(map);

        Map<Node<?, ?>, Object> seen = new IdentityHashMap<Node<?, ?>, Object>();
        Node<?, ?> current = map.sentinel.getNext();
        Object dummy = new Object();
        for (;;) {
            assertNull(seen.put(current, dummy), "Loop detected in list: " + current + " seen: " + seen);
            if (current == map.sentinel) {
                break;
            }
            dataNode(map, current);
            current = current.getNext();
        }
        assertEquals(map.size(), seen.size()-1, "Size != list length");
    }

    /**
     * Validates that the sentinel node is in a proper state.
     *
     * @param node  The sentinel node.
     * @param order The self-linked side - <tt>true</tt> if left, <tt>false</tt> if right.
     */
    public void sentinelNode(ConcurrentLinkedHashMap<?, ?> map) {
        assertNull(map.sentinel.getKey());
        assertNull(map.sentinel.getValue());
        assertFalse(map.sentinel.isMarked());
        assertFalse(map.data.containsValue(map.sentinel));
    }

    /**
     * Validates the the data node is in a proper state.
     *
     * @param node The data node.
     */
    public void dataNode(ConcurrentLinkedHashMap<?, ?> map, Node<?, ?> node) {
        assertNotNull(node.getKey());
        assertNotNull(node.getValue());
        assertTrue(map.containsKey(node.getKey()));
        assertTrue(map.containsValue(node.getValue()), format("Could not find value: %s", node.getValue()));
        assertEquals(map.data.get(node.getKey()).getValue(), node.getValue());
        assertSame(map.data.get(node.getKey()), node);
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
     * A string representation of the map's list nodes in the list's current order.
     */
    public String printFwd(ConcurrentLinkedHashMap<?, ?> map) {
        Map<Node<?, ?>, Object> seen = new IdentityHashMap<Node<?, ?>, Object>();
        StringBuilder buffer = new StringBuilder();
        Node<?, ?> current = map.sentinel;
        do {
            if (seen.put(current, new Object()) != null) {
                buffer.append("Failure: Loop detected\n");
                break;
            }
//            Node<?, ?> next = null;
//            for (int i=0; i<10000; i++) {
//                next = current.getNext();
//                if (next == null) {
//                    next = current.getAuxNext();
//                    if (next == null) {
//                        continue;
//                    }
//                }
//                break;
//            }
            buffer.append(current).append("\n");
            current = current.getNext();
//          current = next;
            if (current == null) {
                buffer.append("Failure: Could not traverse through auxiliary\n");
                break;
            }
        } while (current != map.sentinel);
        return buffer.toString();
    }

    /**
     * A string representation of the map's list nodes in the list's current order.
     */
    public String printBwd(ConcurrentLinkedHashMap<?, ?> map) {
        StringBuilder buffer = new StringBuilder();
        Node<?, ?> current = map.sentinel;
        do {
            buffer.append(current).append("\n");
            Node<?, ?> prev = current.getPrev();
            if (prev == null) {
                buffer.append("Failure: Reached null\n");
                break;
            }
            current = prev;
        } while (current != map.sentinel);
        return buffer.toString();
    }

    public String printNode(Object key, ConcurrentLinkedHashMap<?, ?> map) {
        Node<?, ?> current = map.sentinel;
        while (current.getKey() != key) {
            current = current.getNext();
            if (current == null) {
                return null;
            }
        }
        return current.toString();
    }
}

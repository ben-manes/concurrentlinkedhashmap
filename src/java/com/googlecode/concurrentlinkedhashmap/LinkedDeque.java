/*
 * Copyright 2011 Benjamin Manes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.concurrentlinkedhashmap;

import java.util.AbstractQueue;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Linked list implementation of the {@link Deque} interface where the link
 * pointers are tightly integrated with the element. Linked deques have no
 * capacity restrictions; they grow as necessary to support usage. They are not
 * thread-safe; in the absence of external synchronization, they do not support
 * concurrent access by multiple threads. Null elements are prohibited.
 * <p>
 * Most <tt>LinkedDeque</tt> operations run in constant time by assuming that
 * the {@link Linked} parameters are associated with that the deque instance.
 * Any usage that violates this assumption will result in non-deterministic
 * behavior.
 * <p>
 * The iterators returned by this class are <em>not</em> <i>fail-fast</i>: If
 * the deque is modified at any time after the iterator is created, the iterator
 * will be in an unknown state. Thus, in the face of concurrent modification,
 * the iterator risks arbitrary, non-deterministic behavior at an undetermined
 * time in the future.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 * @param <E> the type of elements held in this collection
 * @see <tt>http://code.google.com/p/concurrentlinkedhashmap/</tt>
 */
@NotThreadSafe
final class LinkedDeque<E extends Linked<E>> extends AbstractQueue<E> implements Deque<E> {

  // The class implements a doubly-linked list that is optimized for the virtual
  // machine. The first and last elements are manipulated instead of a slightly
  // more convenient sentinel element to avoid the insertion of null checks with
  // NullPointerException throws in the byte code. The links to a removed
  // element are cleared to help a generational garbage collector if the
  // discarded elements inhabit more than one generation.

  /**
   * Pointer to first node.
   * Invariant: (first == null && last == null) ||
   *            (first.prev == null && first.item != null)
   */
  E first;

  /**
   * Pointer to last node.
   * Invariant: (first == null && last == null) ||
   *            (last.next == null && last.item != null)
   */
  E last;

  int size;

  /**
   * Links the element to the front of the deque so that it becomes the first
   * element.
   *
   * @param e the unlinked element
   */
  void linkFirst(final E e) {
    final E f = first;
    first = e;

    if (f == null) {
      last = e;
    } else {
      f.setPrevious(e);
      e.setNext(f);
    }
    size++;
  }

  /**
   * Links the element to the back of the deque so that it becomes the last
   * element.
   *
   * @param e the unlinked element
   */
  void linkLast(final E e) {
    final E previousLast = last;
    last = e;

    if (previousLast == null) {
      first = e;
    } else {
      previousLast.setNext(e);
      e.setPrevious(previousLast);
    }
    size++;
  }

  /** Unlinks the non-null first element. */
  E unlinkFirst() {
    final E f = first;
    final E next = f.getNext();
    f.setNext(null);

    first = next;
    if (next == null) {
      last = null;
    } else {
      next.setPrevious(null);
    }
    size--;
    return f;
  }

  /** Unlinks the non-null last element. */
  E unlinkLast() {
    final E l = last;
    final E prev = l.getPrevious();
    l.setPrevious(null);
    last = prev;
    if (prev == null) {
      first = null;
    } else {
      prev.setNext(null);
    }
    size--;
    return l;
  }

  /** Unlinks the non-null element. */
  void unlink(E e) {
    final E prev = e.getPrevious();
    final E next = e.getNext();

    if (prev == null) {
      first = next;
    } else {
      prev.setNext(next);
      e.setPrevious(null);
    }

    if (next == null) {
      last = prev;
    } else {
      next.setPrevious(prev);
      e.setNext(null);
    }
    size--;
  }

  /**
   * Moves the element to the front of the deque so that it becomes the first
   * element.
   *
   * @param e the linked element
   */
  public void moveToFront(E e) {
    if (e != first) {
      unlink(e);
      linkFirst(e);
    }
  }

  /**
   * Moves the element to the back of the deque so that it becomes the last
   * element.
   *
   * @param e the linked element
   */
  public void moveToBack(E e) {
    if (e != last) {
      unlink(e);
      linkLast(e);
    }
  }

  @Override
  public boolean isEmpty() {
    return (first == null);
  }

  void checkNotEmpty() {
    if (isEmpty()) {
      throw new NoSuchElementException();
    }
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public void clear() {
    for (E e = first; e != null;) {
      E next = e.getNext();
      e.setPrevious(null);
      e.setNext(null);
      e = next;
    }
    first = last = null;
    size = 0;
  }

  @Override
  public boolean contains(Object o) {
    Linked<?> e = (Linked<?>) o;
    return (e.getPrevious() != null)
        || (e.getNext() != null)
        || (e == first);
  }

  @Override
  public boolean offer(E e) {
    return offerLast(e);
  }

  @Override
  public boolean offerFirst(E e) {
    linkFirst(e);
    return true;
  }

  @Override
  public boolean offerLast(E e) {
    linkLast(e);
    return true;
  }

  @Override
  public E peek() {
    return peekFirst();
  }

  @Override
  public E peekFirst() {
    return first;
  }

  @Override
  public E peekLast() {
    return last;
  }

  @Override
  public E poll() {
    return pollFirst();
  }

  @Override
  public E pollFirst() {
    return isEmpty() ? null : unlinkFirst();
  }

  @Override
  public E pollLast() {
    return isEmpty() ? null : unlinkLast();
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean remove(Object o) {
    if (contains(o)) {
      unlink((E) o);
      return true;
    }
    return false;
  }

  @Override
  public E removeFirst() {
    checkNotEmpty();
    return pollFirst();
  }

  @Override
  public boolean removeFirstOccurrence(Object o) {
    return remove(o);
  }

  @Override
  public E removeLast() {
    checkNotEmpty();
    return pollLast();
  }

  @Override
  public boolean removeLastOccurrence(Object o) {
    return remove(o);
  }

  @Override
  public void addFirst(E e) {
    if (!offerFirst(e)) {
      throw new IllegalStateException();
    }
  }

  @Override
  public void addLast(E e) {
    if (!offerLast(e)) {
      throw new IllegalStateException();
    }
  }

  @Override
  public E getFirst() {
    checkNotEmpty();
    return peekFirst();
  }

  @Override
  public E getLast() {
    checkNotEmpty();
    return peekLast();
  }

  @Override
  public E pop() {
    return removeFirst();
  }

  @Override
  public void push(E e) {
    addFirst(e);
  }

  @Override
  public Iterator<E> iterator() {
    return new AbstractLinkedIterator(first) {
      @Override E computeNext() {
        return cursor.getNext();
      }
    };
  }

  @Override
  public Iterator<E> descendingIterator() {
    return new AbstractLinkedIterator(last) {
      @Override E computeNext() {
        return cursor.getPrevious();
      }
    };
  }

  abstract class AbstractLinkedIterator implements Iterator<E> {
    E cursor;

    AbstractLinkedIterator(E start) {
      cursor = start;
    }

    @Override
    public boolean hasNext() {
      return (cursor != null);
    }

    @Override
    public E next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      E e = cursor;
      cursor = computeNext();
      return e;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    abstract E computeNext();
  }
}

interface Linked<T extends Linked<T>> {
  T getNext();
  void setNext(T next);

  T getPrevious();
  void setPrevious(T prev);
}

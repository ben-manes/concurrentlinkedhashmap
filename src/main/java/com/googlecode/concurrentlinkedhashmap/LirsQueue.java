/*
 * Copyright 2012 Ben Manes. All Rights Reserved.
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

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A linked deque implementation used to represent the LIRS queue.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 * @param <E> the type of elements held in this collection
 * @see <a href="http://code.google.com/p/concurrentlinkedhashmap/">
 *      http://code.google.com/p/concurrentlinkedhashmap/</a>
 */
@NotThreadSafe
final class LirsQueue<E extends LinkedOnLirsQueue<E>> extends AbstractLinkedDeque<E> {

  @Override
  @SuppressWarnings("unchecked")
  public boolean contains(Object o) {
    return (o instanceof LinkedOnLirsQueue<?>) && contains((E) o);
  }

  // A fast-path containment check
  boolean contains(LinkedOnLirsQueue<?> e) {
    return (e.getPreviousOnLirsQueue() != null)
        || (e.getNextOnLirsQueue() != null)
        || (e == first);
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean remove(Object o) {
    return (o instanceof LinkedOnLirsQueue<?>) && remove((E) o);
  }

  // A fast-path removal
  boolean remove(E e) {
    if (contains(e)) {
      unlink(e);
      return true;
    }
    return false;
  }

  @Override
  protected E getPrevious(E e) {
    return e.getPreviousOnLirsQueue();
  }

  @Override
  protected void setPrevious(E e, E prev) {
    e.setPreviousOnLirsQueue(prev);
  }

  @Override
  protected E getNext(E e) {
    return e.getNextOnLirsQueue();
  }

  @Override
  protected void setNext(E e, E next) {
    e.setNextOnLirsQueue(next);
  }
}

/** An element that is linked on the {@link LirsQueue}. */
interface LinkedOnLirsQueue<E extends LinkedOnLirsQueue<E>> {

  /**
   * Retrieves the previous element or <tt>null</tt> if either the element is
   * unlinked or the first element on the deque.
   */
  E getPreviousOnLirsQueue();

  /** Sets the previous element or <tt>null</tt> if there is no link. */
  void setPreviousOnLirsQueue(E prev);

  /**
   * Retrieves the next element or <tt>null</tt> if either the element is
   * unlinked or the last element on the deque.
   */
  E getNextOnLirsQueue();

  /** Sets the next element or <tt>null</tt> if there is no link. */
  void setNextOnLirsQueue(E next);
}

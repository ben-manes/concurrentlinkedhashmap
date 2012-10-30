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
 * A linked deque implementation used to represent the LIRS stack.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 * @param <E> the type of elements held in this collection
 * @see <a href="http://code.google.com/p/concurrentlinkedhashmap/">
 *      http://code.google.com/p/concurrentlinkedhashmap/</a>
 */
@NotThreadSafe
final class LirsStack<E extends LinkedOnLirsStack<E>> extends AbstractLinkedDeque<E> {

  @Override
  @SuppressWarnings("unchecked")
  public boolean contains(Object o) {
    return (o instanceof LinkedOnLirsStack<?>) && contains((E) o);
  }

  // A fast-path containment check
  boolean contains(LinkedOnLirsStack<?> e) {
    return (e.getPreviousOnLirsStack() != null)
        || (e.getNextOnLirsStack() != null)
        || (e == first);
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean remove(Object o) {
    return (o instanceof LinkedOnLirsStack<?>) && remove((E) o);
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
    return e.getPreviousOnLirsStack();
  }

  @Override
  protected void setPrevious(E e, E prev) {
    e.setPreviousOnLirsStack(prev);
  }

  @Override
  protected E getNext(E e) {
    return e.getNextOnLirsStack();
  }

  @Override
  protected void setNext(E e, E next) {
    e.setNextOnLirsStack(next);
  }
}

/** An element that is linked on the {@link LirsStack}. */
interface LinkedOnLirsStack<E extends LinkedOnLirsStack<E>> {

  /**
   * Retrieves the previous element or <tt>null</tt> if either the element is
   * unlinked or the first element on the deque.
   */
  E getPreviousOnLirsStack();

  /** Sets the previous element or <tt>null</tt> if there is no link. */
  void setPreviousOnLirsStack(E prev);

  /**
   * Retrieves the next element or <tt>null</tt> if either the element is
   * unlinked or the last element on the deque.
   */
  E getNextOnLirsStack();

  /** Sets the next element or <tt>null</tt> if there is no link. */
  void setNextOnLirsStack(E next);
}

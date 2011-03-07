package com.googlecode.concurrentlinkedhashmap;

import static com.google.common.collect.Iterators.elementsEqual;
import static com.googlecode.concurrentlinkedhashmap.IsEmptyCollection.emptyCollection;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A unit-test for {@link LinkedDeque} methods.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public final class LinkedDequeTest {
  private static final int WARMED_SIZE = 100;

  @Test(dataProvider = "emptyDeque")
  public void clear_whenEmpty(LinkedDeque<SimpleLinkedValue> deque) {
    deque.clear();
    assertThat(deque, is(emptyCollection()));
  }

  @Test(dataProvider = "warmedDeque")
  public void clear_whenPopulated(LinkedDeque<SimpleLinkedValue> deque) {
    deque.clear();
    assertThat(deque, is(emptyCollection()));
  }

  @Test(dataProvider = "emptyDeque")
  public void isEmpty_whenEmpty(LinkedDeque<SimpleLinkedValue> deque) {
    assertThat(deque.isEmpty(), is(true));
  }

  @Test(dataProvider = "warmedDeque")
  public void isEmpty_whenPopulated(LinkedDeque<SimpleLinkedValue> deque) {
    assertThat(deque.isEmpty(), is(false));
  }

  @Test(dataProvider = "emptyDeque")
  public void size_whenEmpty(LinkedDeque<SimpleLinkedValue> deque) {
    assertThat(deque.size(), is(0));
  }

  @Test(dataProvider = "warmedDeque")
  public void size_whenPopulated(LinkedDeque<SimpleLinkedValue> deque) {
    assertThat(deque.size(), is(WARMED_SIZE));
    assertThat(Iterables.size(deque), is(WARMED_SIZE));
  }

  @Test(dataProvider = "emptyDeque", expectedExceptions = NoSuchElementException.class)
  public void remove_whenEmpty(LinkedDeque<SimpleLinkedValue> deque) {
    assertThat(deque.remove(), is(nullValue()));
  }

  @Test(dataProvider = "warmedDeque")
  public void remove_toEmpty(LinkedDeque<SimpleLinkedValue> deque) {
    List<SimpleLinkedValue> copy = Lists.newArrayList(deque);
    for (SimpleLinkedValue link : copy) {
      assertThat(deque.remove(link), is(true));
    }
    assertThat(deque.first, is(nullValue()));
    assertThat(deque.last, is(nullValue()));
    assertThat(deque.size(), is(0));
  }

  @Test(dataProvider = "warmedDeque")
  public void add(LinkedDeque<SimpleLinkedValue> deque) {
    List<SimpleLinkedValue> copy = Lists.newArrayList(deque);
    assertThat(deque.size(), is(copy.size()));
    assertThat(copy.size(), is(WARMED_SIZE));
    for (SimpleLinkedValue link : copy) {
      assertThat(deque.contains(link), is(true));
    }
  }

  @Test(dataProvider = "emptyDeque")
  public void poll_whenEmpty(LinkedDeque<SimpleLinkedValue> deque) {
    assertThat(deque.poll(), is(nullValue()));
  }

  @Test(dataProvider = "warmedDeque")
  public void poll_whenPopulated(LinkedDeque<SimpleLinkedValue> deque) {
    SimpleLinkedValue first = deque.first;
    assertThat(deque.poll(), is(first));
    assertThat(first, is(not(nullValue())));
    assertThat(deque, hasSize(WARMED_SIZE - 1));
    assertThat(first.getPrevious(), is(nullValue()));
    assertThat(first.getNext(), is(nullValue()));
  }

  @Test(dataProvider = "emptyDeque")
  public void poll_toEmpty(LinkedDeque<SimpleLinkedValue> deque) {
    SimpleLinkedValue link = new SimpleLinkedValue(0);
    deque.add(link);
    assertThat(deque.poll(), is(link));
    assertThat(deque.first, is(nullValue()));
    assertThat(deque.last, is(nullValue()));
  }

  @Test(dataProvider = "emptyDeque")
  public void iterator_whenEmpty(LinkedDeque<SimpleLinkedValue> deque) {
    assertThat(deque.iterator().hasNext(), is(false));
  }

  @Test(dataProvider = "warmedDeque")
  public void iterator_whenWarmed(LinkedDeque<SimpleLinkedValue> deque) {
    List<SimpleLinkedValue> expected = Lists.newArrayList();
    warmUp(expected);

    assertThat(elementsEqual(deque.iterator(), expected.iterator()), is(true));
  }

  @Test(dataProvider = "emptyDeque")
  public void descendingIterator_whenEmpty(LinkedDeque<SimpleLinkedValue> deque) {
    assertThat(deque.iterator().hasNext(), is(false));
  }

  @Test(dataProvider = "warmedDeque")
  public void descendingIterator_whenWarmed(LinkedDeque<SimpleLinkedValue> deque) {
    List<SimpleLinkedValue> expected = Lists.newArrayList();
    warmUp(expected);
    Collections.reverse(expected);

    assertThat(elementsEqual(deque.descendingIterator(), expected.iterator()), is(true));
  }

  /* ---------------- Deque providers -------------- */

  @DataProvider(name = "emptyDeque")
  public Object[][] providesEmptyDeque() {
    return new Object[][] {{
      new LinkedDeque<SimpleLinkedValue>()
    }};
  }

  @DataProvider(name = "warmedDeque")
  public Object[][] providesWarmedDeque() {
    LinkedDeque<SimpleLinkedValue> deque = new LinkedDeque<SimpleLinkedValue>();
    warmUp(deque);
    return new Object[][] {{ deque }};
  }

  static void warmUp(Collection<SimpleLinkedValue> collection) {
    for (int i = 0; i < WARMED_SIZE; i++) {
      collection.add(new SimpleLinkedValue(i));
    }
  }

  static final class SimpleLinkedValue implements Linked<SimpleLinkedValue> {
    SimpleLinkedValue prev;
    SimpleLinkedValue next;
    final int value;

    SimpleLinkedValue(int value) {
      this.value = value;
    }

    @Override
    public SimpleLinkedValue getPrevious() {
      return prev;
    }

    @Override
    public void setPrevious(SimpleLinkedValue prev) {
      this.prev = prev;
    }

    @Override
    public SimpleLinkedValue getNext() {
      return next;
    }

    @Override
    public void setNext(SimpleLinkedValue next) {
      this.next = next;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof SimpleLinkedValue)) {
        return false;
      }
      return value == ((SimpleLinkedValue) o).value;
    }

    @Override
    public int hashCode() {
      return value;
    }

    @Override
    public String toString() {
      return String.format("value=%s prev=%s, next=%s]", value,
          (prev == null) ? null : prev.value,
          (next == null) ? null : next.value);
    }
  }
}

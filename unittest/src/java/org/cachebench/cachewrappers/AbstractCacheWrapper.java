package org.cachebench.cachewrappers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

import java.util.List;
import java.util.Map;

/**
 * A template implementation of the {@link CacheWrapper} interface.
 *
 * @author bmanes@google.com (Ben Manes)
 */
public abstract class AbstractCacheWrapper implements CacheWrapper {
  protected final Log logger = LogFactory.getLog(getClass());

  /**
   * Retrieves the capacity of the map.
   */
  protected abstract int capacity();

  /**
   * Retrieves the map to delegate operations to.
   */
  protected abstract Map<Object, Object> delegate();

  @Override
  public void put(List<String> path, Object key, Object value) throws Exception {
    delegate().put(key, value);
  }

  @Override
  public Object get(List<String> path, Object key) throws Exception {
    return delegate().get(key);
  }

  @Override
  public void empty() throws Exception {
    delegate().clear();
  }

  @Override
  public int getNumMembers() {
    return 0;
  }

  @Override
  public String getInfo() {
    return "size/capacity: " + delegate().size() + "/" + capacity();
  }

  @Override
  public Object getReplicatedData(List<String> path, String key) throws Exception {
    return get(path, key);
  }

  @Override
  public Object startTransaction() {
    throw new UnsupportedOperationException("Does not support JTA!");
  }

  @Override
  public void endTransaction(boolean successful) {
    throw new UnsupportedOperationException("Does not support JTA!");
  }

  @Override
  public void tearDown() throws Exception {}
}

package org.cachebench.cachewrappers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

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

  /**
   * Initializes the cache from its configuration. If a configuration file was
   * specified then its properties are added to the parameter map.
   */
  protected abstract void initialize(Map<String, String> parameters);

  @Override
  @SuppressWarnings("unchecked")
  public final void init(Map parameters) throws Exception {
    addPropertiesToMap(parameters);
    initialize(parameters);
  }

  private void addPropertiesToMap(Map<String, String> parameters) throws Exception {
    String resourceName = parameters.get("config");
    if ((resourceName == null) || resourceName.trim().isEmpty()) {
      return;
    }
    InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
    try {
      Properties props = new Properties();
      props.load(stream);
      for (Entry<Object, Object> entry : props.entrySet()) {
        parameters.put((String) entry.getKey(), (String) entry.getValue());
      }
    } finally {
      stream.close();
    }
  }

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

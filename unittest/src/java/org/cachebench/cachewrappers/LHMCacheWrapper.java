// Copyright 2010 Google Inc. All Rights Reserved.

package org.cachebench.cachewrappers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ben.manes@gmail.com (Ben Manes)
 */
public class LHMCacheWrapper implements CacheWrapper {
  private final Log logger = LogFactory.getLog("org.cachebench.cachewrappers.LHMCacheWrapper");
  private Map<Object, Object> cache;
  private int capacity;

  public void init(Map parameters) throws Exception {
//    InputStream stream =
//        getClass().getClassLoader().getResourceAsStream((String) parameters.get("config"));
//    Properties props = new Properties();
//
//    props.load(stream);
//    stream.close();
//
//    capacity = Integer.parseInt(props.getProperty("clhm.maximumCapacity"));

    capacity = 5000;
  }

  public void setUp() throws Exception {
    cache = Collections.synchronizedMap(new LinkedHashMap<Object, Object>(capacity, 0.75f, true) {
      private static final long serialVersionUID = 1L;

      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        return size() > capacity;
      }
    });

  }

  public void tearDown() throws Exception {
  }

  public void put(List<String> path, Object key, Object value) throws Exception {
    cache.put(key, value);
  }

  public Object get(List<String> path, Object key) throws Exception {
    return cache.get(key);
  }

  public void empty() throws Exception {
    cache.clear();
  }

  public int getNumMembers() {
    return 0;
  }

  public String getInfo() {
    return "size/capacity: " + cache.size() + "/" + capacity;
  }

  public Object getReplicatedData(List<String> path, String key) throws Exception {
    return get(path, key);
  }

  public Object startTransaction() {
    throw new UnsupportedOperationException("Does not support JTA!");
  }

  public void endTransaction(boolean successful) {
    throw new UnsupportedOperationException("Does not support JTA!");
  }
}

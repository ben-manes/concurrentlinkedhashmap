// Copyright 2010 Google Inc. All Rights Reserved.

package org.cachebench.cachewrappers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ben.manes@gmail.com (Ben Manes)
 */
public class CHMCacheWrapper implements CacheWrapper {
  private final Log logger = LogFactory.getLog("org.cachebench.cachewrappers.LHMCacheWrapper");
  private Map<Object, Object> cache;
  private int capacity;

  @Override
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

  @Override
  public void setUp() throws Exception {
    cache = new ConcurrentHashMap<Object, Object>(capacity);
  }

  @Override
  public void tearDown() throws Exception {
  }

  @Override
  public void put(List<String> path, Object key, Object value) throws Exception {
    cache.put(key, value);
  }

  @Override
  public Object get(List<String> path, Object key) throws Exception {
    return cache.get(key);
  }

  @Override
  public void empty() throws Exception {
    cache.clear();
  }

  @Override
  public int getNumMembers() {
    return 0;
  }

  @Override
  public String getInfo() {
    return "size/capacity: " + cache.size() + "/" + capacity;
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
}
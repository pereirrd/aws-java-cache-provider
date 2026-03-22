package io.github.pereirrd.awsjavacache.core;

public interface CacheProvider {

  String get(String key);

  void put(String key, String value);

  void remove(String key);

  void clear();

  void close();

  void flush();

  void invalidate(String key);
}

package io.github.pereirrd.awsjavacache.core;

/** Cache de chaves e valores texto (UTF-8 / string). */
public interface CacheProvider {

  String get(String key);

  void put(String key, String value);

  void remove(String key);

  void clear();

  void close();

  void flush();

  void invalidate(String key);
}

package io.github.pereirrd.awsjavacache.core;

import java.util.List;

/** Plugin de persistência para *miss* no cache (chaves e payloads como texto). */
public interface MissPlugin {

  String findById(String id);

  void save(String entity);

  void deleteById(String id);

  List<String> findAll();

  long count();

  boolean existsById(String id);

  void deleteAll();
}

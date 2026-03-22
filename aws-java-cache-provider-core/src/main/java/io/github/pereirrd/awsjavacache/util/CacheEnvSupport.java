package io.github.pereirrd.awsjavacache.util;

import java.util.Locale;
import java.util.Map;

public class CacheEnvSupport {

  private CacheEnvSupport() {}

  public static String required(Map<String, String> env, String key) {
    var value = env.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing or blank environment variable: " + key);
    }

    return value.trim();
  }

  public static String optional(Map<String, String> env, String key) {
    var value = env.get(key);

    return value == null ? null : value.trim();
  }

  public static int parseInt(Map<String, String> env, String key, int defaultValue) {
    var raw = optional(env, key);
    if (raw == null || raw.isEmpty()) {
      return defaultValue;
    }

    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid integer for " + key + ": " + raw, e);
    }
  }

  public static long parseLong(Map<String, String> env, String key, long defaultValue) {
    var raw = optional(env, key);
    if (raw == null || raw.isEmpty()) {
      return defaultValue;
    }

    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid long for " + key + ": " + raw, e);
    }
  }

  public static boolean parseBoolean(Map<String, String> env, String key, boolean defaultValue) {
    var raw = optional(env, key);
    if (raw == null || raw.isEmpty()) {
      return defaultValue;
    }
  
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes" -> true;
      case "false", "0", "no" -> false;
      default ->
          throw new IllegalArgumentException(
              "Invalid boolean for " + key + ": " + raw + " (use true/false, 1/0, or yes/no)");
    };
  }
}

/*!
 * HITACHI VANTARA PROPRIETARY AND CONFIDENTIAL
 *
 * Copyright 2018 Hitachi Vantara. All rights reserved.
 *
 * NOTICE: All information including source code contained herein is, and
 * remains the sole property of Hitachi Vantara and its licensors. The intellectual
 * and technical concepts contained herein are proprietary and confidential
 * to, and are trade secrets of Hitachi Vantara and may be covered by U.S. and foreign
 * patents, or patents in process, and are protected by trade secret and
 * copyright laws. The receipt or possession of this source code and/or related
 * information does not convey or imply any rights to reproduce, disclose or
 * distribute its contents, or to manufacture, use, or sell anything that it
 * may describe, in whole or in part. Any reproduction, modification, distribution,
 * or public display of this information without the express written authorization
 * from Hitachi Vantara is strictly prohibited and in violation of applicable laws and
 * international treaties. Access to the source code contained herein is strictly
 * prohibited to anyone except those individuals and entities who have executed
 * confidentiality and non-disclosure agreements or other agreements with Hitachi Vantara,
 * explicitly covering such access.
 */

package org.hitachivantara.ci.config

import com.cloudbees.groovy.cps.NonCPS

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Helper Map wrapper to allow getting properties from Environment
 * The environment properties can be overriden in any stage, so given that they are
 * not static it makes sense to just fallback to them when a property isn't found
 * on a higher priority source.
 */
class FilteredMapWithDefault<K, V> extends LinkedHashMap<K, V> {
  private static final Pattern PROPERTY_REPLACE_PATTERN = Pattern.compile(/\$\{(?<key>[\w|_|-]+)\}/)

  def defaults = [:]

  FilteredMapWithDefault(defaults) {
    this.defaults = defaults
  }

  @NonCPS
  V get(Object key) {
    def value = super.get(key)

    // search the defaults if not found
    // Match with null cause we should consider empty string as a valid override
    if (value == null) {
      value = defaults[key as String]
    }
    try {
      return filter(this, value)
    }
    catch (StackOverflowError e){
      throw new InvalidPropertiesFormatException("Cyclic filtering detected while resolving property [$key]")
    }
  }

  def leftShift(Map source) {
    merge(this, source)
    return this
  }

  static void merge(Map self, Map source){
    source.each { key, value ->
      if (value instanceof Map && self[key] instanceof Map) {
        merge(self[key] as Map, value as Map)
      } else {
        self[key] = value
      }
    }
  }

  @NonCPS
  static def filter(Map properties, toFilter){
    if(toFilter && toFilter instanceof String){
      Matcher matcher = PROPERTY_REPLACE_PATTERN.matcher(toFilter)
      StringBuffer sb = new StringBuffer(toFilter.length())

      while (matcher.find()){
        String replacement = filter(properties, properties[matcher.group('key')] ?: '')
        matcher.appendReplacement(sb, replacement)
      }
      matcher.appendTail(sb)

      return sb.toString()
    }

    return toFilter
  }

  private def getAs(Class clazz, String key) {
    def value = get(key)

    if (value == null) return null

    switch (value) {
      case clazz:
        return value
      case String:
      case GString:
        return clazz.valueOf(value)
      default:
        throw new IOException("Value '$value' cannot be read as $clazz")
    }
  }

  Integer getInt(String key) {
    getAs(Integer, key) as Integer ?: 0i
  }

  Double getDouble(String key) {
    getAs(Double, key) as Double ?: 0d
  }

  Boolean getBool(String key) {
    getAs(Boolean, key) as Boolean ?: false
  }

  String getString(String key) {
    get(key) as String ?: ''
  }

  boolean containsKey(Object key) {
    super.containsKey(key) || defaults[key] != null
  }

  boolean containsValue(Object value) {
    throw new UnsupportedOperationException('Operation not supported')
  }

  V remove(Object key) {
    throw new UnsupportedOperationException('Operation not supported')
  }

  void clear() {
    throw new UnsupportedOperationException('Operation not supported')
  }

  Collection<V> values() {
    throw new UnsupportedOperationException('Operation not supported')
  }
}

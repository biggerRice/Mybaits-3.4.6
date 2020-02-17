/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache;

import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

public class CacheKeyTest {

  @Test
  public void shouldTestCacheKeysEqual() {
    Date date = new Date();
    CacheKey key1 = new CacheKey(new Object[] { "15701286390", "123456", 1 });
    CacheKey key2 = new CacheKey(new Object[] { "15701286390", "123456", 1 });
    System.out.println(key1.equals(key2));
    System.out.println(key2.equals(key1));
    System.out.println(key1.hashCode() == key2.hashCode());
    System.out.println(key1.toString().equals(key2.toString()));
  }

  @Test
  public void shouldTestCacheKeysNotEqualDueToDateDifference() throws Exception {
    CacheKey key1 = new CacheKey(new Object[] { 1, "hello", null, new Date() });
    Thread.sleep(1000);
    CacheKey key2 = new CacheKey(new Object[] { 1, "hello", null, new Date() });
    assertFalse(key1.equals(key2));
    assertFalse(key2.equals(key1));
    assertFalse(key1.hashCode() == key2.hashCode());
    assertFalse(key1.toString().equals(key2.toString()));
  }

  @Test
  public void shouldTestCacheKeysNotEqualDueToOrder() throws Exception {
    CacheKey key1 = new CacheKey(new Object[] { 1, "hello", null });
    Thread.sleep(1000);
    CacheKey key2 = new CacheKey(new Object[] { 1, null, "hello" });
    assertFalse(key1.equals(key2));
    assertFalse(key2.equals(key1));
    assertFalse(key1.hashCode() == key2.hashCode());
    assertFalse(key1.toString().equals(key2.toString()));
  }

  @Test
  public void shouldDemonstrateEmptyAndNullKeysAreEqual() {
    CacheKey key1 = new CacheKey();
    CacheKey key2 = new CacheKey();
    assertEquals(key1, key2);
    assertEquals(key2, key1);
    key1.update(null);
    key2.update(null);
    assertEquals(key1, key2);
    assertEquals(key2, key1);
    key1.update(null);
    key2.update(null);
    assertEquals(key1, key2);
    assertEquals(key2, key1);
  }

  @Test
  public void shouldTestCacheKeysWithBinaryArrays() throws Exception {
    byte[] array1 = new byte[] { 1 };
    byte[] array2 = new byte[] { 1 };
    CacheKey key1 = new CacheKey(new Object[] { array1 });
    CacheKey key2 = new CacheKey(new Object[] { array2 });
    assertTrue(key1.equals(key2));
  }

  @Test (expected = NotSerializableException.class)
  public void serializationExceptionTest() throws Exception {
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(new Object());
    serialize(cacheKey);
  }

  @Test
  public void serializationTest() throws Exception {
    CacheKey cacheKey = new CacheKey();
    cacheKey.update("serializable");
    Assert.assertEquals(cacheKey, serialize(cacheKey));
  }

  private static <T> T serialize(T object) throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      new ObjectOutputStream(baos).writeObject(object);

      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      return (T) new ObjectInputStream(bais).readObject();
  }

}
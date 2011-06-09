/**
 * Copyright (c) 2010 Yahoo! Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package com.googlecode.concurrentlinkedhashmap.generator;

/**
 * A generator of a zipfian distribution. It produces a sequence of items, such
 * that some items are more popular than others, according to a zipfian
 * distribution. When you construct an instance of this class, you specify the
 * number of items in the set to draw from, either by specifying an itemcount
 * (so that the sequence is of items from 0 to itemcount-1) or by specifying a
 * min and a max (so that the sequence is of items from min to max inclusive).
 * After you construct the instance, you can change the number of items by
 * calling nextInt(itemcount) or nextLong(itemcount).
 *
 * Unlike @ZipfianGenerator, this class scatters the "popular" items across the
 * itemspace. Use this, instead of @ZipfianGenerator, if you don't want the head
 * of the distribution (the popular items) clustered together.
 *
 * @author Brian Cooper
 * @see <a href="http://research.yahoo.com/Web_Information_Management/YCSB">YCSB</a>
 */
public class ScrambledZipfianGenerator extends IntegerGenerator {
  private static final long FNV_offset_basis_64=0xCBF29CE484222325L;
  private static final long FNV_prime_64=1099511628211L;

  public static final double ZETAN = 26.46902820178302;
  public static final double USED_ZIPFIAN_CONSTANT = 0.99;
  public static final long ITEM_COUNT = 10000000000L;

  ZipfianGenerator gen;
  long _min, _max, _itemcount;

  /******************************* Constructors **************************************/

  /**
   * Create a zipfian generator for the specified number of items.
   *
   * @param _items The number of items in the distribution.
   */
  public ScrambledZipfianGenerator(long _items) {
    this(0, _items - 1);
  }

  /**
   * Create a zipfian generator for items between min and max.
   *
   * @param _min The smallest integer to generate in the sequence.
   * @param _max The largest integer to generate in the sequence.
   */
  public ScrambledZipfianGenerator(long _min, long _max) {
    this(_min, _max, ZipfianGenerator.ZIPFIAN_CONSTANT);
  }

  /**
   * Create a zipfian generator for the specified number of items using the
   * specified zipfian constant.
   *
   * @param _items The number of items in the distribution.
   * @param _zipfianconstant The zipfian constant to use.
   */
   // not supported, as the value of zeta depends on the zipfian constant, and
   // we have only precomputed zeta for one zipfian constant
   //public ScrambledZipfianGenerator(long _items, double _zipfianconstant) {
   //  this(0,_items-1,_zipfianconstant);
   //}

  /**
   * Create a zipfian generator for items between min and max (inclusive) for
   * the specified zipfian constant. If you use a zipfian constant other than
   * 0.99, this will take a long time to complete because we need to recompute
   * zeta.
   *
   * @param min The smallest integer to generate in the sequence.
   * @param max The largest integer to generate in the sequence.
   * @param _zipfianconstant The zipfian constant to use.
   */
  public ScrambledZipfianGenerator(long min, long max, double _zipfianconstant) {
    _min = min;
    _max = max;
    _itemcount = _max - _min + 1;
    if (_zipfianconstant == USED_ZIPFIAN_CONSTANT) {
      gen = new ZipfianGenerator(0, ITEM_COUNT, _zipfianconstant, ZETAN);
    } else {
      gen = new ZipfianGenerator(0, ITEM_COUNT, _zipfianconstant);
    }
  }

  /**************************************************************************************************/

  /**
   * Return the next int in the sequence.
   */
  @Override
  public int nextInt() {
    return (int) nextLong();
  }

  /**
   * Return the next long in the sequence.
   */
  public long nextLong() {
    long ret = gen.nextLong();
    ret = _min + FNVhash64(ret) % _itemcount;
    setLastInt((int) ret);
    return ret;
  }

  /**
   * 64 bit FNV hash. Produces more "random" hashes than (say)
   * String.hashCode().
   *
   * @param val The value to hash.
   * @return The hash value
   */
  private static long FNVhash64(long val) {
    // from http://en.wikipedia.org/wiki/Fowler_Noll_Vo_hash
    long hashval = FNV_offset_basis_64;

    for (int i = 0; i < 8; i++) {
      long octet = val & 0x00ff;
      val = val >> 8;

      hashval = hashval ^ octet;
      hashval = hashval * FNV_prime_64;
      // hashval = hashval ^ octet;
    }
    return Math.abs(hashval);
  }

  public static void main(String[] args) {
    double newzetan = ZipfianGenerator.zetastatic(ITEM_COUNT, ZipfianGenerator.ZIPFIAN_CONSTANT);
    System.out.println("zetan: " + newzetan);
    System.exit(0);

    ScrambledZipfianGenerator gen = new ScrambledZipfianGenerator(10000);

    for (int i = 0; i < 1000000; i++) {
      System.out.println("" + gen.nextInt());
    }
  }
}

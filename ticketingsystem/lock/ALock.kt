/*
 * ALock.java
 *
 * Created on January 20, 2006, 11:02 PM
 *
 * From "Multiprocessor Synchronization and Concurrent Data Structures",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */
package ticketingsystem.lock

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

/**
 * Anderson lock
 * @author Maurice Herlihy
 */
class ALock(var size: Int) : Lock {
  // thread-local variable
  var mySlotIndex: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }
  var tail: AtomicInteger
  var flag: BooleanArray

  /**
   * Constructor
   * @param capacity max number of array slots
   */
  init {
    tail = AtomicInteger(0)
    flag = BooleanArray(size)
    flag[0] = true
  }

  override fun lock() {
    val slot = tail.getAndIncrement() % size
    mySlotIndex.set(slot)
    while (!flag[mySlotIndex.get()]) {
    }
    // spin
  }

  override fun unlock() {
    flag[mySlotIndex.get()] = false
    flag[(mySlotIndex.get() + 1) % size] = true
  }

  // any class implementing Lock must provide these methods
  override fun newCondition(): Condition {
    throw UnsupportedOperationException()
  }

  @Throws(InterruptedException::class)
  override fun tryLock(
    time: Long,
    unit: TimeUnit
  ): Boolean {
    throw UnsupportedOperationException()
  }

  override fun tryLock(): Boolean {
    throw UnsupportedOperationException()
  }

  @Throws(InterruptedException::class)
  override fun lockInterruptibly() {
    throw UnsupportedOperationException()
  }
}
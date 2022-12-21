/*
 * TTASLock.java
 *
 * Created on January 20, 2006, 10:59 PM
 *
 * From "Multiprocessor Synchronization and Concurrent Data Structures",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */
package ticketingsystem.lock

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

/**
 * Test-and-test-and-set lock
 * @author Maurice Herlihy
 */
class TTASLock : Lock {
  var state = AtomicBoolean(false)
  override fun lock() {
    while (true) {
      while (state.get()) {
      }
      // spin
      if (!state.getAndSet(true)) return
    }
  }

  override fun unlock() {
    state.set(false)
  }

  // Any class that implents Lock must provide these methods.
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
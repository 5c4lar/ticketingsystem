/*
 * CompositeLock.java
 *
 * Created on April 11, 2006, 9:12 PM
 *
 * From "Multiprocessor Synchronization and Concurrent Data Structures",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */
package ticketingsystem.lock

import java.util.concurrent.TimeUnit

/**
 * Composite Abortable Lock, now with Fast Path!
 * @author Maurice Herlihy
 */
class CompositeFastPathLock : CompositeLock() {
  var fastPathTaken = 0
  @Throws(InterruptedException::class)
  override fun tryLock(time: Long, unit: TimeUnit): Boolean {
    if (fastPathLock()) {
      fastPathTaken++
      return true
    }
    if (super.tryLock(time, unit)) {
      fastPathWait()
      return true
    }
    return false
  }

  override fun unlock() {
    if (!fastPathUnlock()) {
      super.unlock()
    }
  }

  private fun fastPathLock(): Boolean {
    val oldStamp: Int
    val newStamp: Int
    val stamp = intArrayOf(0)
    val qnode: QNode?
    qnode = tail[stamp]
    oldStamp = stamp[0]
    if (qnode != null) {
      return false
    }
    if (oldStamp and FASTPATH != 0) {
      return false
    }
    newStamp = oldStamp + 1 or FASTPATH // set flag
    return tail.compareAndSet(qnode, null, oldStamp, newStamp)
  }

  private fun fastPathUnlock(): Boolean {
    var oldStamp: Int
    var newStamp: Int
    oldStamp = tail.stamp
    if (oldStamp and FASTPATH == 0) {
      return false
    }
    val stamp = intArrayOf(0)
    var qnode: QNode?
    do {
      qnode = tail[stamp]
      oldStamp = stamp[0]
      newStamp = oldStamp and FASTPATH.inv() // unset flag
    } while (!tail.compareAndSet(qnode, qnode, oldStamp, newStamp))
    return true
  }

  private fun fastPathWait() {
    while (tail.stamp and FASTPATH != 0) {
    } // spin while flag is set
  }

  companion object {
    private const val FASTPATH = 1 shl 30
  }
}
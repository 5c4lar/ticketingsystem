/*
 * TOLock.java
 *
 * Created on January 21, 2006, 12:04 AM
 *
 * From "Multiprocessor Synchronization and Concurrent Data Structures",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */
package ticketingsystem.lock

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

/**
 * Scott Time-out Lock
 * @author Maurice Herlihy
 */
class TOLock : Lock {
  var tail: AtomicReference<QNode?> = AtomicReference(null)
  var myNode: ThreadLocal<QNode> = ThreadLocal.withInitial { QNode() }

  @Throws(InterruptedException::class)
  override fun tryLock(time: Long, unit: TimeUnit): Boolean {
    val startTime = System.nanoTime()
    val patience = TimeUnit.NANOSECONDS.convert(time, unit)
    val qnode = QNode()
    myNode.set(qnode) // remember for unlock
    qnode.pred = null
    var pred = tail.getAndSet(qnode)
    if (pred == null || pred.pred === AVAILABLE) {
      return true // lock was free; just return
    }
    while (System.nanoTime() - startTime < patience) {
      val predPred = pred!!.pred
      if (predPred === AVAILABLE) {
        return true
      } else if (predPred != null) {  // skip predecessors
        pred = predPred
      }
    }
    // timed out; reclaim or abandon own node
    if (!tail.compareAndSet(qnode, pred)) qnode.pred = pred
    return false
  }

  override fun unlock() {
    val qnode = myNode.get()
    if (!tail.compareAndSet(qnode, null)) qnode.pred = AVAILABLE
  }

  // any class that implements lock must provide these methods
  override fun lock() {
    try {
      tryLock(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    } catch (ex: InterruptedException) {
      ex.printStackTrace()
    }
  }

  override fun newCondition(): Condition {
    throw UnsupportedOperationException()
  }

  override fun tryLock(): Boolean {
    return try {
      tryLock(0, TimeUnit.NANOSECONDS)
    } catch (ex: InterruptedException) {
      false
    }
  }

  @Throws(InterruptedException::class)
  override fun lockInterruptibly() {
    throw UnsupportedOperationException()
  }

  class QNode {
    // Queue node inner class
    @Volatile
    var pred: QNode? = null
  }

  companion object {
    var AVAILABLE = QNode()
  }
}
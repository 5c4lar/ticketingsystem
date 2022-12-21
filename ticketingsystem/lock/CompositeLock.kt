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

import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicStampedReference
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

/**
 * CompositeLock Abortable Lock
 * @author Maurice Herlihy
 */
open class CompositeLock : Lock {
  var tail: AtomicStampedReference<QNode?>
  var waiting: Array<QNode?>
  var random: Random
  var myNode: ThreadLocal<QNode?> = ThreadLocal.withInitial { null }

  /**
   * Creates a new instance of CompositeLock
   */
  init {
    tail = AtomicStampedReference(null, 0)
    random = Random()
    waiting = arrayOfNulls(SIZE)
    for (i in waiting.indices) {
      waiting[i] = QNode()
    }
  }

  override fun lock() {
    try {
      tryLock(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
    } catch (ex: InterruptedException) {
      ex.printStackTrace()
    }
  }

  @Throws(InterruptedException::class)
  override fun lockInterruptibly() {
    throw UnsupportedOperationException()
  }

  override fun tryLock(): Boolean {
    return try {
      tryLock(0, TimeUnit.MILLISECONDS)
    } catch (ex: InterruptedException) {
      false
    }
  }

  @Throws(InterruptedException::class)
  override fun tryLock(time: Long, unit: TimeUnit): Boolean {
    val patience = TimeUnit.MILLISECONDS.convert(time, unit)
    val startTime = System.currentTimeMillis()
    val backoff = Backoff(MIN_BACKOFF, MAX_BACKOFF)
    return try {
      val node = acquireQNode(backoff, startTime, patience)
      val pred = spliceQNode(node, startTime, patience)
      waitForPredecessor(pred, node, startTime, patience)
      true
    } catch (e: TimeoutException) {
      false
    }
  }

  override fun unlock() {
    val acqNode = myNode.get()
    acqNode!!.state.set(State.RELEASED)
  }

  override fun newCondition(): Condition {
    throw UnsupportedOperationException()
  }

  private fun timeout(startTime: Long, patience: Long): Boolean {
    return System.currentTimeMillis() - startTime > patience
  }

  @Throws(TimeoutException::class, InterruptedException::class)
  private fun acquireQNode(backoff: Backoff, startTime: Long, patience: Long): QNode? {
    val node = waiting[random.nextInt(SIZE)]
    var currTail: QNode?
    val currStamp = intArrayOf(0)
    while (true) {
      if (node!!.state.compareAndSet(State.FREE, State.WAITING)) {
        return node
      }
      currTail = tail[currStamp]
      val state = node.state.get()
      if (state == State.ABORTED || state == State.RELEASED) {
        if (node === currTail) {
          var myPred: QNode? = null
          if (state == State.ABORTED) {
            myPred = node.pred
          }
          if (tail.compareAndSet(
              currTail, myPred,
              currStamp[0], currStamp[0] + 1
            )
          ) {
            node.state.set(State.WAITING)
            return node
          }
        }
      }
      backoff.backoff()
      if (timeout(patience, startTime)) {
        throw TimeoutException()
      }
    }
  }

  @Throws(TimeoutException::class)
  private fun spliceQNode(node: QNode?, startTime: Long, patience: Long): QNode? {
    var currTail: QNode?
    val currStamp = intArrayOf(0)
    // splice node into queue
    do {
      currTail = tail[currStamp]
      if (timeout(startTime, patience)) {
        node!!.state.set(State.FREE)
        throw TimeoutException()
      }
    } while (!tail.compareAndSet(
        currTail, node,
        currStamp[0], currStamp[0] + 1
      )
    )
    return currTail
  }

  @Throws(TimeoutException::class)
  private fun waitForPredecessor(predNode: QNode?, node: QNode?, startTime: Long, patience: Long) {
    // wait for predecessor to release lock
    var pred = predNode
    // val stamp = intArrayOf(0)
    if (pred == null) {
      myNode.set(node)
      return
    }
    var predState = pred.state.get()
    while (predState != State.RELEASED) {
      if (predState == State.ABORTED) {
        val temp = pred
        pred = pred!!.pred
        temp!!.state.set(State.FREE)
      }
      if (timeout(patience, startTime)) {
        node!!.pred = pred
        node.state.set(State.ABORTED)
        throw TimeoutException()
      }
      predState = pred!!.state.get()
    }
    pred!!.state.set(State.FREE)
    myNode.set(node)
    return
  }

  /*
   * Internal classes
   */
  enum class State {
    FREE, WAITING, RELEASED, ABORTED
  }

  inner class QNode {
    @Volatile
    var state: AtomicReference<State> = AtomicReference(State.FREE)
    @Volatile
    var pred: QNode? = null

  }

  companion object {
    private const val SIZE = 4
    private const val MIN_BACKOFF = 1
    private const val MAX_BACKOFF = 256 * MIN_BACKOFF
  }
}
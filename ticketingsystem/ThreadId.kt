package ticketingsystem

import java.util.concurrent.atomic.AtomicInteger

object ThreadId {
  // Atomic integer containing the next thread ID to be assigned
  private val nextId = AtomicInteger(0)

  // Thread local variable containing each thread's ID
  private val threadId: ThreadLocal<Int> = object : ThreadLocal<Int>() {
    override fun initialValue(): Int {
      return nextId.getAndIncrement()
    }
  }

  // Returns the current thread's unique ID, assigning it if necessary
  fun get(): Int {
    return threadId.get()
  }

  fun reset() {
    nextId.set(0)
  }
}
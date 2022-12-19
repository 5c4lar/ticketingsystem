package ticketingsystem

import ticketingsystem.TicketingDS.ActionEnum.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock

class TicketingDS(routenum: Int, val coachnum: Int, val seatnum: Int, val stationnum: Int, val threadnum: Int) :
  TicketingSystem {

  private val tid = AtomicLong(0)
  private val tickets = ConcurrentHashMap<Long, Ticket>()

  enum class ActionEnum {
    BUY, REFUND, QUERY, TRANSFER
  }

  data class Action(
    val action: ActionEnum, val start: Int, val end: Int, val threadId: Int, val versionId: Int,
  )

  enum class PHASE {
    UPDATING, QUERYING, IDLE
  }

//  class BoundedQueue<T>(capacity: Int) {
//    private val container = Array<Any?>(capacity + 1) { null }
//    private var head = AtomicInteger(0)
//    private var tail = AtomicInteger(0)
//
//    val size: Int
//      get() {
//        val h = head.get()
//        val t = tail.get()
//        return if (h <= t) t - h else container.size - h + t
//      }
//    fun add(element: T) {
//      container[tail.getAndIncrement() % container.size] = element
//    }
//
//    fun poll(): T? {
//      val element = container[head.getAndIncrement() % container.size]
//      return element as T
//    }
//
//    fun peek(): T? {
//      val element = container[head.get() % container.size]
//      return element as T
//    }
//  }

  inner class Route(private val rid: Int) {
    inner class StateManager() {

      private val capacity = threadnum + 1
      private val currentVersion = AtomicInteger(0)
      private val newestVersion = AtomicInteger(0)
      private val versionLock = ReentrantReadWriteLock()
      private val states: Array<UInt> = Array(stationnum * (stationnum - 1) / 2) { (coachnum * seatnum).toUInt() }
      private val threadResBuffer = Array(threadnum) { 0u }
      private val workList: Array<ConcurrentLinkedQueue<Action>> =
        Array(capacity) { ConcurrentLinkedQueue() }
      private val queryList: Array<ConcurrentLinkedQueue<Action>> = Array(capacity) { ConcurrentLinkedQueue() }
      private val queryListLock: Array<ReentrantReadWriteLock> = Array(capacity) { ReentrantReadWriteLock() }
      private val updateLatches: Array<CountDownLatch?> = Array(capacity) { null }
      private val queryLatches: Array<CountDownLatch?> = Array(capacity) { null }
      private val phases: Array<AtomicInteger> = Array(capacity) { AtomicInteger(PHASE.IDLE.ordinal) }
      private val queryListFlag: Array<AtomicBoolean> = Array(capacity) { AtomicBoolean(false) }
      private val mask = (1u shl 31) - 1u

      init {
        phases[1 % capacity].set(PHASE.UPDATING.ordinal)
      }

      fun update(buy: Boolean, prevStart: Int, start: Int, end: Int, nextEnd: Int) {
        val versionId = newestVersion.incrementAndGet()
        val list = workList[versionId % capacity]
        var count = 0
        for (i in prevStart until end) {
          for (j in maxOf(i, start) + 1..nextEnd) {
            count++
          }
        }
        updateLatches[versionId % capacity] = CountDownLatch(count)
        val action = if (buy) BUY else REFUND
        for (i in prevStart until end) {
          for (j in maxOf(i, start) + 1..nextEnd) {
            list.add(Action(action, i, j, 0, versionId))
          }
        }
        list.add(Action(TRANSFER, 0, 0, threadId.get(), versionId))
        takeAction(versionId)
      }

      fun query(departure: Int, arrival: Int): UInt {
        var queryVersion: Int
        while (true) {

          versionLock.readLock().lock()
          try {
            var versionId = newestVersion.get()
            if (versionId == currentVersion.get()) { // currentVersion <= newestVersion
              return states[intervalToId(departure, arrival)]
            }
            val queryLock = queryListLock[versionId % capacity]
            if (queryLock.readLock().tryLock()) {
              try {
                if (queryListFlag[versionId % capacity].get()) {
                  continue
                } else {
                  val queryAction = Action(QUERY, departure, arrival, threadId.get(), versionId)
                  val list = queryList[versionId % capacity]
                  list.add(queryAction)
                  queryVersion = versionId
                  break
                }
              } finally {
                queryLock.readLock().unlock()
              }
            } else {
              continue
            }
          } finally {
            versionLock.readLock().unlock()
          }

        }
        takeAction(queryVersion)
        val currentRes = threadResBuffer[threadId.get()]
        threadResBuffer[threadId.get()] = 0u
        return currentRes and mask
      }

      private fun stepUpdate(workVersion: Int) {
        val list = workList[workVersion % capacity]
        val action = list.poll() ?: return
        when (action.action) {
          BUY -> {
            states[intervalToId(action.start, action.end)]--
            updateLatches[workVersion % capacity]!!.countDown()
          }

          REFUND -> {
            states[intervalToId(action.start, action.end)]++
            updateLatches[workVersion % capacity]!!.countDown()
          }

          TRANSFER -> {
            updateLatches[workVersion % capacity]!!.await()
            val queryLock = queryListLock[workVersion % capacity]
            queryLock.writeLock().lock()
            try {
              queryListFlag[workVersion % capacity].set(true)
              val queryList = queryList[workVersion % capacity]
              queryLatches[workVersion % capacity] = CountDownLatch(queryList.size)
              queryList.add(Action(TRANSFER, 0, 0, threadId.get(), workVersion))
            } finally {
              queryLock.writeLock().unlock()
            }
            phases[workVersion % capacity].set(PHASE.QUERYING.ordinal)
          }

          else -> {
            throw Exception("Invalid action")
          }
        }
      }

      private fun stepQuery(workVersion: Int) {
        val list = queryList[workVersion % capacity]
        val action = list.poll() ?: return
        when (action.action) {
          QUERY -> {
            val res = states[intervalToId(action.start, action.end)]
            threadResBuffer[action.threadId] = res or mask.inv()
            queryLatches[workVersion % capacity]!!.countDown()
          }

          TRANSFER -> {
            queryLatches[workVersion % capacity]!!.await()
            versionLock.writeLock().lock()
            try {
              currentVersion.incrementAndGet()
              queryListFlag[workVersion % capacity].set(false)
              phases[workVersion % capacity].set(PHASE.IDLE.ordinal)
              phases[(workVersion + 1) % capacity].set(PHASE.UPDATING.ordinal)
            } finally {
              versionLock.writeLock().unlock()
            }
          }

          else -> {
            throw Exception("Invalid action")
          }
        }
      }

      private fun takeAction(targetVersion: Int) {
        while (true) {
          val currentCopy = currentVersion.get()
          if (currentCopy >= targetVersion && phases[currentCopy % capacity].get() == PHASE.IDLE.ordinal) {
            return
          }
          val workVersion = currentCopy + 1
          when (phases[workVersion % capacity].get()) {
            PHASE.UPDATING.ordinal -> {
              stepUpdate(workVersion)
            }

            PHASE.QUERYING.ordinal -> {
              stepQuery(workVersion)
            }

            PHASE.IDLE.ordinal -> {
              continue
            }
          }
        }
      }
    }

    private val stateManager = StateManager()
    private val intervals = Array((stationnum * (stationnum - 1) / 2)) { ConcurrentLinkedQueue<Seat>() }
    private val seats = Array(coachnum) { cid -> Array(seatnum) { sid -> Seat(cid + 1, sid + 1) } }
    private val threadId = ThreadLocal.withInitial { ThreadId.get() }

    init {
      for (cid in 0 until coachnum) {
        for (sid in 0 until seatnum) {
          intervals[intervalToId(0, stationnum - 1)].add(seats[cid][sid])
        }
      }
    }

    private fun intervalToId(start: Int, end: Int): Int {
      return start * stationnum - start * (start + 1) / 2 + end - start - 1
    }

    inner class Seat(val cid: Int, val sid: Int) : Comparable<Seat> {
      private val status: AtomicLong = AtomicLong((1L shl stationnum - 1) - 1)
      private val busy: AtomicBoolean = AtomicBoolean(false)

      fun aquire() {
        while (!busy.compareAndSet(false, true)) {
          Thread.yield()
        }
      }

      fun tryAquire(): Boolean {
        return busy.compareAndSet(false, true)
      }

      fun release() {
        busy.set(false)
      }

      fun containsInterval(start: Int, end: Int): Boolean {
        var mask = (1L shl end) - (1L shl start)
        return status.get() and mask == mask
      }

      fun markInterval(start: Int, end: Int): Boolean {
        var mask = (1L shl end) - (1L shl start)
        while (true) {
          val num = status.get()
          if (num and mask.inv() != num) return false
          val num2 = num or mask
          if (num == num2 || status.compareAndSet(num, num2)) return true
        }
      }

      fun unmarkInterval(start: Int, end: Int): Boolean {
        var mask = (1L shl end) - (1L shl start)
        while (true) {
          val num = status.get()
          if (num and mask != mask) return false
          val num2 = num and mask.inv()
          if (num == num2 || status.compareAndSet(num, num2)) return true
        }
      }

      override fun compareTo(other: Seat): Int {
        return if (cid != other.cid) cid - other.cid else sid - other.sid
      }
    }

    fun inquiry(departure: Int, arrival: Int): Int {
      return stateManager.query(departure, arrival).toInt()
    }

    fun buyTicket(passenger: String?, departure: Int, arrival: Int): Ticket? {
      for (i in departure downTo 0) {
        for (j in arrival until stationnum) {
          val id = intervalToId(i, j)
          while (true) {
            val seat = intervals[id].peek() ?: break
            if (seat.tryAquire()) {
              try {
                intervals[id].poll()
                if (!seat.unmarkInterval(departure, arrival)) {
                  continue
                }
                if (i < departure) {
                  intervals[intervalToId(i, departure)].add(seat)
                }
                if (j > arrival) {
                  intervals[intervalToId(arrival, j)].add(seat)
                }
              } finally {
                seat.release()
              }
            } else {
              continue
            }
            stateManager.update(true, i, departure, arrival, j)
            val ticket = Ticket()
            val cid = seat.cid
            val sid = seat.sid
            ticket.setFields(tid.incrementAndGet(), rid, cid, sid, departure + 1, arrival + 1, passenger)
            return ticket
          }
        }
      }
      return null
    }

    fun refundTicket(ticket: Ticket?): Boolean {
      if (ticket == null) return false
      val cid = ticket.coach
      val sid = ticket.seat
      val departure = ticket.departure - 1
      val arrival = ticket.arrival - 1
      var prevStart = departure
      var nextEnd = arrival
      val seat = seats[cid - 1][sid - 1]
      seat.aquire()
      try {
        if (!seat.markInterval(departure, arrival)) return false
        for (i in (departure - 1) downTo 0) {
          if (seat.containsInterval(i, departure)) {
            if (intervals[intervalToId(i, departure)].remove(seat)) {
              prevStart = i
            }
          } else {
            break
          }
        }
        for (i in (arrival + 1) until stationnum) {
          if (seat.containsInterval(arrival, i)) {
            if (intervals[intervalToId(arrival, i)].remove(seat)) {
              nextEnd = i
            }
          } else {
            break
          }
        }
        intervals[intervalToId(prevStart, nextEnd)].add(seat)
      } finally {
        seat.release()
      }
      stateManager.update(false, prevStart, departure, arrival, nextEnd)
      return true
    }
  }

  private val routes = Array(routenum) { Route(it + 1) }

  private fun Ticket.setFields(t: Long, r: Int, c: Int, s: Int, d: Int, a: Int, p: String?) {
    this.tid = t
    this.route = r
    this.coach = c
    this.seat = s
    this.departure = d
    this.arrival = a
    this.passenger = p
  }

  /**
   * @param passenger
   * @param route
   * @param departure
   * @param arrival
   * @return
   */
  override fun buyTicket(passenger: String?, route: Int, departure: Int, arrival: Int): Ticket? {
    if (departure >= arrival) return null
    val ticket = routes[route - 1].buyTicket(passenger, departure - 1, arrival - 1)
    if (ticket != null) {
      tickets[ticket.tid] = ticket
    }
    return ticket
  }

  /**
   * @param route
   * @param departure
   * @param arrival
   * @return
   */
  override fun inquiry(route: Int, departure: Int, arrival: Int): Int {
    val r = routes[route - 1]
    return r.inquiry(departure - 1, arrival - 1)
  }

  /**
   * @param ticket
   * @return
   */
  override fun refundTicket(ticket: Ticket?): Boolean {
    return if (tickets.remove(ticket?.tid) != null) {
      routes[ticket!!.route - 1].refundTicket(ticket)
    } else {
      false
    }
  }

  /**
   * @param ticket
   * @return
   */
  override fun buyTicketReplay(ticket: Ticket?): Boolean {
    return false
  }

  /**
   * @param ticket
   * @return
   */
  override fun refundTicketReplay(ticket: Ticket?): Boolean {
    return false
  }
}
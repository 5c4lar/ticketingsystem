package ticketingsystem

import ticketingsystem.lock.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Lock

class TicketingDS(routenum: Int, val coachnum: Int, val seatnum: Int, val stationnum: Int, val threadnum: Int) :
  TicketingSystem {

  private val tid = AtomicLong(0)
  private val tickets = ConcurrentHashMap<Long, Ticket>()

  inner class Route(private val rid: Int) {
    private val seatStates = Array(stationnum * (stationnum - 1) / 2) { coachnum * seatnum }
    private val intervals = Array((stationnum * (stationnum - 1) / 2)) { ConcurrentLinkedQueue<Seat>() }
    private val seats = Array(coachnum) { cid -> Array(seatnum) { sid -> Seat(cid + 1, sid + 1) } }
    private val version = AtomicInteger(0)
    private val currentVersion = AtomicInteger(0)

    init {
      // add seats to intervals in random order
      val random = java.util.Random()
      random.setSeed(0)
      for (i in 0 until (stationnum * (stationnum - 1) / 2)) {
        val interval = intervals[i]
        for (seat in seats.flatten().shuffled(random)) {
          interval.add(seat)
        }
      }
    }

    private fun updateBuy(prevStart: Int, start: Int, end: Int, nextEnd: Int, stamp: Int) {
      while (currentVersion.get() != stamp - 1) {
//        Thread.yield()
      }
      for (i in prevStart until end) {
        for (j in maxOf(i, start) + 1..nextEnd) {
          seatStates[intervalToId(i, j)]--
        }
      }
      currentVersion.set(stamp)
    }

    private fun updateRefund(prevStart: Int, start: Int, end: Int, nextEnd: Int, stamp: Int) {
      while (currentVersion.get() != stamp - 1) {
//        Thread.yield()
      }
      for (i in prevStart until end) {
        for (j in maxOf(i, start) + 1..nextEnd) {
          seatStates[intervalToId(i, j)]++
        }
      }
      currentVersion.set(stamp)
    }

    private fun intervalToId(start: Int, end: Int): Int {
      return start * stationnum - start * (start + 1) / 2 + end - start - 1
    }

    inner class Seat(val cid: Int, val sid: Int) : Comparable<Seat>, Lock by TASLock() {
      private val status: AtomicLong = AtomicLong((1L shl stationnum - 1) - 1)

      fun markInterval(start: Int, end: Int): Boolean {
        val mask = (1L shl end) - (1L shl start)
        while (true) {
          val num = status.get()
          if (num and mask.inv() != num) return false
          val num2 = num or mask
          if (num == num2 || status.compareAndSet(num, num2)) return true
        }
      }

      fun unmarkInterval(start: Int, end: Int): Boolean {
        val mask = (1L shl end) - (1L shl start)
        while (true) {
          val num = status.get()
          if (num and mask != mask) return false
          val num2 = num and mask.inv()
          if (num == num2 || status.compareAndSet(num, num2)) return true
        }
      }

      fun getOuterInterval(start: Int, end: Int): Pair<Int, Int> {
        val num = status.get()
        var prevStart = start
        var nextEnd = end
        while (prevStart > 0) {
          if ((num and (1L shl (prevStart - 1))) == 0L) break
          prevStart--
        }
        while (nextEnd < (stationnum - 1)) {
          if ((num and (1L shl nextEnd)) == 0L) break
          nextEnd++
        }
        return Pair(prevStart, nextEnd)
      }

      override fun compareTo(other: Seat): Int {
        return if (cid != other.cid) cid - other.cid else sid - other.sid
      }
    }

    fun inquiry(departure: Int, arrival: Int): Int {
      val targetStamp = version.get()
      while (true) {
        val stamp = currentVersion.get()
        if (stamp >= targetStamp) {
          return seatStates[intervalToId(departure, arrival)]
        }
//        Thread.yield()
      }
    }

    fun buyTicket(passenger: String?, departure: Int, arrival: Int): Ticket? {
      val stamp: Int
      val id = intervalToId(departure, arrival)
      val prevStart: Int
      val nextEnd: Int
      var seat: Seat
      while (true) {
        seat = intervals[id].poll() ?: return null
        seat.lock()
        try {
          if (seat.unmarkInterval(departure, arrival)) {
            val outer = seat.getOuterInterval(departure, arrival)
            prevStart = outer.first
            nextEnd = outer.second
            stamp = version.incrementAndGet()
            break
          } else {
            continue
          }
        } finally {
          seat.unlock()
        }
      }
      updateBuy(prevStart, departure, arrival, nextEnd, stamp)
      val cid = seat.cid
      val sid = seat.sid
      return Ticket(tid.incrementAndGet(), rid, cid, sid, departure + 1, arrival + 1, passenger)
    }

    private fun refundIntervals(prevStart: Int, start: Int, end: Int, nextEnd: Int, seat: Seat) {
      for (i in prevStart until end) {
        for (j in maxOf(i, start) + 1..nextEnd) {
          val interval = intervals[intervalToId(i, j)]
          interval.add(seat)
        }
      }
    }

    fun refundTicket(ticket: Ticket?): Boolean {
      if (ticket == null) return false
      val cid = ticket.coach
      val sid = ticket.seat
      val departure = ticket.departure - 1
      val arrival = ticket.arrival - 1
      val prevStart: Int
      val nextEnd: Int
      val seat = seats[cid - 1][sid - 1]
      val stamp: Int
      seat.lock()
      try {
        if (!seat.markInterval(departure, arrival)) return false
        val outer = seat.getOuterInterval(departure, arrival)
        prevStart = outer.first
        nextEnd = outer.second
        refundIntervals(prevStart, departure, arrival, nextEnd, seat)
        stamp = version.incrementAndGet()
      } finally {
        seat.unlock()
      }
      updateRefund(prevStart, departure, arrival, nextEnd, stamp)
      return true
    }
  }

  private val routes = Array(routenum) { Route(it + 1) }

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
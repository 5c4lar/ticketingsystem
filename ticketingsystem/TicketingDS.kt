package ticketingsystem

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
      for (cid in 0 until coachnum) {
        for (sid in 0 until seatnum) {
          intervals[intervalToId(0, stationnum - 1)].add(seats[cid][sid])
        }
      }
    }

    private fun updateBuy(prevStart: Int, start: Int, end: Int, nextEnd: Int) {
      val stamp = version.incrementAndGet()
      while (currentVersion.get() != stamp - 1) {
//        Thread.yield()
      }
      for (i in 0 until stationnum) {
        for (j in i + 1 until stationnum) {
          if (i in prevStart until end && j in maxOf(i, start) + 1..nextEnd) {
            seatStates[intervalToId(i, j)] -= 1
          }
        }
      }
      currentVersion.set(stamp)
    }

    private fun updateRefund(prevStart: Int, start: Int, end: Int, nextEnd: Int) {
      val stamp = version.incrementAndGet()
      while (currentVersion.get() != stamp - 1) {
//        Thread.yield()
      }
      for (i in 0 until stationnum) {
        for (j in i + 1 until stationnum) {
          if (i in prevStart until end && j in maxOf(i, start) + 1..nextEnd) {
            seatStates[intervalToId(i, j)] += 1
          }
        }
      }
      currentVersion.set(stamp)
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
            updateBuy(i, departure, arrival, j)
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
      updateRefund(prevStart, departure, arrival, nextEnd)
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
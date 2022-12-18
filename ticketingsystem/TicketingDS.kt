package ticketingsystem

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class TicketingDS(routenum: Int, val coachnum: Int, val seatnum: Int, val stationnum: Int, val threadnum: Int) :
  TicketingSystem {

  private val tid = AtomicLong(0)
  private val tickets = ConcurrentHashMap<Long, Ticket>()

  data class Edit(val start: Int, val end: Int, val update: Int)
  data class EditGroup(val edits: List<Edit>)

  inner class Route(private val rid: Int) {

    private val seatNums = Array((stationnum * (stationnum - 1) / 2)) { 0 }
    private val intervals: Array<ConcurrentLinkedDeque<Seat>> =
      Array((stationnum * (stationnum - 1) / 2)) { ConcurrentLinkedDeque() }
    private val seats = Array(coachnum) { cid -> Array(seatnum) { sid -> Seat(cid + 1, sid + 1) } }
    // private val pendingUpdates = ConcurrentLinkedQueue<EditGroup>()
    private val updateLock = ReentrantReadWriteLock()

    init {
      seatNums[intervalToId(0, stationnum - 1)] = coachnum * seatnum
      for (cid in 0 until coachnum) {
        for (sid in 0 until seatnum) {
          intervals[intervalToId(0, stationnum - 1)].add(seats[cid][sid])
        }
      }
    }

    private fun intervalToId(start: Int, end: Int): Int {
      return start * stationnum - start * (start + 1) / 2 + end - start - 1
    }

    private fun updateNums(edits: EditGroup) {
      updateLock.writeLock().withLock {
        for (edit in edits.edits) {
          val id = intervalToId(edit.start, edit.end)
          seatNums[id] += edit.update
        }
      }
    }

    inner class Seat(val cid: Int, val sid: Int) : Comparable<Seat> {
      private val status: AtomicLong = AtomicLong((1L shl stationnum - 1) - 1)
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
      updateLock.readLock().withLock {
        // val edits = pendingUpdates.toArray()
        // for (item in edits) {
        //   val edit = item as EditGroup
        //   pendingUpdates.poll()
        //   for (e in edit.edits) {
        //     seatNums[intervalToId(e.start, e.end)] += e.update
        //   }
        // }
        var sum = 0
        for (i in 0..departure) {
          for (j in arrival until stationnum) {
            val id = intervalToId(i, j)
            sum += seatNums[id]
          }
        }
        return sum
      }
    }

    fun buyTicket(passenger: String?, departure: Int, arrival: Int): Ticket? {
      for (i in departure downTo 0) {
        for (j in arrival until stationnum) {
          val id = intervalToId(i, j)
          while (true) {
            val seat = intervals[id].pollFirst() ?: break
            if (!seat.unmarkInterval(departure, arrival)) {
              continue
            }
            val edits = mutableListOf<Edit>()
            edits.add(Edit(i, j, -1))
            if (i < departure) {
              edits.add(Edit(i, departure, 1))

            }
            if (j > arrival) {
              edits.add(Edit(arrival, j, 1))
            }
            updateNums(EditGroup(edits))
            if (i < departure) {
              intervals[intervalToId(i, departure)].add(seat)
            }
            if (j > arrival) {
              intervals[intervalToId(arrival, j)].add(seat)
            }
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
      val seat = seats[cid - 1][sid - 1]
      if (!seat.markInterval(departure, arrival)) return false
      val edits = mutableListOf<Edit>()
      var prevStart = departure
      var nextEnd = arrival
      for (i in (departure - 1) downTo 0) {
        if (seat.containsInterval(i, departure)) {
          if (intervals[intervalToId(i, departure)].remove(seat)) {
            edits.add(Edit(i, departure, -1))
            prevStart = i
          }
        } else {
          break
        }
      }
      for (i in (arrival + 1) until stationnum) {
        if (seat.containsInterval(arrival, i)) {
          if (intervals[intervalToId(arrival, i)].remove(seat)) {
            edits.add(Edit(arrival, i, -1))
            nextEnd = i
          }
        } else {
          break
        }
      }
      edits.add(Edit(prevStart, nextEnd, 1))
//      pendingUpdates.add(EditGroup(edits))
      updateNums(EditGroup(edits))
      intervals[intervalToId(prevStart, nextEnd)].add(seat)
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

  data class TicketData(
    val tid: Long,
    val passenger: String?,
    val route: Int,
    val coach: Int,
    val seat: Int,
    val departure: Int,
    val arrival: Int
  )

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
package ticketingsystem

import java.util.concurrent.locks.ReentrantLock


class TicketingDS(routenum: Int, val coachnum: Int, val seatnum: Int, val stationnum: Int, val threadnum: Int) :
  TicketingSystem {
  @Volatile
  var tid = 0L
  private val lock = ReentrantLock()

  inner class Route {
    inner class Seat(val cid: Int, val sid: Int, var departure: Int = 1, var arrival: Int = stationnum) {
      var prev: Seat? = this
      var next: Seat? = this
      fun insert(head: Seat?) {
        if (head != null) {
          prev = head.prev
          next = head
          head.prev!!.next = this
          head.prev = this
        }
      }

      fun remove() {
        if (prev != this) {
          prev!!.next = next
          next!!.prev = prev
        }
        prev = this
        next = this
      }
    }

    inner class Interval(val departure: Int, val arrival: Int) {
      @Volatile
      var head: Seat? = null
      @Volatile
      var num: Int = 0
//      fun insert(seat: Seat) {
//        require(seat.departure == departure && seat.arrival == arrival)
//        seat.insert(head)
//        head = seat
//        num++
//      }

      fun remove(seat: Seat) {
        require(seat.departure == departure && seat.arrival == arrival)
        require(num > 0)
        if (num == 1) {
          head = null
        } else if (seat == head) {
          head = seat.next
        }
        seat.remove()
        num--
        check(num >= 0)
      }

      fun push(seat: Seat) {
        require(seat.departure == departure && seat.arrival == arrival)
        seat.insert(head)
        head = seat
        num++
      }

      fun pop(): Seat? {
        if (num == 0) {
          return null
        }
        val seat = head
        if (num == 1) {
          head = null
        } else {
          head = seat!!.next
        }
        seat!!.remove()
        num--
        check(num >= 0)
        return seat
      }
    }
    private val seats = Array(coachnum) { cid -> Array(seatnum) { sid -> mutableListOf(Seat(cid + 1, sid + 1)) } }
    private val intervals: Array<Array<Interval>> = Array( stationnum ) { departure ->
      Array(stationnum - departure) { idx ->
        Interval(departure + 1, departure + 1 + (idx + 1))
      }
    }

    init {
      for (coach in seats) {
        for (seat in coach) {

          val interval = intervals[seat[0].departure - 1][seat[0].arrival - seat[0].departure - 1]
          interval.push(seat[0])
        }
      }
    }
    fun inquiry(departure: Int, arrival: Int): Int {
      var num = 0
      for (start in 1 .. departure) {
        for (end in arrival..stationnum) {
          num += intervals[start - 1][end - start - 1].num
        }
      }
      check(num >= 0)
      return num
    }
    fun getSeat(departure: Int, arrival: Int): Seat? {
      for (start in 1 .. departure) {
        for (end in arrival..stationnum) {
          val interval = intervals[start - 1][end - start - 1]
          if (interval.num > 0) {
            val seat = interval.pop()!!
            seats[seat.cid - 1][seat.sid - 1].remove(seat)
            if (seat.departure < departure) {
              val newSeat = Seat(seat.cid, seat.sid, seat.departure, departure)
              seats[seat.cid - 1][seat.sid - 1].add(newSeat)
              intervals[newSeat.departure - 1][newSeat.arrival - newSeat.departure - 1].push(newSeat)
            }
            if (seat.arrival > arrival) {
              val newSeat = Seat(seat.cid, seat.sid, arrival, seat.arrival)
              seats[seat.cid - 1][seat.sid - 1].add(newSeat)
              intervals[newSeat.departure - 1][newSeat.arrival - newSeat.departure - 1].push(newSeat)
            }
            seat.departure = departure
            seat.arrival = arrival
            return seat
          }
        }
      }
      return null
    }
    fun returnSeat(cid: Int, sid: Int, start: Int, end: Int) {
      var prev:Seat? = null
      var next:Seat? = null
      var departure = start
      var arrival = end
      for (s in seats[cid - 1][sid - 1]) {
        if (s.arrival == departure) {
          prev = s
          departure = s.departure
          intervals[prev.departure - 1][prev.arrival - prev.departure - 1].remove(prev)
        }
        if (s.departure == arrival) {
          next = s
          arrival = s.arrival
          intervals[next.departure - 1][next.arrival - next.departure - 1].remove(next)
        }
      }
      if (prev != null) {
        seats[cid - 1][sid - 1].remove(prev)
      }
      if (next != null) {
        seats[cid - 1][sid - 1].remove(next)
      }
      val seat = Seat(cid, sid, departure, arrival)
      seats[cid - 1][sid - 1].add(seat)
      intervals[seat.departure - 1][seat.arrival - seat.departure - 1].push(seat)
    }
  }

  private val routes = Array(routenum) { Route() }

  private val tickets = mutableMapOf<Long, Ticket>()

  fun Ticket.setFileds(t:Long, r: Int, c: Int, s: Int, d: Int, a: Int, p: String?) {
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
    val r = routes[route - 1]
    val ticket:Ticket
    lock.lock()
    try {
      val seat = r.getSeat(departure, arrival) ?: return null
      ticket = Ticket()
      ticket.setFileds(
        tid, route, seat.cid, seat.sid, departure,arrival, passenger
      )
      tickets[tid] = ticket
      tid += 1
    }
    finally {
      lock.unlock()
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
    lock.lock()
    try {
      return routes[route - 1].inquiry(departure, arrival)
    } finally {
      lock.unlock()
    }

  }

  /**
   * @param ticket
   * @return
   */
  override fun refundTicket(ticket: Ticket?): Boolean {
    lock.lock()
    try {
      if (ticket == null || !tickets.contains(ticket.tid)) {
        return false
      }
      val ticketData = TicketData(
        ticket.tid,
        ticket.passenger,
        ticket.route,
        ticket.coach,
        ticket.seat,
        ticket.departure,
        ticket.arrival
      )
      val record = tickets[ticket.tid]!!
      val recordData = TicketData(
        record.tid,
        record.passenger,
        record.route,
        record.coach,
        record.seat,
        record.departure,
        record.arrival
      )
      if (recordData != ticketData) {
        return false
      }
      routes[ticket.route - 1].returnSeat(ticket.coach, ticket.seat, ticket.departure, ticket.arrival)
      tickets.remove(ticket.tid)
      return true
    } finally {
      lock.unlock()
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
  } //ToDo
}
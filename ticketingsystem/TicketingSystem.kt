package ticketingsystem

class Ticket {
  @JvmField
  var tid: Long = 0
  @JvmField
  var passenger: String? = null
  @JvmField
  var route = 0
  @JvmField
  var coach = 0
  @JvmField
  var seat = 0
  @JvmField
  var departure = 0
  @JvmField
  var arrival = 0
}

interface TicketingSystem {
  fun buyTicket(passenger: String?, route: Int, departure: Int, arrival: Int): Ticket?
  fun inquiry(route: Int, departure: Int, arrival: Int): Int
  fun refundTicket(ticket: Ticket?): Boolean
  fun buyTicketReplay(ticket: Ticket?): Boolean
  fun refundTicketReplay(ticket: Ticket?): Boolean
}
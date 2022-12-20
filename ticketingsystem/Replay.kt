package ticketingsystem

import java.io.*
import java.util.*

object Replay {
  var threadNum = 0
  var methodList: MutableList<String> = ArrayList()

  /**********Manually Modified  */
  var isPosttime = true
//  var detail = false
  var routenum = 3
  var coachnum = 3
  var seatnum = 3
  var stationnum = 3
  var refRatio = 0
  var buyRatio = 0
  var inqRatio = 0
  var debugMode = 1
  var history = ArrayList<HistoryLine>()
  var `object`: TicketingDS? = null
  private fun parseline(historyList: ArrayList<HistoryLine>, line: String): Boolean {
    val linescanner = Scanner(line)
    if (line == "") {
      linescanner.close()
      return true
    }
    val tl = HistoryLine()
    tl.pretime = linescanner.nextLong()
    tl.posttime = linescanner.nextLong()
    tl.threadid = linescanner.nextInt()
    tl.operationName = linescanner.next()
    tl.tid = linescanner.nextLong()
    tl.passenger = linescanner.next()
    tl.route = linescanner.nextInt()
    tl.coach = linescanner.nextInt()
    tl.departure = linescanner.nextInt()
    tl.arrival = linescanner.nextInt()
    tl.seat = linescanner.nextInt()
    tl.res = linescanner.next()
    historyList.add(tl)
    linescanner.close()
    return true
  }

  private fun readConfig(filename: String): Boolean {
    try {
      val scanner = Scanner(File(filename))
      while (scanner.hasNextLine()) {
        val line = scanner.nextLine()
        //System.out.println(line);
        val linescanner = Scanner(line)
        if (line == "") {
          linescanner.close()
          continue
        }
        if (line.substring(0, 1) == "#") {
          linescanner.close()
          continue
        }
        routenum = linescanner.nextInt()
        coachnum = linescanner.nextInt()
        seatnum = linescanner.nextInt()
        stationnum = linescanner.nextInt()
        refRatio = linescanner.nextInt()
        buyRatio = linescanner.nextInt()
        inqRatio = linescanner.nextInt()
        println("route: " + routenum + ", coach: " + coachnum + ", seatnum: " + seatnum + ", station: " + stationnum + ", refundRatio: " + refRatio + ", buyRatio: " + buyRatio + ", inquiryRatio: " + inqRatio)
        linescanner.close()
      }
      scanner.close()
    } catch (e: FileNotFoundException) {
      println(e)
    }
    return true
  }

  private fun initialization() {
    `object` = TicketingDS(routenum, coachnum, seatnum, stationnum, threadNum)
    methodList.add("refundTicket")
    methodList.add("buyTicket")
    methodList.add("inquiry")
  }

  private fun execute(methodName: String, line: HistoryLine): Boolean {
    val ticket = Ticket()
    val flag: Boolean
    ticket.tid = line.tid
    ticket.passenger = line.passenger
    ticket.route = line.route
    ticket.coach = line.coach
    ticket.departure = line.departure
    ticket.arrival = line.arrival
    ticket.seat = line.seat
    if (methodName == "buyTicket") {
      if (line.res == "false") {
        val num = `object`!!.inquiry(ticket.route, ticket.departure, ticket.arrival)
        return if (num == 0) true else {
          println("Error: TicketSoldOut" + " " + line.pretime + " " + line.posttime + " " + line.threadid + " " + line.route + " " + line.departure + " " + line.arrival)
          println("RemainTicket" + " " + num + " " + line.route + " " + line.departure + " " + line.arrival)
          false
        }
      }
      val ticket1: Ticket? = `object`!!.buyTicket(ticket.passenger, ticket.route, ticket.departure, ticket.arrival)
      return if (ticket1 != null && line.res == "true" && ticket.passenger === ticket1.passenger && ticket.route == ticket1.route && ticket.coach == ticket1.coach && ticket.departure == ticket1.departure && ticket.arrival == ticket1.arrival && ticket.seat == ticket1.seat) {
        true
      } else {
        println("Error: Ticket is bought" + " " + line.pretime + " " + line.posttime + " " + line.threadid + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat)
        false
      }
    } else if (methodName == "refundTicket") {
      flag = `object`!!.refundTicket(ticket)
      return if (flag && line.res == "true" || !flag && line.res == "false") true else {
        println("Error: Ticket is refunded" + " " + line.pretime + " " + line.posttime + " " + line.threadid + " " + ticket.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat)
        false
      }
    } else if (methodName == "inquiry") {
      val num = `object`!!.inquiry(line.route, line.departure, line.arrival)
      return if (num == line.seat) true else {
        println("Error: RemainTicket" + " " + line.pretime + " " + line.posttime + " " + line.threadid + " " + line.route + " " + line.departure + " " + line.arrival + " " + line.seat)
        println("Real RemainTicket is" + " " + line.seat + " " + ", Expect RemainTicket is" + " " + num + ", " + line.route + " " + line.departure + " " + line.arrival)
        false
      }
    }
    println("No match method name")
    return false
  }

  /***********************VeriLin***************  */
  private fun writeHistoryToFile(historyList: ArrayList<HistoryLine>, filename: String) {
    try {
      System.setOut(PrintStream(FileOutputStream(filename)))
      writeHistory(historyList)
      System.setOut(PrintStream(FileOutputStream(FileDescriptor.out)))
    } catch (e: FileNotFoundException) {
      println(e)
    }
  }

  private fun writeHistory(historyList: ArrayList<HistoryLine>) {
    for (i in historyList.indices) {
      writeline(historyList, i)
    }
  }

  private fun writeline(historyList: ArrayList<HistoryLine>, line: Int) {
    val tl = historyList[line]
    println(tl.pretime.toString() + " " + tl.posttime + " " + tl.threadid + " " + tl.operationName + " " + tl.tid + " " + tl.passenger + " " + tl.route + " " + tl.coach + " " + tl.departure + " " + tl.arrival + " " + tl.seat)
  }

  private fun readHistory(historyList: ArrayList<HistoryLine>, filename: String): Boolean {
    try {
      val scanner = Scanner(File(filename))
      var i = 0
      while (scanner.hasNextLine()) {
        if (parseline(historyList, scanner.nextLine()) == false) {
          scanner.close()
          println("Error in parsing line $i")
          return false
        }
        i++
      }
      scanner.close()
    } catch (e: FileNotFoundException) {
      println(e)
    }
    return true
  }

  private fun checkline(historyList: ArrayList<HistoryLine>, index: Int): Boolean {
    val line = historyList[index]
    if (debugMode == 1) {
      if (index == 158) {
        println("Debugging line $index ")
      }
    }
    for (i in methodList.indices) {
      if (line.operationName == methodList[i]) {
        //		System.out.println("Line " + index + " executing " + methodList.get(i) + " res: " + flag + " tid = " + line.tid);
        return execute(methodList[i], line)
      }
    }
    return false
  }

  @Throws(InterruptedException::class)
  @JvmStatic
  fun main(args: Array<String>) {
    if (args.size != 4) {
      println("The parameter list of VeriLin is threadNum, historyFile, isPosttime(0/1), failedTrace.")
      return
    }
    threadNum = args[0].toInt()
    val fileName = args[1]
    if (args[2].toInt() == 0) {
      isPosttime = false
    } else if (args[2].toInt() == 1) {
      isPosttime = true
    } else {
      println("The parameter list of VeriLin is threadNum, historyFile, isPosttime(0/1), failedTrace.")
      return
    }
    val ft = args[3]
    readConfig("TrainConfig")
    val startMs: Long
    val endMs: Long
    readHistory(history, fileName)
    initialization()
    startMs = System.currentTimeMillis()
    if (!isPosttime) {
      val com1 = hl_Comparator_1()
      Collections.sort(history, com1)
    } else {
      val com2 = hl_Comparator_2()
      Collections.sort(history, com2)
    }
    writeHistoryToFile(history, ft)
    for (i in history.indices) {
      if (!checkline(history, i)) {
        println("checkLine returns FALSE in line $i")
        break
      }
    }
    endMs = System.currentTimeMillis()
    println("checking time = " + (endMs - startMs))
  }

  class hl_Comparator_1 : Comparator<HistoryLine> {
    override fun compare(hl1: HistoryLine, hl2: HistoryLine): Int {
      return if (hl1.pretime - hl2.pretime > 0) 1 else if (hl1.pretime - hl2.pretime == 0L) 0 else -1
    }
  }

  class hl_Comparator_2 : Comparator<HistoryLine> {
    override fun compare(hl1: HistoryLine, hl2: HistoryLine): Int {
      return if (hl1.posttime - hl2.posttime > 0) 1 else if (hl1.posttime - hl2.posttime == 0L) 0 else -1
    }
  }

  class HistoryLine {
    var pretime: Long = 0
    var posttime: Long = 0
    var threadid = 0
    var operationName: String? = null
    var tid: Long = 0
    var passenger: String? = null
    var route = 0
    var coach = 0
    var seat = 0
    var departure = 0
    var arrival = 0
    var res: String? = null
  }
}
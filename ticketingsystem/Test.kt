package ticketingsystem

import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.*

object Test {
  internal class myInt {
    @Volatile
    var value = 0
  }

  var threadnum = 0 //input

  var testnum = 0 //input

  var isSequential = false //input

  var msec = 0
  var nsec = 0
  var totalPc = 0

  /****************Manually Set Testing Information  */
  var routenum = 3 // route is designed from 1 to 3

  var coachnum = 3 // coach is arranged from 1 to 5

  var seatnum = 5 // seat is allocated from 1 to 20

  var stationnum = 5 // station is designed from 1 to 5


  var refRatio = 10
  var buyRatio = 20
  var inqRatio = 30


  var tds: TicketingDS? = null
  val methodList: MutableList<String> = ArrayList()
  val freqList: MutableList<Int> = ArrayList()
  val currentTicket: MutableList<Ticket?> = ArrayList()
  val currentRes: MutableList<String> = ArrayList()
  val soldTicket = ArrayList<MutableList<Ticket>>()

  @Volatile
  var initLock = false

  //	final static AtomicInteger tidGen = new AtomicInteger(0);
//  val rand = ThreadLocalRandom
  fun initialization() {
    tds = TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum)
    soldTicket.clear()
    currentTicket.clear()
    currentRes.clear()
    methodList.clear()
    freqList.clear()
    for (i in 0 until threadnum) {
      val threadTickets: MutableList<Ticket> = ArrayList()
      soldTicket.add(threadTickets)
      currentTicket.add(null)
      currentRes.add("")
    }
    //method freq is up to
    methodList.add("refundTicket")
    freqList.add(refRatio)
    methodList.add("buyTicket")
    freqList.add(refRatio + buyRatio)
    methodList.add("inquiry")
    freqList.add(refRatio + buyRatio + inqRatio)
    totalPc = refRatio + buyRatio + inqRatio
  }

  class Executor() {
    private val rand = ThreadLocalRandom.current()
    private val threadId = ThreadId.get()
    fun print(preTime: Long, postTime: Long, actionName: String) {
      val ticket = currentTicket[ThreadId.get()]
      println(preTime.toString() + " " + postTime + " " + ThreadId.get() + " " + actionName + " " + ticket!!.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat + " " + currentRes[ThreadId.get()])
    }

    fun getPassengerName(): String {
//      val uid = rand.nextInt(testnum).toLong()
//    return "passenger$uid"
      return "passenger"
    }

    fun execute(num: Int): Boolean {
      val route: Int
      val departure: Int
      val arrival: Int
      var ticket: Ticket? = Ticket()
      return when (num) {
        0 -> {
          if (soldTicket[threadId].size == 0) return false
          val n = rand.nextInt(soldTicket[threadId].size)
          ticket = soldTicket[threadId].removeAt(n)
          currentTicket[threadId] = ticket
          val flag = tds!!.refundTicket(ticket)
          currentRes[threadId] = "true"
          flag
        }

        1 -> {
          val passenger = getPassengerName()
          route = rand.nextInt(routenum) + 1
          departure = rand.nextInt(stationnum - 1) + 1
          arrival = departure + rand.nextInt(stationnum - departure) + 1
          ticket = tds!!.buyTicket(passenger, route, departure, arrival)
          if (ticket == null) {
            ticket = Ticket()
            ticket.passenger = passenger
            ticket.route = route
            ticket.departure = departure
            ticket.arrival = arrival
            ticket.seat = 0
            currentTicket[threadId] = ticket
            currentRes[threadId] = "false"
            return true
          }
          currentTicket[threadId] = ticket
          currentRes[threadId] = "true"
          soldTicket[threadId].add(ticket)
          true
        }

        2 -> {
          ticket!!.passenger = getPassengerName()
          ticket.route = rand.nextInt(routenum) + 1
          ticket.departure = rand.nextInt(stationnum - 1) + 1
          ticket.arrival =
            ticket.departure + rand.nextInt(stationnum - ticket.departure) + 1 // arrival is always greater than departure
          ticket.seat = tds!!.inquiry(ticket.route, ticket.departure, ticket.arrival)
          currentTicket[threadId] = ticket
          currentRes[threadId] = "true"
          true
        }

        else -> {
          println("Error in execution.")
          false
        }
      }
    }

    fun step() {
      val sel = rand.nextInt(totalPc)
      var cnt = 0
      for (j in methodList.indices) {
        if (sel >= cnt && sel < cnt + freqList[j]) {
          execute(j)
        } else {
          cnt += freqList[j]
        }
      }
    }
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
        linescanner.close()
      }
      scanner.close()
    } catch (e: FileNotFoundException) {
      println(e)
    }
    return true
  }

  private fun benchMarkOne(threadPool: ExecutorService): Long {
    initialization()
    val exitLatch = CountDownLatch(threadnum)
    val tasks = Array(threadnum) {
      Runnable {
        val executor = Executor()
        for (i in 0 until testnum) {
          executor.step()
        }
        exitLatch.countDown()
      }
    }
    val startTime = System.nanoTime()
    for (i in 0 until threadnum) {
      threadPool.execute(tasks[i])
    }
    exitLatch.await()
    val endTime = System.nanoTime()
    return endTime - startTime
  }

  /***********VeriLin */
  @Throws(InterruptedException::class)
  @JvmStatic
  fun main(args: Array<String>) {
    if (args.size != 4) {
      println("The arguments of Test is threadNum,  testNum, benchNum, warmUpNum")
      return
    }
    threadnum = args[0].toInt()
    testnum = args[1].toInt()
    val benchNum = args[2].toInt()
    val warmUpNum = args[3].toInt()
    readConfig("TrainConfig")
    val times = LongArray(benchNum)
    val threadPool = Executors.newFixedThreadPool(threadnum)
    for (i in 0 until warmUpNum) {
      benchMarkOne(threadPool)
    }
    for (i in 0 until benchNum) {
      val res = benchMarkOne(threadPool)
      times[i] = res
    }
    try {
      threadPool.shutdown()
      threadPool.awaitTermination(1, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
      e.printStackTrace()
    }
    val meanThroughputs = (testnum * threadnum * 10e6 * benchNum) / times.sum().toDouble()
    // calc average without min and max
    println("Average throughput: $meanThroughputs ops/ms")
  }
}
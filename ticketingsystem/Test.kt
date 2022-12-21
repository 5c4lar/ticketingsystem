package ticketingsystem

import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

object Test {

  var threadnum = 0 //input

  var testnum = 0 //input

  var totalPc = 0

  /****************Manually Set Testing Information  */
  var routenum = 3 // route is designed from 1 to 3

  var coachnum = 3 // coach is arranged from 1 to 5

  var seatnum = 5 // seat is allocated from 1 to 20

  var stationnum = 5 // station is designed from 1 to 5


  var refRatio = 10
  var buyRatio = 20
  var inqRatio = 30

  var numCallsAll = Array(3) { AtomicLong(0) }
  var callTimeAll = Array(3) { AtomicLong(0) }

  var tds: TicketingDS? = null
  val methodList: MutableList<String> = ArrayList()
  val freqList: MutableList<Int> = ArrayList()
  val currentTicket: MutableList<Ticket?> = ArrayList()
  val currentRes: MutableList<String> = ArrayList()
  val soldTicket = ArrayList<MutableList<Ticket>>()

  fun initialization() {
    tds = TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum)
    soldTicket.clear()
    currentTicket.clear()
    currentRes.clear()
    methodList.clear()
    freqList.clear()
    numCallsAll = Array(3) { AtomicLong(0) }
    callTimeAll = Array(3) { AtomicLong(0) }
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
    val numCalls = Array(3) { 0L }
    val callTime = Array(3) { 0L }

    private fun getPassengerName(): String {
      return "passenger"
    }

    private fun execute(num: Int): Boolean {
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
          numCalls[num]++
          val start = System.nanoTime()
          val flag = tds!!.refundTicket(ticket)
          val end = System.nanoTime()
          callTime[num] += end - start
          currentRes[threadId] = "true"
          flag
        }

        1 -> {
          val passenger = getPassengerName()
          route = rand.nextInt(routenum) + 1
          departure = rand.nextInt(stationnum - 1) + 1
          arrival = departure + rand.nextInt(stationnum - departure) + 1
          numCalls[num]++
          val start = System.nanoTime()
          ticket = tds!!.buyTicket(passenger, route, departure, arrival)
          val end = System.nanoTime()
          callTime[num] += end - start
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
          numCalls[num]++
          val start = System.nanoTime()
          ticket.seat = tds!!.inquiry(ticket.route, ticket.departure, ticket.arrival)
          val end = System.nanoTime()
          callTime[num] += end - start
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
          break
        } else {
          cnt += freqList[j]
        }
      }
    }
  }


  fun readConfig(filename: String): Boolean {
    try {
      val scanner = Scanner(File(filename))
      while (scanner.hasNextLine()) {
        val line = scanner.nextLine()
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

  fun benchMarkOne(threadPool: ExecutorService): Long {
    initialization()
    val exitLatch = CountDownLatch(threadnum)
    val tasks = Array(threadnum) {
      Runnable {
        val executor = Executor()
        for (i in 0 until testnum) {
          executor.step()
        }
        exitLatch.countDown()
        numCallsAll.zip(executor.numCalls) { a, b -> a.addAndGet(b) }
        callTimeAll.zip(executor.callTime) { a, b -> a.addAndGet(b) }
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

  fun testWithConfig(threadNum: Int, testNum: Int, benchNum: Int, warmUpNum: Int) {
    threadnum = threadNum
    testnum = testNum
    println("========================================")
    println("ThreadNum: $threadnum")
    val threadPool = Executors.newFixedThreadPool(threadnum)
    ThreadId.reset()
    val times = LongArray(benchNum)
    val totalCalls = LongArray(benchNum)
    val eachCallCount = Array(benchNum) { LongArray(3) }
    val eachCallTime = Array(benchNum) { LongArray(3) }
    for (i in 0 until warmUpNum) {
      benchMarkOne(threadPool)
    }
    for (i in 0 until benchNum) {
      val res = benchMarkOne(threadPool)
      totalCalls[i] = numCallsAll.sumOf { it.get() }
      eachCallCount[i] = numCallsAll.map { it.get() }.toLongArray()
      eachCallTime[i] = callTimeAll.map { it.get() }.toLongArray()
      times[i] = res
    }
    try {
      threadPool.shutdown()
      threadPool.awaitTermination(1, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
      e.printStackTrace()
    }
    for (i in 0 until methodList.size) {
      println("[*]" + methodList[i] + " Calls: " + eachCallCount.sumOf { it[i] } + " Time: " + TimeUnit.NANOSECONDS.toMillis(
        eachCallTime.sumOf { it[i] }) + " ms" + " Latency: " + TimeUnit.NANOSECONDS.toMicros(eachCallTime.sumOf { it[i] }) / eachCallCount.sumOf { it[i] }
        .toDouble() + " us/op")
    }
    val ms = TimeUnit.NANOSECONDS.toMillis(times.sum())
    val meanThroughputs = totalCalls.sum().toDouble() / ms
    print("[*]Total Calls: " + totalCalls.sum())
    print(" Total Time: ${TimeUnit.NANOSECONDS.toMillis(times.sum())} ms")
    print(" Mean Throughput: $meanThroughputs ops/ms\n")
    println("========================================")
  }

  /***********VeriLin */
  @Throws(InterruptedException::class)
  @JvmStatic
  fun main(args: Array<String>) {
    if (args.size != 4) {
      println("The arguments of Test is threadNum, testNum, benchNum, warmUpNum")
      return
    }
    threadnum = args[0].toInt()
    testnum = args[1].toInt()
    val benchNum = args[2].toInt()
    val warmUpNum = args[3].toInt()
    readConfig("TrainConfig")
    testWithConfig(threadnum, testnum, benchNum, warmUpNum)
  }
}
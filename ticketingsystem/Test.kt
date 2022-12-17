package ticketingsystem

import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

object Test {
  var threadnum = 0 //input

  var testnum = 0 //input

  var isSequential = false //input

  var msec = 0
  var nsec = 0
  var totalPc = 0

  var sLock = AtomicInteger(0) //Synchronization Lock

  lateinit var fin: BooleanArray

  fun exOthNotFin(tNum: Int, tid: Int): Boolean {
    var flag = false
    for (k in 0 until tNum) {
      if (k == tid) continue
      flag = flag || !fin[k]
    }
    return flag
  }

  fun SLOCK_TAKE() {
    while (sLock.compareAndSet(0, 1) == false) {
    }
  }

  fun SLOCK_GIVE() {
    sLock.set(0)
  }

  fun SLOCK_TRY(): Boolean {
    return sLock.get() == 0
  }

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
  val rand = Random()
  fun initialization() {
    tds = TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum)
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

  fun getPassengerName(): String {
    val uid = rand.nextInt(testnum).toLong()
    return "passenger$uid"
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
        //System.out.println("route: " + routenum + ", coach: " + coachnum + ", seatnum: " + seatnum + ", station: " + stationnum + ", refundRatio: " + refRatio + ", buyRatio: " + buyRatio + ", inquiryRatio: " + inqRatio);
        linescanner.close()
      }
      scanner.close()
    } catch (e: FileNotFoundException) {
      println(e)
    }
    return true
  }

  fun print(preTime: Long, postTime: Long, actionName: String) {
    val ticket = currentTicket[ThreadId.get()]
    println(preTime.toString() + " " + postTime + " " + ThreadId.get() + " " + actionName + " " + ticket!!.tid + " " + ticket.passenger + " " + ticket.route + " " + ticket.coach + " " + ticket.departure + " " + ticket.arrival + " " + ticket.seat + " " + currentRes[ThreadId.get()])
  }

  fun execute(num: Int): Boolean {
    val route: Int
    val departure: Int
    val arrival: Int
    var ticket: Ticket? = Ticket()
    return when (num) {
      0 -> {
        if (soldTicket[ThreadId.get()].size == 0) return false
        val n = rand.nextInt(soldTicket[ThreadId.get()].size)
        ticket = soldTicket[ThreadId.get()].removeAt(n)
        if (ticket == null) {
          return false
        }
        currentTicket[ThreadId.get()] = ticket
        val flag = tds!!.refundTicket(ticket)
        currentRes[ThreadId.get()] = "true"
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
          currentTicket[ThreadId.get()] = ticket
          currentRes[ThreadId.get()] = "false"
          return true
        }
        currentTicket[ThreadId.get()] = ticket
        currentRes[ThreadId.get()] = "true"
        soldTicket[ThreadId.get()].add(ticket)
        true
      }

      2 -> {
        ticket!!.passenger = getPassengerName()
        ticket.route = rand.nextInt(routenum) + 1
        ticket.departure = rand.nextInt(stationnum - 1) + 1
        ticket.arrival =
          ticket.departure + rand.nextInt(stationnum - ticket.departure) + 1 // arrival is always greater than departure
        ticket.seat = tds!!.inquiry(ticket.route, ticket.departure, ticket.arrival)
        currentTicket[ThreadId.get()] = ticket
        currentRes[ThreadId.get()] = "true"
        true
      }

      else -> {
        println("Error in execution.")
        false
      }
    }
  }

  /***********VeriLin */
  @Throws(InterruptedException::class)
  @JvmStatic
  fun main(args: Array<String>) {
    if (args.size != 5) {
      println("The arguments of Test is threadNum,  testNum, isSequential(0/1), delay(millionsec), delay(nanosec)")
      return
    }
    threadnum = args[0].toInt()
    testnum = args[1].toInt()
    isSequential = if (args[2] == "0") {
      false
    } else if (args[2] == "1") {
      true
    } else {
      println("The arguments of GenerateHistory is threadNum,  testNum, isSequential(0/1)")
      return
    }
    msec = args[3].toInt()
    nsec = args[4].toInt()
    readConfig("TrainConfig")
    val threads = arrayOfNulls<Thread>(threadnum)
    val barrier = myInt()
    fin = BooleanArray(threadnum)
    val startTime = System.nanoTime()
    for (i in 0 until threadnum) {
      threads[i] = Thread(Runnable {
        if (ThreadId.get() == 0) {
          initialization()
          initLock = true
        } else {
          while (!initLock) {
          }
        }
        for (k in 0 until testnum) {
          val sel = rand.nextInt(totalPc)
          var cnt = 0
          if (isSequential) {
            while (ThreadId.get() != barrier.value && exOthNotFin(threadnum, ThreadId.get()) == true) {
            }
            SLOCK_TAKE()
          }
          for (j in methodList.indices) {
            if (sel >= cnt && sel < cnt + freqList[j]) {
              if (msec != 0 || nsec != 0) {
                try {
                  Thread.sleep(msec.toLong(), nsec)
                } catch (e: InterruptedException) {
                  return@Runnable
                }
              }
              val preTime = System.nanoTime() - startTime
              val flag = execute(j)
              val postTime = System.nanoTime() - startTime
//              if (flag) {
//                print(preTime, postTime, methodList[j])
//              }
              cnt += freqList[j]
            }
          }
          if (isSequential) {
            if (k == testnum - 1) fin[ThreadId.get()] = true
            if (exOthNotFin(threadnum, ThreadId.get()) == true) {
              barrier.value = rand.nextInt(threadnum)
              while (fin[barrier.value] == true) {
                barrier.value = rand.nextInt(threadnum)
              }
            }
            SLOCK_GIVE()
          }
        }
      })
      threads[i]!!.start()
    }
    for (i in 0 until threadnum) {
      threads[i]!!.join()
    }
    val endTime = System.nanoTime()
    println("Total time: ${(endTime - startTime) / 10e6} ms")
    println("Throughput: ${(testnum * threadnum * 10e9).toDouble() / (endTime - startTime)} ops/s")
  }
}
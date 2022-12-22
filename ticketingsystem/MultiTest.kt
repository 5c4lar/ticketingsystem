package ticketingsystem

import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
  val threadNums = arrayOf(1, 2, 4, 8, 16, 32, 64)
  if (args.size != 3) {
    println("The arguments of Test is testNum, benchNum, warmUpNum")
    return
  }
  val testNum = args[0].toInt()
  val benchNum = args[1].toInt()
  val warmUpNum = args[2].toInt()
  Test.readConfig("TrainConfig")
  val results: MutableMap<Int, Test.Result> = HashMap()
  for (threadNum in threadNums) {
    val currentTestNum = testNum * 64 / threadNum
    val res = Test.testWithConfig(threadNum, currentTestNum, benchNum, warmUpNum)
    results[threadNum] = res
  }
  println("========================================")
  println("ThreadNum,Total Calls,Total Time,Mean Throughput,Refund Calls,Refund Time,Refund Latency,Buy Calls,Buy Time,Buy Latency,Inquiry Calls,Inquiry Time,Inquiry Latency")
  for (threadNum in threadNums) {
    val res = results[threadNum]
    for (i in 0 until benchNum) {
      val ms = TimeUnit.NANOSECONDS.toMillis(res!!.times[i])
      val meanThroughputs = res.totalCalls[i].toDouble() / ms
      val eachCallCount = res.eachCallCount[i]
      val eachCallTime = res.eachCallTime[i]
      val refundCalls = eachCallCount[0]
      val refundTime = TimeUnit.NANOSECONDS.toMillis(eachCallTime[0])
      val refundLatency = TimeUnit.NANOSECONDS.toMicros(eachCallTime[0]) / eachCallCount[0].toDouble()
      val buyCalls = eachCallCount[1]
      val buyTime = TimeUnit.NANOSECONDS.toMillis(eachCallTime[1])
      val buyLatency = TimeUnit.NANOSECONDS.toMicros(eachCallTime[1]) / eachCallCount[1].toDouble()
      val inquiryCalls = eachCallCount[2]
      val inquiryTime = TimeUnit.NANOSECONDS.toMillis(eachCallTime[2])
      val inquiryLatency = TimeUnit.NANOSECONDS.toMicros(eachCallTime[2]) / eachCallCount[2].toDouble()
      println("$threadNum,${res.totalCalls[i]},$ms,$meanThroughputs, $refundCalls, $refundTime, $refundLatency, $buyCalls, $buyTime, $buyLatency, $inquiryCalls, $inquiryTime, $inquiryLatency")
    }
  }
  println("========================================")
}
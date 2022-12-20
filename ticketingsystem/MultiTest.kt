package ticketingsystem

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun testWithConfig(threadNum: Int, testNum: Int, benchNum: Int, warmUpNum: Int) {
  Test.threadnum = threadNum
  Test.testnum = testNum
  Test.readConfig("TrainConfig")
  val times = LongArray(benchNum)
  val threadPool = Executors.newFixedThreadPool(Test.threadnum)
  ThreadId.reset()
  for (i in 0 until warmUpNum) {
    Test.benchMarkOne(threadPool)
  }
  for (i in 0 until benchNum) {
    val res = Test.benchMarkOne(threadPool)
    times[i] = res
  }
  try {
    threadPool.shutdown()
    threadPool.awaitTermination(1, TimeUnit.SECONDS)
  } catch (e: InterruptedException) {
    e.printStackTrace()
  }
  val meanThroughputs = (Test.testnum * Test.threadnum * 10e6 * benchNum) / times.sum().toDouble()
  // calc average without min and max
  println("Average throughput: $meanThroughputs ops/ms")
}

fun main() {
  val threadNums = arrayOf(1, 2, 4, 8, 16, 32, 64)
  val testNum = 1000000
  val benchNum = 1
  val warmUpNum = 0
  for (threadNum in threadNums) {
    println("threadNum: $threadNum")
    testWithConfig(threadNum, testNum, benchNum, warmUpNum)
  }
}
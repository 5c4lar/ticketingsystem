package ticketingsystem

fun main() {
  val threadNums = arrayOf(1, 2, 4, 8, 16, 32, 64)
  val testNum = 1000000
  val benchNum = 1
  val warmUpNum = 1
  for (threadNum in threadNums) {
    Test.testWithConfig(threadNum, testNum, benchNum, warmUpNum)
  }
}
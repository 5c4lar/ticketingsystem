package ticketingsystem

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
  for (threadNum in threadNums) {
    Test.testWithConfig(threadNum, testNum, benchNum, warmUpNum)
  }
}
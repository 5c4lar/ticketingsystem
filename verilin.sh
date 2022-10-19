#!/bin/sh

mkdir -p build
kotlinc ticketingsystem/*.kt -include-runtime -d build
javac -encoding UTF-8 -cp ./build ticketingsystem/GenerateHistory.java -d build
javac -encoding UTF-8 -cp ./build ticketingsystem/Replay.java -d build
kotlin -cp ./build ticketingsystem.GenerateHistory 4 1000 1 0 0 > history
java -Xss1024m -Xmx400g -jar VeriLin.jar 4 history 1 failedHistory

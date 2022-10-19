#!/bin/sh
mkdir -p build
kotlinc ticketingsystem/*.kt -include-runtime -d build
javac -encoding UTF-8 -cp ./build ticketingsystem/GenerateHistory.java -d build
kotlin -classpath ./build ticketingsystem.GenerateHistory 4 100 0 0 0

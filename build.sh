#!/bin/sh
mkdir -p build
javac -encoding UTF-8 -cp ./build ticketingsystem/ThreadId.java -d build
kotlinc ticketingsystem/*.kt -include-runtime -d build -classpath ./build
javac -encoding UTF-8 -cp ./build ticketingsystem/GenerateHistory.java -d build
#!/bin/sh
./build.sh
kotlin -cp ./build ticketingsystem.GenerateHistory 4 10000 1 0 0 > history
java -Xss1024m -Xmx400g -jar VeriLin.jar 4 history 1 failedHistory

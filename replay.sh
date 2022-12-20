#!/bin/sh
#./build.sh
java -Xss1024m -Xmx400g -cp TicketingSystem.jar ticketingsystem.GenerateHistory 4 1000 1 0 0 > history
java -Xss1024m -Xmx400g -cp TicketingSystem.jar ticketingsystem.Replay 4 history 1 failedHistory

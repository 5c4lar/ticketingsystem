#!/bin/sh
./build.sh
kotlin -classpath ./build ticketingsystem.GenerateHistory 4 10000 0 0 0

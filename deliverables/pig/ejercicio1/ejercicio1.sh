#!/bin/sh
SESSION_FILE="./input/sessions.data"
ORDERS_FILE="./input/orders.data"
DESTINY_FOLDER="output"
DESTINY_SESSION_FILE=$DESTINY_FOLDER"/sessions.data"
DESTINY_ORDERS_FILE=$DESTINY_FOLDER"/orders.data"
PIG_SCRIPT_FILE="load_data.pig"

if [ "$1" != "local" -a "$1" != "hdfs" ]
then
       echo "usage: ./ejercicio1.sh <mode>"
       echo "where mode is \"hdfs\" or \"local\""
       echo "eg. ./ejercicio1.sh hdfs"
       exit -1
fi

if [ "$1" == "hdfs" ]
then
	echo "Checking if hdfs command is installed."
	which hdfs > /dev/null
	if [ $? -ne 0 ]
	then
		echo "Sorry. You need hdfs command installed in your system. Aborting."
		exit -1
	else
		echo "hdfs is properly configured. Continuing..."
		echo "creating destiny folder in HDFS: "$DESTINY_FOLDER
		hdfs dfs -mkdir $DESTINY_FOLDER
		echo "Checking sample files existance..."
		if [ -f $SESSIONS_FILE ]
		then
			echo "Input file: $SESSION_FILE exists. Putting it into HDFS."
			hdfs dfs -put $SESSION_FILE $DESTINY_SESSION_FILE
		else
			echo "Input file doesn't exists. Aborting..."
			exit -3
		fi
		if [ -f $ORDERS_FILE ]
		then
			echo "Input file: $ORDERS_FILE exists. Putting it into HDFS."
			hdfs dfs -put $ORDERS_FILE $DESTINY_ORDERS_FILE
		else
			echo "Input file doesn't exists. Aborting..."
			exit -3
		fi
		echo "Done..."
	fi
fi

echo "Checking if pig is installed..."
which pig > /dev/null
if [ $? -ne 0 ]
then
	echo "Sorry. Pig is not installed. Aborting."
	exit -1
else
	if [ "$1" == "hdfs" ]
	then
		echo "Executing pig using HDFS..."
		pig -f $PIG_SCRIPT_FILE
	else
		echo "Executing pig in local mode..."
		pig -x local -f $PIG_SCRIPT_FILE
	fi
fi

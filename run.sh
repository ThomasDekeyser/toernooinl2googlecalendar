#!/bin/sh

JAVA_HOME=/usr/lib/jvm/java-6-openjdk-i386; export JAVA_HOME

DIRNAME=`dirname $0`
cd $DIRNAME

# Classpath env variable positionning
LIB_DIR=lib
SEP=:
CLASSPATH=".${SEP}"
for file in `ls -1 $LIB_DIR`
  do CLASSPATH=${CLASSPATH}${SEP}${LIB_DIR}/${file}
done

$JAVA_HOME/bin/java -Dconfig=config/config.xml -Dlog4j.configuration=file:config/log4j.properties -cp $CLASSPATH be.gentsebc.calendar.sync.CalendarSync



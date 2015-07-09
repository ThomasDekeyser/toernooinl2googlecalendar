# toernooinl2googlecalendar
This application sync's competition calendars from toernooi.nl into individual google calendars per team. 
As toernooi.nl has no public API, the data needs to be scrapped from toernooi.nl website (slow).
Alternativly, an XML with the necesary data can be given as well (faster)

# Requirements
* Java 1.7 runtime environment
* Possibility the run a standalone java application from cmd-line.
* External cron-alike scheduler

# Limitations
* Current Google API limitation: only "25 new calendars within a short period of time" can be created.
https://support.google.com/a/answer/2905486?hl=en



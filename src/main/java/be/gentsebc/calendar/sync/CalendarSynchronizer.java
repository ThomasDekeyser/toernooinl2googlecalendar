/*
 * Copyright (c) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package be.gentsebc.calendar.sync;

import static org.joox.JOOX.$;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.AclRule;
import com.google.api.services.calendar.model.AclRule.Scope;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import org.apache.log4j.Logger;
import org.joox.JOOX;
import org.joox.Match;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;

/**
 * @author Thomas Dekeyser
 */
public class CalendarSynchronizer {


    private Document config;
    private Document document;
    private static com.google.api.services.calendar.Calendar client;
    static Logger logger = Logger.getLogger(CalendarSynchronizer.class);
    DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static int SLEEP_TIME_TO_AVOID_USER_RATE_LIMITS_AT_GOOGLE = 1000;

    public CalendarSynchronizer(Document myConfig, com.google.api.services.calendar.Calendar myClient) throws IOException {
        config = myConfig;
        client = myClient;
    }

    public void execute() throws SAXException, IOException, ParseException {
        String urlString = $(config).xpath("/config/pbo").content();

        HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
        HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();
        HttpRequest httpRequest = requestFactory.buildGetRequest(new GenericUrl(new URL(urlString)));

        //Parse webHarvest results and sync with googleCalendar
        DocumentBuilder builder = JOOX.builder();
        document = builder.parse(httpRequest.execute().getContent());
        SortedMap<String,String> existingCalendarList = giveExistingCalendars();
        syncTeams(existingCalendarList);
        giveExistingCalendars();
    }


    public SortedMap<String,String> giveExistingCalendars() throws IOException, ParseException{

        SortedMap<String,String> myCalendarList = new TreeMap<String, String>();

        CalendarList feed = getCalendarListAndAddToGivenMap(myCalendarList,null);
        while (feed.getNextPageToken() != null) {
            feed = getCalendarListAndAddToGivenMap(myCalendarList,feed.getNextPageToken());
        }

        Iterator<String> i = myCalendarList.keySet().iterator();
        while (i.hasNext()) {
            String key = i.next();
            logger.info(key + "," + myCalendarList.get(key));
        }
        return myCalendarList;
    }

    private CalendarList getCalendarListAndAddToGivenMap(SortedMap<String, String> myCalendarList,String nextPageToken) throws IOException {
        //Build call
        com.google.api.services.calendar.Calendar.CalendarList.List calendarList = client.calendarList().list().setMaxResults(250);
        if (nextPageToken != null) {
            calendarList.setPageToken(nextPageToken);
        }

        //Execte call
        CalendarList feed = calendarList.execute();

        //Add to map
        addCalenderItemsToGivenMap(feed,myCalendarList);
        return feed;
    }

    private void addCalenderItemsToGivenMap(CalendarList feed, SortedMap<String,String> myCalendarList) {
        if (feed.getItems() != null) {
            for (CalendarListEntry entry : feed.getItems()) {
                if (myCalendarList.containsKey(entry.getSummary().toUpperCase())) {
                    logger.error("Duplicate calender found for key "+entry.getSummary()+". Please manually removed one of them because this only one of them will be updated and no garantees that it will be always the same one.");
                    System.exit(-1);
                }
                myCalendarList.put(entry.getSummary().toUpperCase(),entry.getId());
            }
        }


    }



    public void testJoox() {
        if (logger.isDebugEnabled()) {
            logger.debug("Searching for teams...");
        }

        for (Match value : $(document).xpath("//team").each()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Found team: " + value.attr("name"));
            }
        }
    }

    public void syncTeams(SortedMap<String,String> existingCalendarList) throws IOException, ParseException {
        long startTime,endTime=0L;
        int runTime=0;
        for (Match value : $(document).xpath("//team").each()) {
            startTime = System.nanoTime();
            String teamName = value.attr("name");
            String calendarName = giveCalendarName(teamName);
            String calendarId = giveCalendarId(calendarName, existingCalendarList);
            Events existingEvents = client.events().list(calendarId).execute();
            logger.info("Starting sync for calendar '"+calendarName+"'/'"+calendarId+"'");
            try {
                Thread.sleep(SLEEP_TIME_TO_AVOID_USER_RATE_LIMITS_AT_GOOGLE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (Match valueEvent : $(document).xpath("//team[@name='" + teamName + "']/event").each()) {
                addEventIfNeeded(valueEvent, existingEvents, calendarId);
            }
            removeExistingCompetitionEvents(calendarName);
            endTime = System.nanoTime();
            runTime = Math.round((endTime - startTime)/1000000000);
            logger.info("Sync for calendar '"+calendarName+"' completed in "+runTime+" sec");
        }
    }

    /**
     * @param valueEvent
     * @param existingEvents
     * @throws IOException
     * @throws ParseException
     */
    private void addEventIfNeeded(Match valueEvent, Events existingEvents, String calendarId) throws IOException, ParseException {
        String subject = valueEvent.find("subject").content();
        if (logger.isDebugEnabled()) {
            logger.debug("Checking event " + subject);
        }
        Event matchingEvent = null;

        Event event = new Event();
        event.setSummary(subject);
        event.setLocation(valueEvent.find("location").content());
        Date startDate = null;
        Date endDate = null;
        try {
            startDate = df.parse(valueEvent.find("startDateTime").content());
            endDate = df.parse(valueEvent.find("endDateTime").content());
        } catch (ParseException e) {
            logger.error("Unable to parse date for event " + subject);
            throw e;
        }
        DateTime start = new DateTime(startDate, TimeZone.getTimeZone("Europe/Brussels"));
        DateTime end = new DateTime(endDate, TimeZone.getTimeZone("Europe/Brussels"));
        event.setStart(new EventDateTime().setDateTime(start));
        event.setEnd(new EventDateTime().setDateTime(end));

        //Step1. Find event with equal subject (if any)
        //We expect max 1 event with same summary name
        if (existingEvents.getItems() != null) {
            for (Event entry : existingEvents.getItems()) {
                if (entry.getSummary() != null && entry.getSummary().equalsIgnoreCase(subject)) {
                    matchingEvent = entry;
                    break;
                }
            }
        }

        if (matchingEvent != null) {
            //Step2. Check if other eventproperties are equal: {subject equals && startdate equal && location equal }
            if (matchingEvent.getStart() != null
                    && matchingEvent.getStart().getDateTime().getValue() == event.getStart().getDateTime().getValue()
                    && (
                    (matchingEvent.getLocation() != null && matchingEvent.getLocation().equals(event.getLocation()))
                            || (matchingEvent.getLocation() == null && "".equals(event.getLocation()))
            )
                    ) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Event already exists...ignoring");
                }
                return;
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Deleting duplicate event " + subject);
            }

            client.events().delete(calendarId, matchingEvent.getId()).execute();
            try {
                Thread.sleep(SLEEP_TIME_TO_AVOID_USER_RATE_LIMITS_AT_GOOGLE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (logger.isDebugEnabled()) {
            logger.info("Adding new/updated event " + subject);
        }
        client.events().insert(calendarId, event).execute();
        try {
            Thread.sleep(SLEEP_TIME_TO_AVOID_USER_RATE_LIMITS_AT_GOOGLE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param calendarName
     */
    private void removeExistingCompetitionEvents(String calendarName) {


    }

    /**
     * Return calender for a given calendarName. If not existing, calenderName will be created
     *
     * @param calendarName
     * @throws IOException
     */
    private String giveCalendarId(String calendarName, SortedMap<String,String> existingCalendarList) throws IOException {

        boolean needToAdd = true;
        String calendarId = existingCalendarList.get(calendarName.toUpperCase());

        if (calendarId == null || calendarId.length()==0) {
            Calendar entry = new Calendar();
            entry.setSummary(calendarName);

            Calendar result = client.calendars().insert(entry).execute();
            //View.display(result);
            calendarId = result.getId();

            AclRule rule = new AclRule();
            Scope scope = new Scope();

            scope.setType("default");
            //scope.setValue("scopeValue");
            rule.setScope(scope);
            rule.setRole("reader");

            AclRule createdRule = client.acl().insert(calendarId, rule).execute();

        }
        return calendarId;
    }

    /**
     * @param teamName
     * @return
     */
    private String giveCalendarName(String teamName) {
        return teamName + " competitie";
    }

}

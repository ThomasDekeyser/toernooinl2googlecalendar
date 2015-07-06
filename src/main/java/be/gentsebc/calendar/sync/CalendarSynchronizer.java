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
import org.webharvest.definition.ScraperConfiguration;
import org.webharvest.runtime.Scraper;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;

/**
 * @author Thomas Dekeyser
 */
public class CalendarSynchronizer {


    private Document config;
    private Document document;
    private Scraper scraper;
    private static com.google.api.services.calendar.Calendar client;
    static Logger logger = Logger.getLogger(CalendarSynchronizer.class);
    DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static int SLEEP_TIME_TO_AVOID_USER_RATE_LIMITS_AT_GOOGLE = 1000;

    public CalendarSynchronizer(Document myConfig, com.google.api.services.calendar.Calendar myClient) throws IOException {
        config = myConfig;
        client = myClient;
        String webHarvestScaperConfig = $(config).xpath("/config/webHarvest/scraperscript").content();
        boolean webHarvestDebug = $(config).xpath("/config/webHarvest/debug").content().equals("true") ? true : false;
        ScraperConfiguration webHarvestConfig = new ScraperConfiguration(webHarvestScaperConfig);
        scraper = new Scraper(webHarvestConfig, ".");
        scraper.setDebug(webHarvestDebug);
    }

    public void execute() throws SAXException, IOException, ParseException {
        String urlString = $(config).xpath("/config/pbo").content();

        HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
        HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();
        HttpRequest httpRequest = requestFactory.buildGetRequest(new GenericUrl(new URL(urlString)));

        //Parse webHarvest results and sync with googleCalendar
        DocumentBuilder builder = JOOX.builder();
        document = builder.parse(httpRequest.execute().getContent());
        syncTeams();
    }


    public void executeWebHarvest() throws SAXException, IOException, ParseException {
        String webHarvestTmp = $(config).xpath("/config/webHarvest/tmpdir").content();

        for (Match valueCalendar : $(config).xpath("//calendar").each()) {
            String competitionId = valueCalendar.find("competitionId").content();
            String clubId = valueCalendar.find("clubId").content();
            String eventsXML = webHarvestTmp + "gentseBcCalendar_" + competitionId + ".xml";
            logger.info("Start syncing calendar for clubId '" + clubId + "' on competionId '" + competitionId + "'. (tmp file '" + eventsXML + "')");

            //Run webHarvest script
            scraper.addVariableToContext("eventsXML", eventsXML);
            scraper.addVariableToContext("competitionId", competitionId);
            scraper.addVariableToContext("clubId", clubId);
            scraper.execute();

            //Parse webHarvest results and sync with googleCalendar
            DocumentBuilder builder = JOOX.builder();
            document = builder.parse(new File(eventsXML));
            syncTeams();
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

    public void syncTeams() throws IOException, ParseException {
        long startTime,endTime=0L;
        int runTime=0;
        for (Match value : $(document).xpath("//team").each()) {
            startTime = System.nanoTime();
            String teamName = value.attr("name");
            String calendarName = giveCalendarName(teamName);
            String calendarId = giveCalendarId(calendarName);
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
            startDate = df.parse(valueEvent.find("startDate").content() + " " + valueEvent.find("startTime").content());
            endDate = df.parse(valueEvent.find("startDate").content() + " " + valueEvent.find("endTime").content());
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
    private String giveCalendarId(String calendarName) throws IOException {
        CalendarList feed = client.calendarList().list().execute();

        boolean needToAdd = true;
        String calendarId = "";
        if (feed.getItems() != null) {
            for (CalendarListEntry entry : feed.getItems()) {
                if (entry.getSummary().equalsIgnoreCase(calendarName)) {
                    calendarId = entry.getId();
                    needToAdd = false;
                    break;
                }

            }
        }

        if (needToAdd) {
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

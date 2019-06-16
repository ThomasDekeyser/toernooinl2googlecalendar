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

import com.google.common.collect.ImmutableList;
import org.apache.log4j.Logger;
import org.joox.JOOX;
import org.joox.Match;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

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


    private static Logger logger = Logger.getLogger(CalendarSynchronizer.class);
    private static com.google.api.services.calendar.Calendar client;
    private static int SLEEP_TIME_TO_AVOID_USER_RATE_LIMITS_AT_GOOGLE = 1000;
    private static List<String> CALENDAR_NAMES_TO_IGNORE = ImmutableList.<String>builder()
            .add("pbo.competitie.agenda@gmail.com")
            .add("Feestdagen in België")
            .build();


    private DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private Document config;
    private Document document;

    public CalendarSynchronizer(Document myConfig, com.google.api.services.calendar.Calendar myClient) throws IOException {
        config = myConfig;
        client = myClient;
    }

    void execute() throws SAXException, IOException, ParseException {
        String urlString = $(config).xpath("/config/pbo").content();

        HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
        HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();
        HttpRequest httpRequest = requestFactory.buildGetRequest(new GenericUrl(new URL(urlString)));

        //Parse webHarvest results and sync with googleCalendar
        DocumentBuilder builder = JOOX.builder();
        document = builder.parse(httpRequest.execute().getContent());
        List<TeamCalendar> existingCalendarList = giveExistingCalendars();
        syncTeams(existingCalendarList);
        report("-------------Active teams with an existing calendar", giveActiveTeamsWithExistingCalender());
    }


    private List<TeamCalendar> giveExistingCalendars() throws IOException, ParseException{

        List<TeamCalendar> teamCalendars = new ArrayList<>();

        CalendarList feed = getCalendarListAndAddToGivenList(teamCalendars,null);
        while (feed.getNextPageToken() != null) {
            feed = getCalendarListAndAddToGivenList(teamCalendars,feed.getNextPageToken());
        }

        teamCalendars.sort(new TeamCalendarComparator());

        return teamCalendars;
    }

    private CalendarList getCalendarListAndAddToGivenList(List<TeamCalendar> myCalendarList, String nextPageToken) throws IOException {
        //Build call
        com.google.api.services.calendar.Calendar.CalendarList.List calendarList = client.calendarList().list().setMaxResults(250);
        if (nextPageToken != null) {
            calendarList.setPageToken(nextPageToken);
        }

        //Execte call
        CalendarList feed = calendarList.execute();

        //Add to map
        addCalenderItemsToGivenList(feed, myCalendarList);
        return feed;
    }

    private void addCalenderItemsToGivenList(CalendarList feed, List<TeamCalendar> myCalendarList) {
        if (feed.getItems() != null) {
            for (CalendarListEntry entry : feed.getItems()) {
                if (!CALENDAR_NAMES_TO_IGNORE.contains(entry.getSummary())) {
                    try {
                        TeamCalendar teamCalendar = new TeamCalendar(entry.getSummary(), entry.getId());
                        if (myCalendarList.contains(teamCalendar)) {
                            logger.error("Duplicate calender found for key "+entry.getSummary()+". Please manually removed one of them because this only one of them will be updated and no garantees that it will be always the same one.");
                            System.exit(-1);
                        }
                        myCalendarList.add(new TeamCalendar(entry.getSummary(),entry.getId()));
                    } catch (IndexOutOfBoundsException | NumberFormatException e) {
                        logger.warn("Unable to parse team calendar with name "+ entry.getSummary());
                    }
                }
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

    void syncTeams(List<TeamCalendar> existingCalendarList) throws IOException, ParseException {
        long startTime,endTime=0L;
        int runTime=0;
        for (Match value : $(document).xpath("//team").each()) {
            startTime = System.nanoTime();
            String teamName = value.attr("name");
            TeamCalendar teamCalendar = new TeamCalendar(teamName);

            String calendarId = createRemoteCalendarIfNonExisting(teamCalendar, existingCalendarList);
            Events existingEvents = client.events().list(calendarId).execute();
            logger.info("Starting sync for calendar "+teamCalendar);
            try {
                Thread.sleep(SLEEP_TIME_TO_AVOID_USER_RATE_LIMITS_AT_GOOGLE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (Match valueEvent : $(document).xpath("//team[@name='" + teamName + "']/event").each()) {
                addEventIfNeeded(valueEvent, existingEvents, calendarId);
            }
            endTime = System.nanoTime();
            runTime = Math.round((endTime - startTime)/1000000000);
            logger.info("Sync for calendar '"+teamCalendar.getTeamName()+"' completed in "+runTime+" sec");
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
     * Return calender for a given teamCalendar. If not existing, calenderName will be created
     *
     * @throws IOException
     */
    private String createRemoteCalendarIfNonExisting(TeamCalendar teamCalendarToFind, List<TeamCalendar> existingCalendarList) {

        return existingCalendarList.stream()
                .filter(teamCalendar -> teamCalendar.getGoogleTeamCalendarName().equalsIgnoreCase(teamCalendarToFind.getGoogleTeamCalendarName()))
                .findAny()
                .orElseGet(() -> {
                    createTeamCalendar(teamCalendarToFind);
                    return teamCalendarToFind;
                })
                .getGoogleCalendarId();
    }


    private static String createTeamCalendar(TeamCalendar teamCalendar) {
        try {
            Calendar entry = new Calendar();
            entry.setSummary(teamCalendar.getGoogleTeamCalendarName());

            Calendar result = client.calendars().insert(entry).execute();
            //View.display(result);
            String calendarId = result.getId();

            AclRule rule = new AclRule();
            Scope scope = new Scope();

            scope.setType("default");
            //scope.setValue("scopeValue");
            rule.setScope(scope);
            rule.setRole("reader");

            AclRule createdRule = client.acl().insert(calendarId, rule).execute();

            teamCalendar.setGoogleCalendarId(calendarId);

            return calendarId;

        } catch (IOException e) {
            throw new RuntimeException("Failed to create new calendar", e);
        }

    }


    private void report(String header, List<TeamCalendar> teamCalendars) {
        logger.info(header);
        teamCalendars.stream()
                .filter( teamCalendar -> !teamCalendar.getClubName().startsWith("TESTCLUB"))
                .forEach(teamCalendar -> logger.info(String.format("%s,%s", teamCalendar.getTeamName() ,teamCalendar.getGoogleCalendarId())));
    }

    private List<TeamCalendar> giveActiveTeamsWithExistingCalender() throws IOException, ParseException {
        List<TeamCalendar> result = new ArrayList<>();

        List<TeamCalendar> teamCalendars = giveExistingCalendars();

        $(document).xpath("//team").each().forEach(value -> {
            String teamName = value.attr("name");
            TeamCalendar teamCalendar = new TeamCalendar(teamName);
            teamCalendars.stream()
                    .filter(t -> t.getGoogleTeamCalendarName().equalsIgnoreCase(teamCalendar.getGoogleTeamCalendarName()))
                    .findAny()
                    .ifPresent(result::add);
        });
        result.sort(new TeamCalendarComparator());
        return result;
    }


}

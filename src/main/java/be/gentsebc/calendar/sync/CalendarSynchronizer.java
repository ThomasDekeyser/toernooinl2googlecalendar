package be.gentsebc.calendar.sync;

import static org.joox.JOOX.$;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.joox.Match;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.AclRule;
import com.google.api.services.calendar.model.AclRule.Scope;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.common.base.Strings;

/**
 * @author Thomas Dekeyser
 */
public class CalendarSynchronizer {


    private static final Logger logger = Logger.getLogger(CalendarSynchronizer.class);
    private static com.google.api.services.calendar.Calendar client;

    private static final DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private static final TimeZone brussels = TimeZone.getTimeZone("Europe/Brussels");

    private static final DateFormat CSV_IN = new SimpleDateFormat("d-M-yyyy H:mm:ss");
    private static final DateFormat XML_OUT = new SimpleDateFormat("d/M/yyyy H:mm:ss");

    static {
        df.setTimeZone(brussels);
        CSV_IN.setTimeZone(brussels);
        XML_OUT.setTimeZone(brussels);
    }

    private final Document config;
    private Document document;
    private final ToernooiNlGrapper toernooiNlGrapper;
    private final int sleepTimeToAvoidGoogleRateLimitsMs;

    public CalendarSynchronizer(Document myConfig, com.google.api.services.calendar.Calendar myClient) throws IOException {
        config = myConfig;
        client = myClient;
        sleepTimeToAvoidGoogleRateLimitsMs = Integer.parseInt($(config).xpath("/config/sleepTimeToAvoidGoogleRateLimitMs").content());
        this.toernooiNlGrapper = new ToernooiNlGrapper($(config).xpath("/config/pboCompetitionId").content());
    }

    void execute() throws SAXException, IOException, ParseException {
        String csvPath = $(config).xpath("/config/matchesCsv").content();

        document = buildDocumentFromCsv(csvPath);
        writeDocumentToXml(document, csvPath);
        List<TeamCalendar> existingCalendarList = new GoogleCalenderFetcher(client).giveExistingCalendars();
        syncTeams(existingCalendarList);
        report("-------------Active teams with an existing calendar", giveActiveTeamsWithExistingCalender());
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

            //Only look at events starting from a year ago.
            DateTime filterStartDate = new DateTime(Date.from(LocalDate.now().minusDays(365).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            Events existingEvents = client.events().list(calendarId).setMaxResults(2500).setTimeMin(filterStartDate).execute();
            logger.info("Starting sync for calendar " + teamCalendar);
            try {
                Thread.sleep(sleepTimeToAvoidGoogleRateLimitsMs);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (Match valueEvent : $(document).xpath("//team[@name='" + teamName + "']/event").each()) {
                addEventIfNeeded(valueEvent, existingEvents, calendarId);
            }
            endTime = System.nanoTime();
            runTime = Math.round((endTime - startTime) / 1000000000);
            logger.info("Sync for calendar '" + teamCalendar.getTeamName() + "' completed in " + runTime + " sec");
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
        String locationDescription = valueEvent.find("location").content();
        locationDescription = Strings.isNullOrEmpty(locationDescription) && !Strings.isNullOrEmpty(valueEvent.find("matchId").content()) ?
                              this.toernooiNlGrapper.getLocationDescription(valueEvent.find("matchId").content()) : locationDescription;
        List<Event> matchingEvents = new ArrayList<>();

        Event event = new Event();
        event.setSummary(subject);
        event.setLocation(locationDescription);
        Date startDate = null;
        Date endDate = null;
        try {
            startDate = df.parse(valueEvent.find("startDateTime").content());
            endDate = df.parse(valueEvent.find("endDateTime").content());
        } catch (ParseException e) {
            logger.error("Unable to parse date for event " + subject);
            throw e;
        }
        DateTime start = new DateTime(startDate, brussels);
        DateTime end = new DateTime(endDate, brussels);
        event.setStart(new EventDateTime().setDateTime(start));
        event.setEnd(new EventDateTime().setDateTime(end));

        //Step1. Find event with equal subject (if any)
        //We expect max 1 event with same summary name
        if (existingEvents.getItems() != null) {
            for (Event entry : existingEvents.getItems()) {
                if (entry.getSummary() != null && entry.getSummary().equalsIgnoreCase(subject)) {
                    matchingEvents.add(entry);
                }
            }
        }


        if (matchingEvents.size() > 0) {

            for (int i = 0; i < matchingEvents.size() - 1; i++) {
                logger.info("Deleting duplicate event " +i+ ":" + subject);
                try {
                    client.events().delete(calendarId, matchingEvents.get(i).getId()).execute();
                } catch (GoogleJsonResponseException ex) {
                   if (ex.getStatusCode() == 410) {
                       logger.debug("Event "+ i+ "already deleted");
                   }
                }

            }


            Event matchingEvent = matchingEvents.get(matchingEvents.size()-1);
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
                Thread.sleep(sleepTimeToAvoidGoogleRateLimitsMs);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (logger.isDebugEnabled()) {
            logger.info("Adding new/updated event " + subject);
        }
        client.events().insert(calendarId, event).execute();
        try {
            Thread.sleep(sleepTimeToAvoidGoogleRateLimitsMs);
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

        List<TeamCalendar> teamCalendars = new GoogleCalenderFetcher(client).giveExistingCalendars();

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

    static String stripTeamIndex(String rawName) {
        return rawName.replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
    }

    static String toXmlStart(String plannedTime) throws ParseException {
        return XML_OUT.format(CSV_IN.parse(plannedTime));
    }

    static String toXmlEnd(String plannedTime) throws ParseException {
        Date start = CSV_IN.parse(plannedTime);
        return XML_OUT.format(new Date(start.getTime() + 3L * 60 * 60 * 1000));
    }

    private static final class EventRow {
        final String matchId;
        final String subject;
        final String start;
        final String end;
        EventRow(String matchId, String subject, String start, String end) {
            this.matchId = matchId;
            this.subject = subject;
            this.start = start;
            this.end = end;
        }
    }

    static Document buildDocumentFromCsv(String csvPath) throws IOException, ParseException {
        List<String> lines = Files.readAllLines(Paths.get(csvPath), StandardCharsets.UTF_8);
        Map<String, List<EventRow>> teamToEvents = new TreeMap<>();

        for (int i = 1; i < lines.size(); i++) { // skip header
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] c = line.split(";", -1);
            if (c.length < 17) {
                continue;
            }
            String matchId = c[1];
            String team1 = stripTeamIndex(c[11]);
            String team2 = stripTeamIndex(c[13]);
            String plannedTime = c[16];
            String subject = team1 + " - " + team2;
            EventRow row = new EventRow(matchId, subject, toXmlStart(plannedTime), toXmlEnd(plannedTime));

            teamToEvents.computeIfAbsent(team1, k -> new ArrayList<>()).add(row);
            teamToEvents.computeIfAbsent(team2, k -> new ArrayList<>()).add(row);
        }

        List<Match> teamElements = new ArrayList<>();
        for (Map.Entry<String, List<EventRow>> entry : teamToEvents.entrySet()) {
            List<Match> eventElements = new ArrayList<>();
            for (EventRow row : entry.getValue()) {
                eventElements.add($("event",
                        $("matchId", row.matchId),
                        $("subject", row.subject),
                        $("location", ""),
                        $("startDateTime", row.start),
                        $("endDateTime", row.end)));
            }
            teamElements.add($("team", eventElements.toArray(new Match[0])).attr("name", entry.getKey()));
        }
        return $("teams", teamElements.toArray(new Match[0])).document();
    }

    /**
     * Serialize the built sync document to an XML file next to the incoming CSV
     * (same directory, same base name with a {@code .xml} extension), so the
     * generated data can be inspected and reused as the faster XML input path.
     */
    static void writeDocumentToXml(Document document, String csvPath) throws IOException {
        Path csv = Paths.get(csvPath);
        String fileName = csv.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        Path xmlPath = csv.resolveSibling(base + ".xml");

        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(document), new StreamResult(xmlPath.toFile()));
        } catch (TransformerException e) {
            throw new IOException("Failed to write sync XML to " + xmlPath, e);
        }
        logger.info("Wrote sync XML to " + xmlPath);
    }

}

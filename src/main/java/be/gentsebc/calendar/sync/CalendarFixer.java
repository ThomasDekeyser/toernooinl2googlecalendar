package be.gentsebc.calendar.sync;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.common.collect.ImmutableList;
import org.apache.log4j.Logger;
import org.joox.JOOX;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.joox.JOOX.$;

/**
 * @author Thomas Dekeyser
 */
public class CalendarFixer {


    private static Logger logger = Logger.getLogger(CalendarFixer.class);
    private static com.google.api.services.calendar.Calendar client;

    private static List<String> TEAMS_TO_CHECK = ImmutableList.<String>builder()
            .add("Latem-De Pinte 4G")
            .add("Danlie 1G")
            .add("Drive 3G")
            .add("Gentse 4G")
            .add("Landegem 3G")
            .add("Latem-De Pinte 3G")
            .add("Lokerse 6G")
            .add("Pluimplukkers 7G")
            .add("Wetteren 2G")
            .add("4Ghent 2G")
            .add("Eikenlo 2G")
            .add("Hoge Wal 2G")
            .add("Pluimplukkers 6G")
            .add("Smash For Fun 1G")
            .add("Stekene 1G")
            .add("Wetteren 1G")
            .add("4Ghent 3G")
            .add("Aalsterse 1G")
            .add("Denderleeuw 1G")
            .add("Drive 4G")
            .add("Drive 5G")
            .add("Geraardsbergen 1G")
            .add("Lokerse 5G")
            .add("VlaBad 1G")
            .build();


    private Document config;
    private Document document;

    /*
    * Remove obsolete events (from the future) from a given list of calenders. This should only be run manually when needed.
    * Usecase:  when team was move to another "reeks", when team was removed from a "reeks"
     */
    public CalendarFixer(Document myConfig, com.google.api.services.calendar.Calendar myClient) throws IOException {
        config = myConfig;
        client = myClient;
    }

    void execute() throws SAXException, IOException, ParseException {
        logger.info("Running calendar fixer for teams "+ TEAMS_TO_CHECK);
        String urlString = $(config).xpath("/config/pbo").content();

        HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
        HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();
        HttpRequest httpRequest = requestFactory.buildGetRequest(new GenericUrl(new URL(urlString)));

        //Parse webHarvest results and sync with googleCalendar
        DocumentBuilder builder = JOOX.builder();
        document = builder.parse(httpRequest.execute().getContent());
        List<TeamCalendar> existingCalendarList = new GoogleCalenderFetcher(client).giveExistingCalendars();

        List<TeamCalendar> existingCalendarListToCheck = existingCalendarList.stream()
                .filter(t -> TEAMS_TO_CHECK.contains(t.getTeamName()))
                .collect(Collectors.toList());

        fixTeams(existingCalendarListToCheck);
    }



    void fixTeams(List<TeamCalendar> existingCalendarList) throws IOException, ParseException {
        Date now = new Date();

        for(TeamCalendar teamCalendar: existingCalendarList) {
            Events existingEvents = client.events().list(teamCalendar.getGoogleCalendarId()).execute();

            List<String> ontmoetingThatAreCorrect = $(document).xpath("//team[@name='" + teamCalendar.getTeamName() + "']/event").each().stream()
                    .map(match -> match.find("subject").content().toUpperCase())
                    .collect(Collectors.toList());


            List<Event> eventsToRemove = existingEvents.getItems().stream()
                    .filter(e -> e.getStart().getDateTime().getValue() > now.getTime())
                    .filter(e -> !ontmoetingThatAreCorrect.contains(e.getSummary().toUpperCase()))
                    .collect(Collectors.toList());

            if (!eventsToRemove.isEmpty()) {
                logger.info("Removing "+eventsToRemove.size() +" calender items for team "+ teamCalendar.getTeamName());
            }

            eventsToRemove.forEach(e -> {
                        logger.debug("Need to remove ontmoeting '"+ e.getSummary()+"' on '"+e.getStart()+"' for team '"+teamCalendar.getTeamName()+"'" );
                try {
                    client.events().delete(teamCalendar.getGoogleCalendarId(), e.getId()).execute();
                } catch (IOException e1) {
                    logger.error("Unable to remove ontmoeting '"+ e.getSummary()+"' on '"+e.getStart()+"' for team '"+teamCalendar.getTeamName()+"'" );
                }
            });

        }

    }

}

package be.gentsebc.calendar.sync;

import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.common.collect.ImmutableList;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class GoogleCalenderFetcher {

    private static List<String> CALENDAR_NAMES_TO_IGNORE = ImmutableList.<String>builder()
            .add("pbo.competitie.agenda@gmail.com")
            .add("Feestdagen in België")
            .build();

    private static Logger LOG = Logger.getLogger(CalendarFixer.class);

    private com.google.api.services.calendar.Calendar client;

    public GoogleCalenderFetcher(com.google.api.services.calendar.Calendar client) {
        this.client = client;
    }

    public List<TeamCalendar> giveExistingCalendars() throws IOException, ParseException {

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
                            LOG.error("Duplicate calender found for key "+entry.getSummary()+". Please manually removed one of them because this only one of them will be updated and no garantees that it will be always the same one.");
                            System.exit(-1);
                        }
                        myCalendarList.add(new TeamCalendar(entry.getSummary(),entry.getId()));
                    } catch (IndexOutOfBoundsException | NumberFormatException e) {
                        LOG.warn("Unable to parse team calendar with name "+ entry.getSummary());
                    }
                }
            }
        }
    }

}

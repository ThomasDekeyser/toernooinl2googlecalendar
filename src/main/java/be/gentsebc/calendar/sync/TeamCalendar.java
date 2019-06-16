package be.gentsebc.calendar.sync;

import com.google.common.base.Objects;
import lombok.*;
import org.apache.log4j.Logger;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TeamCalendar {

    private static Logger LOGGER = Logger.getLogger(TeamCalendar.class);
    private static String GOOGLE_CALENDAR_NAME_SUFFIX = "competitie";
    private static String GOOGLE_CALENDAR_NAME_SUFFIX_UPPER = GOOGLE_CALENDAR_NAME_SUFFIX.toUpperCase();


    private String googleTeamCalendarName;
    private String googleCalendarId;
    private String teamName;
    private String clubName;
    private String competitionType;
    private Integer teamNumber;

    public TeamCalendar(String calendarName) {
        this(calendarName, null);
    }


    public TeamCalendar(String googleTeamCalendarName, String googleCalendarId) {
        this.googleTeamCalendarName = getGoogleCalendarName(googleTeamCalendarName);
        this.googleCalendarId = googleCalendarId;

        parse();
    }

    private static String getGoogleCalendarName(String calendarName) {
        if (calendarName.endsWith(GOOGLE_CALENDAR_NAME_SUFFIX) || calendarName.endsWith(GOOGLE_CALENDAR_NAME_SUFFIX_UPPER)) {
            return calendarName;
        } else {
            return (calendarName.trim() + " " + GOOGLE_CALENDAR_NAME_SUFFIX).trim();
        }
    }

    private void parse() throws IndexOutOfBoundsException, NumberFormatException{
        this.teamName = googleTeamCalendarName.replaceAll(GOOGLE_CALENDAR_NAME_SUFFIX, "").replaceAll(GOOGLE_CALENDAR_NAME_SUFFIX_UPPER,"").trim();
        int indexOfTeamNumber = teamName.lastIndexOf(" ") + 1;

        this.clubName = teamName.substring(0, indexOfTeamNumber).trim();
        String competitionTypeAndTeamNumber = teamName.substring(indexOfTeamNumber);
        this.competitionType = competitionTypeAndTeamNumber.substring(competitionTypeAndTeamNumber.length()-1);
        String teamNumber = competitionTypeAndTeamNumber.substring(0, competitionTypeAndTeamNumber.length()-1);
        this.teamNumber = new Integer(teamNumber);
        if (this.clubName == null || this.competitionType == null) {
            LOGGER.error("Unable to resolve clubName, competitionType of teamNumber from "+ googleTeamCalendarName);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamCalendar team = (TeamCalendar) o;
        return Objects.equal(googleTeamCalendarName, team.googleTeamCalendarName) &&
                Objects.equal(googleCalendarId, team.googleCalendarId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(googleTeamCalendarName, googleCalendarId);
    }
}

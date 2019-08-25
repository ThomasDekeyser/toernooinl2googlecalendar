package be.gentsebc.calendar.sync;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TeamNameMapper {

    private Map<String,String> teamNameAccordingVBL, teamNameAccordingGoogleCalendar;

    public TeamNameMapper(List<String> teamNameAccordingVBL, List<String> teamNameAccordingGoogleCalendar) {
        this.teamNameAccordingVBL = teamNameAccordingVBL.stream()
                .collect(Collectors.toMap(String::toUpperCase, x -> x));
        this.teamNameAccordingGoogleCalendar = teamNameAccordingGoogleCalendar.stream()
                .collect(Collectors.toMap(String::toUpperCase, x -> x));
    }

    public String getTeamNameAccordingVBL(String teamNameAccordingGoogleCalendar) {
        return teamNameAccordingVBL.get(teamNameAccordingGoogleCalendar.toUpperCase());
    }

    public String getTeamNameAccordingGoogleCalendar(String teamNameAccordingVBL) {
        return teamNameAccordingGoogleCalendar.get(teamNameAccordingVBL.toUpperCase());
    }
}

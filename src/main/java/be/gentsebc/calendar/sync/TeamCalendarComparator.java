package be.gentsebc.calendar.sync;

import com.google.common.collect.ImmutableBiMap;
import org.apache.log4j.Logger;

import java.util.Comparator;
import java.util.Map;

/**
 * Created by thomas on 6/16/19.
 */
public class TeamCalendarComparator implements Comparator<TeamCalendar> {
    private static Logger LOGGER = Logger.getLogger(TeamCalendarComparator.class);

    private static Map<String,Integer> COMPETITION_TYPE_SORT_ORDER = new ImmutableBiMap.Builder<String,Integer>()
            .put("H",1)
            .put("D",2)
            .put("G",3)
            .build();

    private static final Comparator<TeamCalendar> BY_CLUB_NAME = Comparator.comparing(TeamCalendar::getClubName);

    private static final Comparator<TeamCalendar> BY_COMPETITION_TYPE =
            Comparator.comparing(team -> COMPETITION_TYPE_SORT_ORDER.get(team.getCompetitionType()));

    private static final Comparator<TeamCalendar> BY_TEAM_NUMBER =
            Comparator.comparing(TeamCalendar::getTeamNumber);

    private static final Comparator<TeamCalendar> TEAM_COMPARATOR = BY_CLUB_NAME.thenComparing(BY_COMPETITION_TYPE).thenComparing(BY_TEAM_NUMBER);

    @Override
    public int compare(TeamCalendar t1, TeamCalendar t2) {
        return TEAM_COMPARATOR.compare(t1, t2);
    }
}

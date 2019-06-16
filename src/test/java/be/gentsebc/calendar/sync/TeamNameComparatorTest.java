package be.gentsebc.calendar.sync;


import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;

public class TeamNameComparatorTest {

    private TeamCalendarComparator teamCalendarComparator;

    @Before
    public void init(){
        teamCalendarComparator = new TeamCalendarComparator();
    }

    @Test
    public void testComparator() {
        List<TeamCalendar> teams = Arrays.asList(
                new TeamCalendar("4GHENT 1H COMPETITIE"),
                new TeamCalendar("4GHENT 1G COMPETITIE"),
                new TeamCalendar("4GHENT 2D COMPETITIE"),
                new TeamCalendar("4GHENT 2G COMPETITIE"),
                new TeamCalendar("4GHENT 2H COMPETITIE"),
                new TeamCalendar("AALSTERSE 1G COMPETITIE"),
                new TeamCalendar("GENTSE 1D COMPETITIE"),
                new TeamCalendar("GENTSE 1G COMPETITIE"),
                new TeamCalendar("GENTSE 1H COMPETITIE"),
                new TeamCalendar("GENTSE 2D COMPETITIE"),
                new TeamCalendar("GENTSE 2G COMPETITIE"),
                new TeamCalendar("GENTSE 2H COMPETITIE"),
                new TeamCalendar("GENTSE 3D COMPETITIE"),
                new TeamCalendar("GENTSE 3G COMPETITIE"),
                new TeamCalendar("GENTSE 3H COMPETITIE"),
                new TeamCalendar("AALSTERSE 1H COMPETITIE"),
                new TeamCalendar("AALSTERSE 2G COMPETITIE"),
                new TeamCalendar("AALSTERSE 2H COMPETITIE"),
                new TeamCalendar("AALSTERSE 3H COMPETITIE"),
                new TeamCalendar("AALSTERSE 4H COMPETITIE")
        );

        teams.sort(teamCalendarComparator);
        assertThat(teams, Matchers.contains(
                new TeamCalendar("4GHENT 1H COMPETITIE"),
                new TeamCalendar("4GHENT 2H COMPETITIE"),
                new TeamCalendar("4GHENT 2D COMPETITIE"),
                new TeamCalendar("4GHENT 1G COMPETITIE"),
                new TeamCalendar("4GHENT 2G COMPETITIE"),
                new TeamCalendar("AALSTERSE 1H COMPETITIE"),
                new TeamCalendar("AALSTERSE 2H COMPETITIE"),
                new TeamCalendar("AALSTERSE 3H COMPETITIE"),
                new TeamCalendar("AALSTERSE 4H COMPETITIE"),
                new TeamCalendar("AALSTERSE 1G COMPETITIE"),
                new TeamCalendar("AALSTERSE 2G COMPETITIE"),
                new TeamCalendar("GENTSE 1H COMPETITIE"),
                new TeamCalendar("GENTSE 2H COMPETITIE"),
                new TeamCalendar("GENTSE 3H COMPETITIE"),
                new TeamCalendar("GENTSE 1D COMPETITIE"),
                new TeamCalendar("GENTSE 2D COMPETITIE"),
                new TeamCalendar("GENTSE 3D COMPETITIE"),
                new TeamCalendar("GENTSE 1G COMPETITIE"),
                new TeamCalendar("GENTSE 2G COMPETITIE"),
                new TeamCalendar("GENTSE 3G COMPETITIE"))
        );
    }

    @Test
    public void testWithTeamIndexesBiggerThanTen() {
        List<TeamCalendar> teams = Arrays.asList(
                new TeamCalendar("4GHENT 1H COMPETITIE"),
                new TeamCalendar("4GHENT 12H COMPETITIE"),
                new TeamCalendar("4GHENT 10H COMPETITIE"),
                new TeamCalendar("4GHENT 11H COMPETITIE"),
                new TeamCalendar("4GHENT 2H COMPETITIE")
        );
        teams.sort(teamCalendarComparator);
        assertThat(teams, Matchers.contains(
                new TeamCalendar("4GHENT 1H COMPETITIE"),
                new TeamCalendar("4GHENT 2H COMPETITIE"),
                new TeamCalendar("4GHENT 10H COMPETITIE"),
                new TeamCalendar("4GHENT 11H COMPETITIE"),
                new TeamCalendar("4GHENT 12H COMPETITIE")
        ));

    }

}
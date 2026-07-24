package be.gentsebc.calendar.sync;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

import java.text.ParseException;
import org.junit.Test;

public class CsvToXmlTest {

    @Test
    public void stripsTrailingTeamIndex() {
        assertThat(CalendarSynchronizer.stripTeamIndex("Dendermondse 5G (102)"), is("Dendermondse 5G"));
        assertThat(CalendarSynchronizer.stripTeamIndex("4Ghent 1G (73)"), is("4Ghent 1G"));
    }

    @Test
    public void leavesNameWithoutIndexUntouched() {
        assertThat(CalendarSynchronizer.stripTeamIndex("Gentse 2G"), is("Gentse 2G"));
    }

    @Test
    public void formatsStartDateTime() throws ParseException {
        assertThat(CalendarSynchronizer.toXmlStart("20-9-2026 19:00:00"), is("20/9/2026 19:00:00"));
    }

    @Test
    public void formatsEndDateTimePlusThreeHours() throws ParseException {
        assertThat(CalendarSynchronizer.toXmlEnd("20-9-2026 19:00:00"), is("20/9/2026 22:00:00"));
    }
}
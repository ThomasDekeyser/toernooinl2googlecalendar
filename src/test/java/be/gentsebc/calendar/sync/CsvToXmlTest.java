package be.gentsebc.calendar.sync;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.joox.JOOX.$;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;

import org.joox.Match;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;

public class CsvToXmlTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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

    @Test
    public void buildsTeamsDocumentWithBothTeamsPerMatch() throws IOException, ParseException {
        Document doc = CalendarSynchronizer.buildDocumentFromCsv("src/test/resources/matches-sample.csv");

        // 3 distinct teams
        assertThat($(doc).xpath("//team").size(), is(3));
        // Gentse 2G appears as team1 in both rows -> 2 events
        assertThat($(doc).xpath("//team[@name='Gentse 2G']/event").size(), is(2));
        // Away team from row 1 also has the match
        assertThat($(doc).xpath("//team[@name='De Mintons 3G']/event").size(), is(1));

        Match firstEvent = $(doc).xpath("//team[@name='De Mintons 3G']/event").first();
        assertThat(firstEvent.find("matchId").content(), is("1137"));
        assertThat(firstEvent.find("subject").content(), is("Gentse 2G - De Mintons 3G"));
        assertThat(firstEvent.find("location").content(), is(""));
        assertThat(firstEvent.find("startDateTime").content(), is("20/9/2026 19:00:00"));
        assertThat(firstEvent.find("endDateTime").content(), is("20/9/2026 22:00:00"));
    }

    @Test
    public void writesXmlSiblingToCsv() throws Exception {
        File csv = tmp.newFile("matches.csv");
        Files.copy(new File("src/test/resources/matches-sample.csv").toPath(), csv.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        Document doc = CalendarSynchronizer.buildDocumentFromCsv(csv.getAbsolutePath());
        CalendarSynchronizer.writeDocumentToXml(doc, csv.getAbsolutePath());

        File xml = new File(csv.getParentFile(), "matches.xml");
        assertThat(xml.exists(), is(true));

        // Re-parse the written file to confirm it is valid and preserved the data.
        Document reparsed = $(xml).document();
        assertThat($(reparsed).xpath("//team").size(), is(3));
        assertThat($(reparsed).xpath("//team[@name='De Mintons 3G']/event").find("subject").content(),
                is("Gentse 2G - De Mintons 3G"));
    }
}
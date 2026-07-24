# CSV → XML conversion in CalendarSynchronizer

## Goal

Replace the toernooi.nl URL fetch in `CalendarSynchronizer.execute()` with an
in-memory conversion of a local matches CSV (`data/tmp/matches.csv`) into the
`org.w3c.dom.Document` that the existing sync logic already consumes. The XML is
built with JOOX (already a project dependency).

## Input

`data/tmp/matches.csv` — semicolon (`;`) delimited, UTF-8 with a BOM on the header
line. ~1230 data rows, 190 distinct teams. Header row is skipped (BOM therefore
irrelevant). All rows have a populated `plannedtime`; all team names carry a
` (NNN)` index suffix.

Relevant columns (0-based index):

| Index | Column | Use |
|-------|--------|-----|
| 1 | `matchid` | `<matchId>` |
| 11 | `team1name` | team element + subject (left side) |
| 13 | `team2name` | team element + subject (right side) |
| 16 | `plannedtime` | `<startDateTime>` / `<endDateTime>` |

## Output XML structure

```xml
<teams>
  <team name="Gentse 2G">
    <event>
      <matchId>1137</matchId>
      <subject>Gentse 2G - De Mintons 3G</subject>
      <location></location>
      <startDateTime>20/9/2026 19:00:00</startDateTime>
      <endDateTime>20/9/2026 22:00:00</endDateTime>
    </event>
    ...
  </team>
  <team name="...">...</team>
</teams>
```

This matches the structure the existing `syncTeams()` / `addEventIfNeeded()` read
via XPath (`//team`, and per-event `matchId`, `subject`, `location`,
`startDateTime`, `endDateTime`).

## Field mapping / transforms

- **Team name**: strip the trailing index — `"Dendermondse 5G (102)"` →
  `"Dendermondse 5G"`. Regex `\s*\(\d+\)\s*$` on both `team1name` and `team2name`.
- **subject**: `<clean team1> - <clean team2>`. The **same** subject string is used
  in both teams' calendars.
- **location**: empty element. (`addEventIfNeeded()` still scrapes the real
  location from toernooi.nl via `ToernooiNlGrapper` because `location` is empty and
  `matchId` is present — unchanged existing behavior.)
- **startDateTime**: parse `plannedtime` with format `d-M-yyyy H:mm:ss`
  (Europe/Brussels), re-emit as `d/M/yyyy H:mm:ss`.
- **endDateTime**: start + 3 hours, same output format.

The existing downstream parser `df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")`
parses the single-digit `d/M/yyyy H:mm:ss` output correctly (SimpleDateFormat is
lenient), so no downstream change is required.

## Match scope

Each CSV row (one match) is emitted as an `<event>` under **both** team1's and
team2's `<team>` element, so every team calendar contains its home *and* away
games.

## XML construction

JOOX fluent builder. Teams are accumulated in a `TreeMap<String, List<EventRow>>`
(alphabetical order, events grouped per team). A small private value holder
`EventRow` carries `matchId`, `subject`, `start`, `end`.

```java
Match teams = $("teams");
teamMap.forEach((name, events) -> {
    Match teamEl = $("team").attr("name", name);
    for (EventRow e : events) {
        teamEl.append($("event")
            .append($("matchId").content(e.matchId))
            .append($("subject").content(e.subject))
            .append($("location").content(""))
            .append($("startDateTime").content(e.start))
            .append($("endDateTime").content(e.end)));
    }
    teams.append(teamEl);
});
return teams.document();
```

(The exact JOOX element-creation API — `$(String)`, `.content(...)`, `.append(...)`
— will be verified against the `joox` 1.1.0 jar during implementation; if
`$(String)` is not element-creation in that version, fall back to building via the
shared `DocumentBuilder`/`Document.createElement`.)

## Integration

- New method: `Document buildDocumentFromCsv(String csvPath) throws IOException`.
- `execute()`:
  - Remove the `HttpTransport` / `HttpRequestFactory` / `buildGetRequest` /
    `builder.parse(httpRequest.execute()...)` block and the `pbo` URL lookup.
  - Set `document = buildDocumentFromCsv($(config).xpath("/config/matchesCsv").content());`
  - Keep the rest (`GoogleCalenderFetcher`, `syncTeams`, `report`) unchanged.
- Config:
  - Add `<matchesCsv>data/tmp/matches.csv</matchesCsv>` to `config/config.xml` and
    `config/config-template.xml`.
  - Remove the now-unused `<pbo>` element from both.
- Remove imports left unused after dropping the URL fetch (`java.net.URL`,
  `HttpTransport`, `NetHttpTransport`, `HttpRequest`, `HttpRequestFactory`,
  `GenericUrl`).
- `ToernooiNlGrapper` and `pboCompetitionId` remain (still used for location
  scraping).

## CSV parsing

Read all lines (`Files.readAllLines`, UTF-8). Skip line 0 (header). Split each row
with `line.split(";", -1)` to preserve empty trailing fields. Guard against short
rows.

## Testing

- Unit test the team-name bracket stripping and the date transform
  (`d-M-yyyy H:mm:ss` → `d/M/yyyy H:mm:ss`, +3h) as pure helper methods.
- Unit test `buildDocumentFromCsv` against a small fixture CSV: assert a known
  match appears under both team elements with the expected subject and datetimes,
  and that team count / event count match expectations.
- Follow the existing test style in `src/test/java` (JUnit 4 + Hamcrest).

## Out of scope

- Writing the XML to disk (in-memory `Document` only).
- Changing the Google Calendar sync logic.
- CSV columns other than the four mapped above.
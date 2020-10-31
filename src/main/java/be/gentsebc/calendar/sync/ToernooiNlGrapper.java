package be.gentsebc.calendar.sync;

import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ToernooiNlGrapper {

    private static final Logger LOG = Logger.getLogger(ToernooiNlGrapper.class);
    private final String competitionId;
    private Map<String, String> cookies;
    private Map<String, String> lidToDescription = new HashMap<>();
    private Map<String, String> lidToLocationURL = new HashMap<>();
    private Map<String, String> matchId2LocationId = new HashMap<>();


    public ToernooiNlGrapper(final String competitionId) {
        this.competitionId = competitionId;
        setToernooiNlCookieFirewall();

    }


    private void setToernooiNlCookieFirewall() {
        try {
            Connection.Response response = Jsoup.connect("https://www.toernooi.nl//cookiewall")
                    .method(Connection.Method.GET)
                    .execute();
            Map<String, String> cookies = response.cookies();
            cookies.put("st", "c=1");

            this.cookies = cookies;
        } catch (Exception e) {
            LOG.warn("Unable to connect to toernooinl cookie firewall");
        }

    }

    public String getLocationDescription(final String matchId) {

        try {
            matchId2LocationId.computeIfAbsent(matchId, m -> {
                Document matchDoc;
                try {
                    matchDoc = Jsoup.connect(String.format("https://www.toernooi.nl/sport/league/match?id=%s&match=%s", this.competitionId, matchId)).cookies(cookies).get();
                } catch (IOException e) {
                    LOG.warn("Unable to find location for matchId " + matchId + ":" + e.getMessage());
                    return "";
                }

                String locationUrl = matchDoc.select("a[href^=\"../location.aspx?id\"]").attr("href");
                Map<String, String> queryParams = Arrays.stream(locationUrl.substring(locationUrl.lastIndexOf("?") + 1).split("&")).collect(Collectors.toMap(q -> q.split("=")[0], q -> q.split("=")[1]));
                String lid = queryParams.get("lid");
                lidToLocationURL.put(lid, locationUrl);

                return lid;
            });


            String lid = matchId2LocationId.get(matchId);
            lidToDescription.computeIfAbsent(lid, locationId -> {
                Document locationDoc;
                try {
                    locationDoc = Jsoup.connect(String.format("https://www.toernooi.nl/sport/league/%s", lidToLocationURL.get(locationId))).cookies(cookies).get();
                } catch (IOException e) {
                    LOG.warn("Unable to find location for locationId " + locationId + ":" + e.getMessage());
                    return "";
                }

                String part1 = locationDoc.select(":containsOwn(Locatie:)").text().split(":")[1].trim();
                String part2 = locationDoc.select(":containsOwn(Adres:)").next().text().trim();

                return String.format("%s, %s", part1, part2);
            });

            return lidToDescription.get(lid);

        } catch (Exception e) {
            LOG.warn("Unable to find location for matchId " + matchId + ":" + e.getMessage());
            return "";
        }


    }

}
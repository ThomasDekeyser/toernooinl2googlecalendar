package be.gentsebc.calendar.sync;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.CalendarScopes;
import org.apache.log4j.Logger;
import org.joox.JOOX;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;

import static org.joox.JOOX.$;

/**
 * @author Thoomas Dekeyser
 */
public class CalendarSync {

    /**
     * Global instance of the HTTP transport.
     */
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private static com.google.api.services.calendar.Calendar client;


    private static Document config;
    private static Logger logger = Logger.getLogger(CalendarSync.class);

    /**
     * Authorizes the installed application to access user's protected data.
     */
    private static Credential authorize() throws Exception {
        String secretConfig = $(config).xpath("/config/google/clientSecrets").content();
        String credentialStoreConfig = $(config).xpath("/config/google/credentialStore").content();

        // load client secrets
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY, new InputStreamReader(new FileInputStream(secretConfig)));
        if (clientSecrets.getDetails().getClientId().startsWith("Enter")
                || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
            System.out.println(
                    "Enter Client ID and Secret from https://code.google.com/apis/console/?api=calendar "
                            + "into " + secretConfig);
            System.exit(1);
        }
        // set up file credential store
        FileCredentialStore credentialStore = new FileCredentialStore(
                new File(credentialStoreConfig), JSON_FACTORY);
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets,
                Collections.singleton(CalendarScopes.CALENDAR)).setCredentialStore(credentialStore).build();
        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    public static void main(String[] args) {
        logger.info("CalendarSync started");
        try {
            loadConfiguration();
            try {
                // authorizationclient
                Credential credential = authorize();

                // set up global Calendar instance
                client = new com.google.api.services.calendar.Calendar.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
                        "Google-CalendarSample/1.0").build();
                logger.debug("Google client initialised...");
                if (Boolean.getBoolean("fix")) {
                    CalendarFixer cf = new CalendarFixer(config,client);
                    cf.execute();
                } else {
                    CalendarSynchronizer cs = new CalendarSynchronizer(config, client);
                    cs.execute();
                }


            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        logger.info("CalendarSync ended");
        System.exit(0);
    }

    private static void loadConfiguration() throws SAXException, IOException {
        String configFileName = System.getProperty("config");
        if (configFileName == null) {
            System.out.println("Please set systemproperty -Dconfig that points to XML configuration file");
            System.exit(-1);
        }
        DocumentBuilder builder = JOOX.builder();
        config = builder.parse(new File(configFileName));
    }
}

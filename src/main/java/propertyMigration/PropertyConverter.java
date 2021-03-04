package propertyMigration;

import org.jasypt.util.text.BasicTextEncryptor;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static java.util.Map.Entry;
import static java.util.Objects.requireNonNull;

/**
 * @author wxa40
 * @since 01/03/2021
 * <p>
 * Service for the conversion of property files to JSON objects so that they can easily be copied over to HashiCorp Vault as part of the
 * process of migrating an application from on-premise deployment to GCP.
 */
public class PropertyConverter {

    private static final String SERVER_PORT_KEY = "server.port";
    private static final String SERVER_PORT_VALUE = "8080";
    private static final String SERVER_SSL_KEY_STORE_KEY = "server.ssl.key-store";
    private static final String SERVER_SSL_KEY_STORE_PREFIX = "/home/efx_container_user";
    private static final String JASYPT_ENCRYPTOR_PASSWORD = "jasypt.encryptor.password";

    // Service objects
    private static BasicTextEncryptor basicTextEncryptor = new BasicTextEncryptor();
    private static final Scanner scanner = new Scanner(System.in);
    private static String jasyptKey;
    // Maps
    private static final Map<String, String> profilePaths = new HashMap<>();
    private static final Map<String, String> defaultProfile = new HashMap<>();
    private static final Map<String, JSONObject> profiles = new HashMap<>();
    // Paths
    private static String directoryPath;
    private static String defaultProfilePath;

    public static void main(String[] args) {
        setJasyptKey();
        setDirectory();
        setProfilePaths();
        convertProfiles();
        exportToFiles();
    }

    private static void exportToFiles() {
        for (Entry<String, JSONObject> entry : profiles.entrySet()) {
            String outputFilePath = String.format("src/main/resources/%s.json", entry.getKey());

            try {
                FileWriter file = new FileWriter(outputFilePath);
                file.write(entry.getValue().toString());
                file.flush();
                file.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void setJasyptKey() {
        System.out.print("Enter the Jasypt key for this project: ");
        jasyptKey = scanner.nextLine();
        basicTextEncryptor.setPassword(jasyptKey);
    }

    private static void setDirectory() {
        System.out.print("Enter the full path to the directory containing the property files you want to decrypt: ");
        directoryPath = scanner.nextLine();

        scanner.close();

        if (directoryPath.charAt(directoryPath.length() - 1) != '/') {
            directoryPath += "\\";
        }
    }

    private static void setProfilePaths() {
        File directory = new File(directoryPath);

        defaultProfilePath = directoryPath + "application.properties";

        for (String profile : requireNonNull(directory.list())) {
            if (profile.startsWith("application-") && profile.endsWith(".properties")) {
                String profileName = profile.substring(profile.indexOf('-') + 1, profile.lastIndexOf('.'));
                String profilePath = directoryPath + profile;

                profilePaths.put(profileName, profilePath);
            }
        }
    }

    private static void convertProfiles() {
        readFile(defaultProfilePath, defaultProfile);

        for (Entry<String, String> entry : profilePaths.entrySet()) {
            Map<String, String> profile = new HashMap<>();
            readFile(entry.getValue(), profile);

            mergeMaps(profile);
            fixValues(profile);

            profiles.put(entry.getKey(), new JSONObject(profile));
        }

        System.out.println(profiles);
    }

    private static void readFile(String profilePath, Map<String, String> profileMap) {
        BufferedReader reader;

        try {
            reader = new BufferedReader(new FileReader(profilePath));
            String line = reader.readLine();

            while (line != null) {
                line = line.strip(); // Get rid of whitespace trails and leads

                // Check for and ignore empty and commented lines
                if (!line.isBlank() && !line.isEmpty() && !line.startsWith("#")) {
                    insertProperty(checkMultiLine(line, reader), profileMap);
                }

                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        basicTextEncryptor = new BasicTextEncryptor();
        basicTextEncryptor.setPassword(jasyptKey);
    }

    private static String checkMultiLine(String line, BufferedReader reader) throws IOException {
        if (!line.endsWith("\\")) {
            return line; // If there's no backslash on the end, it's a single line property and we can just send it back
        }

        line = line.substring(0, line.length() - 1); // Take the backslash off of the end
        String nextLine = reader.readLine(); // Look ahead

        while (true) {
            nextLine = nextLine.strip(); // Get rid of whitespace trails and leads

            if (nextLine.endsWith("\\")) {
                line = line + nextLine.substring(0, nextLine.length() - 1) + " "; // Take the backslash off the end and append it
            } else {
                line = line + nextLine; // Append and exit the loop
                break;
            }

            nextLine = reader.readLine();
        }

        return line.strip();
    }

    private static void insertProperty(String line, Map<String, String> properties) {
        int split = line.indexOf("=");

        String key = line.substring(0, split);
        String value = line.substring(split + 1);

        if (key.equals(JASYPT_ENCRYPTOR_PASSWORD)) {
            if (value.startsWith("${") && value.endsWith("}")) {
                return;
            }

            basicTextEncryptor = new BasicTextEncryptor();
            basicTextEncryptor.setPassword(value);

            return;
        }

        // Check to see if the property is encrypted and decrypt it if it is
        if (value.startsWith("ENC(") && value.endsWith(")")) {
            value = basicTextEncryptor.decrypt(value.substring(4, value.length() - 1));
        }

        // Add the k/v pair to the Map
        properties.put(key, value);
    }

    private static void mergeMaps(Map<String, String> profile) {
        for (Entry<String, String> entry : defaultProfile.entrySet()) {
            if (!profile.containsKey(entry.getKey())) {
                profile.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void fixValues(Map<String, String> profile) {
        // Set the server port to 8080
        if (profile.containsKey(SERVER_PORT_KEY)) {
            profile.put(SERVER_PORT_KEY, SERVER_PORT_VALUE);
        }

        // Prepend the root directory onto the key store path
        if (profile.containsKey(SERVER_SSL_KEY_STORE_KEY)) {
            String serverSSLKeyStoreValue = profile.get(SERVER_SSL_KEY_STORE_KEY);

            // Check to see whether or not the path needs to be updated
            if (!serverSSLKeyStoreValue.contains(SERVER_SSL_KEY_STORE_PREFIX)) {
                serverSSLKeyStoreValue = SERVER_SSL_KEY_STORE_PREFIX + serverSSLKeyStoreValue;
            }

            // Update the property
            profile.put(SERVER_SSL_KEY_STORE_KEY, serverSSLKeyStoreValue);
        }
    }
}
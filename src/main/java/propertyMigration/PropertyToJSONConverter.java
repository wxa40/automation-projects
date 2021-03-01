package propertyMigration;

import org.jasypt.util.text.BasicTextEncryptor;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static java.util.Map.Entry;

/**
 * @author wxa40
 * @since 01/03/2021
 *
 * Service for the conversion of property files to JSON objects so that they can easily be copied over to HashiCorp Vault as part of the
 * process of migrating an application from on-premise deployment to GCP.
 */
public class PropertyToJSONConverter {

    // Key/Value pairs for property corrections
    private static final String SERVER_PORT_KEY = "server.port";
    private static final String SERVER_PORT_VALUE = "8080";
    private static final String SERVER_SSL_KEY_STORE_KEY = "server.ssl.key-store";
    private static final String SERVER_SSL_KEY_STORE_PREFIX = "/home/efx_container_user";

    // User input prompt messages
    private static final String JASYPT_KEY_PROMPT = "Enter the Jasypt key for this project: ";
    private static final String NON_DEFAULT_PROFILE_PROMPT = "Enter the full path of the non-default (e.g. -dev/-qa) profile you want to convert: ";
    private static final String DEFAULT_PROFILE_PROMPT = "Enter the full path of the default profile you want to convert: ";

    // Error message templates
    private static final String UNREADABLE_LINE_MESSAGE = "Unreadable property on line %d in file %s. Please manually review and add this line to the JSON file. %n";

    // Characters and substrings
    private static final String ENCRYPTION_MARKER = "ENC(";
    private static final char HASH = '#';
    private static final char EQUALS = '=';

    // Encryptor for decrypting confidential properties
    private static final BasicTextEncryptor basicTextEncryptor = new BasicTextEncryptor();

    public static void main(String[] args) {
        basicTextEncryptor.setPassword(getJasyptKey());
        Map<String, String> properties = getProperties();
        fixValues(properties);

        // Convert the Map to a JSON object and print it out to the console
        JSONObject jsonObject = new JSONObject(properties);
        System.out.println(jsonObject.toString());
    }

    /**
     * Read in the Jasypt key used for decrypting any encrypted properties.
     *
     * @return Jasypt key for this project.
     */
    private static String getJasyptKey() {
        Scanner scanner = new Scanner(System.in);

        System.out.print(JASYPT_KEY_PROMPT);

        return scanner.nextLine();
    }

    /**
     * Accepts user input of the absolute path to the base and environment-specific profiles of an application and reads them into a Map.
     *
     * @return Map containing the merged properties from the profiles selected.
     */
    private static Map<String, String> getProperties() {
        Scanner scanner = new Scanner(System.in);

        System.out.print(NON_DEFAULT_PROFILE_PROMPT);
        String nonBaseProfileLocation = scanner.nextLine();
        Map<String, String> nonBaseProperties = readFile(nonBaseProfileLocation);

        System.out.print(DEFAULT_PROFILE_PROMPT);
        String baseProfileLocation = scanner.nextLine();
        Map<String, String> baseProperties = readFile(baseProfileLocation);

        return mergeMaps(nonBaseProperties, baseProperties);
    }

    /**
     * Accepts a path to a property file and reads it line-by-line, adding each new property to a Map.
     *
     * @param filePath Absolute path to the property file to be read.
     * @return Map containing properties from the profile selected.
     */
    private static Map<String, String> readFile(String filePath) {
        Map<String, String> properties = new HashMap<>();
        BufferedReader reader;
        int lineNumber = 0;

        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine();

            while (line != null) {
                lineNumber ++;

                // Check for and ignore empty and commented lines
                if (!line.isBlank() && !line.isEmpty() && line.charAt(0) != HASH) {
                    insertProperty(line, lineNumber, properties, filePath);
                }

                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return properties;
    }

    /**
     * Takes a line from the property file and splits it into key and value so it can be added to the Map.
     *
     * @param line       The line to be processed.
     * @param lineNumber The number of the line to be processed. (Used for debug output)
     * @param properties The Map for the property to be added to.
     * @param filePath   The path to the file that the line came from. (Used for debug output)
     */
    private static void insertProperty(String line, int lineNumber, Map<String, String> properties, String filePath) {
        String key;
        String value;

        try {
            int split = line.indexOf(EQUALS);

            key = line.substring(0, split);
            value = line.substring(split + 1);
        } catch (StringIndexOutOfBoundsException e) {
            // If the line does not contain an equals sign, ask the user to manually review it
            System.out.printf(UNREADABLE_LINE_MESSAGE, lineNumber, filePath);
            return;
        }

        // Check to see if the property is encrypted and decrypt it if it is
        if (value.contains(ENCRYPTION_MARKER)) {
            value = basicTextEncryptor.decrypt(value.substring(4, value.length() - 1));
        }

        // Add the k/v pair to the Map
        properties.put(key, value);
    }

    /**
     * Takes a Map of each default, and environment-specific properties and merges them,
     * overriding any duplicate values in the default profile.
     *
     * @param nonBaseProperties Map containing properties from the selected environment-specific profile.
     * @param baseProperties    Map containing properties from the selected default profile.
     *
     * @return Map containing the merger of the two previous Maps.
     */
    private static Map<String, String> mergeMaps(Map<String, String> nonBaseProperties, Map<String, String> baseProperties) {
        for (Entry<String, String> entry : baseProperties.entrySet()) {
            if (!nonBaseProperties.containsKey(entry.getKey())) {
                nonBaseProperties.put(entry.getKey(), entry.getValue());
            }
        }

        return nonBaseProperties;
    }

    /**
     * Update the server port and key-store values to match all of the other applications in GCP.
     *
     * @param properties Map containing current list of properties.
     */
    private static void fixValues(Map<String, String> properties) {
        // Set the server port to 8080
        if (properties.containsKey(SERVER_PORT_KEY)) {
            properties.put(SERVER_PORT_KEY, SERVER_PORT_VALUE);
        }

        // Prepend the root directory onto the key store path
        if (properties.containsKey(SERVER_SSL_KEY_STORE_KEY)) {
            String serverSSLKeyStoreValue = properties.get(SERVER_SSL_KEY_STORE_KEY);

            // Check to see whether or not the path needs to be updated
            if (!serverSSLKeyStoreValue.contains(SERVER_SSL_KEY_STORE_PREFIX)) {
                serverSSLKeyStoreValue = SERVER_SSL_KEY_STORE_PREFIX + serverSSLKeyStoreValue;
            }

            // Update the property
            properties.put(SERVER_SSL_KEY_STORE_KEY, serverSSLKeyStoreValue);
        }
    }
}

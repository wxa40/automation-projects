package propertyMigration;

import org.jasypt.util.text.BasicTextEncryptor;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static java.util.Map.*;

public class Main {

    private static final BasicTextEncryptor basicTextEncryptor = new BasicTextEncryptor();

    public static void main(String[] args) {
        basicTextEncryptor.setPassword(getJasyptKey());
        Map<String, String> properties = getProperties();
        fixValues(properties);

        JSONObject jsonObject = new JSONObject(properties);
        System.out.println(jsonObject.toString());
    }

    private static void fixValues(Map<String, String> properties) {
        String portK = "server.port";
        String portV = "8080";

        if (properties.containsKey(portK)) {
            properties.put(portK, portV);
        }

        String sslK = "server.ssl.key-store";
        String rootPath = "/home/efx_container_user";

        if (properties.containsKey(sslK)) {
            String sslV = properties.get(sslK);

            if (!sslV.contains(rootPath)) {
                sslV = rootPath + sslV;
            }

            properties.put(sslK, sslV);
        }
    }

    private static String getJasyptKey() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter the Jasypt key for this project: ");

        return scanner.nextLine();
    }

    private static Map<String, String> getProperties() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter the full path of the non-default (e.g. -dev/-qa) profile you want to convert: ");
        String nonBaseProfileLocation = scanner.nextLine();

        System.out.print("Enter the full path of the default profile you want to convert: ");
        String baseProfileLocation = scanner.nextLine();

        scanner.close();

        Map<String, String> nonBaseProperties = readFile(nonBaseProfileLocation);
        Map<String, String> baseProperties = readFile(baseProfileLocation);

        return mergeMaps(nonBaseProperties, baseProperties);
    }

    private static Map<String, String> readFile(String filePath) {
        Map<String, String> properties = new HashMap<>();
        BufferedReader reader;

        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line = reader.readLine();

            while (line != null) {
                // Check for empty and commented lines
                if (!line.isBlank() && !line.isEmpty() && line.charAt(0) != '#') {
                    insertProperty(line, properties);
                }

                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return properties;
    }

    private static void insertProperty(String line, Map<String, String> properties) {
        int split = line.indexOf('=');
        String k;
        String v;

        try {
            k = line.substring(0, split);
            v = line.substring(split + 1);
        } catch (StringIndexOutOfBoundsException e) {
            System.out.println(line);
            return;
        }


        if (v.contains("ENC(")) {
            v = basicTextEncryptor.decrypt(v.substring(4, v.length() - 1));
        }

        properties.put(k, v);
    }

    private static Map<String, String> mergeMaps(Map<String, String> nonBaseProperties, Map<String, String> baseProperties) {
        for (Entry<String, String> entry : baseProperties.entrySet()) {
            if (!nonBaseProperties.containsKey(entry.getKey())) {
                nonBaseProperties.put(entry.getKey(), entry.getValue());
            }
        }

        return nonBaseProperties;
    }
}

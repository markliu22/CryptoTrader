package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.management.RuntimeErrorException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Base64;
import java.util.Formatter;
import java.util.List;

public class App {
    private static String BASE_URL;
    private static String API_KEY;
    private static String SECRET_KEY;
    private static String PASSPHRASE;

    // old decode was wrong, correct version: https://stackoverflow.com/questions/49679288/gdax-api-returning-invalid-signature-on-post-requests
    public static String generateSignedHeader(String requestPath, String method, String body, String timestamp) {
        try {
            String prehash = timestamp + method.toUpperCase() + requestPath + body;
            byte[] secretDecoded = Base64.getDecoder().decode(SECRET_KEY);
            SecretKeySpec keyspec = new SecretKeySpec(secretDecoded, Mac.getInstance("HmacSHA256").getAlgorithm());
            Mac sha256 = (Mac) Mac.getInstance("HmacSHA256").clone();
            sha256.init(keyspec);
            String response = Base64.getEncoder().encodeToString(sha256.doFinal(prehash.getBytes()));
            return response;
        } catch (CloneNotSupportedException | InvalidKeyException e) {
            System.out.println(e);
            throw new RuntimeErrorException(new Error("Cannot set up authentication headers."));
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e);
            throw new RuntimeErrorException(new Error("Cannot set up authentication headers."));
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // auto import: https://stackoverflow.com/questions/63243193/has-intellij-idea2020-1-removed-maven-auto-import-dependencies
        // get env vars from: https://github.com/cdimascio/dotenv-java
        Dotenv dotenv = Dotenv.load();
        BASE_URL = dotenv.get("BASE_URL");
        API_KEY = dotenv.get("API_KEY");
        SECRET_KEY = dotenv.get("SECRET_KEY");
        PASSPHRASE = dotenv.get("PASSPHRASE");

        // Get CB_ACCESS_SIGN
        String CB_ACCESS_TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/accounts";
        String METHOD = "GET";
        String CB_ACCESS_SIGN = generateSignedHeader(REQUEST_PATH, METHOD, "", CB_ACCESS_TIMESTAMP);

        // Now can create request
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .setHeader("CB-ACCESS-SIGN", CB_ACCESS_SIGN)
                .setHeader("CB-ACCESS-TIMESTAMP", CB_ACCESS_TIMESTAMP)
                .setHeader("CB-ACCESS-KEY", API_KEY)
                .setHeader("CB-ACCESS-PASSPHRASE", PASSPHRASE)
                .setHeader("content-type", "application/json")
                .uri(URI.create(BASE_URL + "accounts"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        // will print a bunch of accounts because there will be 1 account per currency (BTC account, ETH account..)
    }
}


/*
Test Http GET
Get an instance of the http client
https://www.youtube.com/watch?v=5MmlRZZxTqk&ab_channel=DanVega
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .header("accept", "application/json")
        .setHeader("CB-ACCESS-KEY", "application/json")
        .uri(URI.create(URL))
        .build();
return type is HttpResponse<String>
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
 System.out.println(response.body());
 parse JSON into objects using jackson
ObjectMapper mapper = new ObjectMapper();
List<Post> posts = mapper.readValue(response.body(), new TypeReference<List<Post>>() {});
posts.forEach(System.out::println);
* */
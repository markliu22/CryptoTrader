package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONArray;
import org.json.JSONObject;

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
import java.util.*;

public class App {
    // keep variables private, interact with them through public getters and setters
    // constants:
    private static final String CB_ACCESS_SIGN = "CB-ACCESS-SIGN";
    private static final String CB_ACCESS_TIMESTAMP = "CB-ACCESS-TIMESTAMP";
    private static final String CB_ACCESS_KEY = "CB-ACCESS-KEY";
    private static final String CB_ACCESS_PASSPHRASE = "CB-ACCESS-PASSPHRASE";

    private static String BASE_URL;
    private static String API_KEY;
    private static String SECRET_KEY;
    private static String PASSPHRASE;

    // response.body() after getting prices is a json array of json arrays
    public static Double parsePrices(String responseBody) {
        JSONArray arr = new JSONArray(responseBody);
        JSONArray mostRecent = arr.getJSONArray(0);
        double open = mostRecent.getDouble(1);
        double close = mostRecent.getDouble(2);
        return (open + close) / 2;
    }

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

    // TODO: place order method
    public static void buyCoin(HttpClient client, String coinName) {
        // must have enough usd in their account
        // https://docs.pro.coinbase.com/#place-a-new-order
    }

    // TODO: sell method
    public static void sellCoin(HttpClient client, String coinName) {
        // https://docs.pro.coinbase.com/#create-conversion
        // convert to usd
    }

    // TODO: check out coin price method
    // https://docs.pro.coinbase.com/#get-historic-rates
    public static Double getCoinPrice(HttpClient client, String coinTicker) throws IOException, InterruptedException {
        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        // must be one of {60, 300, 900, 3600, 21600, 86400}
        int granularity = 60; // get most recent
        // "if data points are readily available, your response may contain as many as 300 candles and some of those candles may precede your declared start value"
        String REQUEST_PATH = "/products/" + coinTicker + "/candles?granularity=" + granularity;
        String METHOD = "GET";
        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, "", TIMESTAMP);
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .setHeader(CB_ACCESS_SIGN, SIGN)
                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
                .setHeader(CB_ACCESS_KEY, API_KEY)
                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
                .setHeader("content-type", "application/json")
                .uri(URI.create(BASE_URL + REQUEST_PATH))
                .build();
        // response.body() contains a list of accounts, 1 for each coin
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // https://www.youtube.com/watch?v=qzRKa8I36Ww&ab_channel=CodingMaster-ProgrammingTutorials
        return parsePrices(response.body());
        // TODO: find a way to analyze these recent prices?
    }

    // response contains a bunch of accounts, 1 for each coin
    public static HttpResponse<String> getAllAccounts(HttpClient client) throws IOException, InterruptedException {
        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/accounts";
        String METHOD = "GET";
        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, "", TIMESTAMP);
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .setHeader(CB_ACCESS_SIGN, SIGN)
                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
                .setHeader(CB_ACCESS_KEY, API_KEY)
                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
                .setHeader("content-type", "application/json")
                .uri(URI.create(BASE_URL + REQUEST_PATH))
                .build();
        // response.body() contains a list of accounts, 1 for each coin
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    // start bot method
    public static void startProcess() throws IOException, InterruptedException {
        // auto import: https://stackoverflow.com/questions/63243193/has-intellij-idea2020-1-removed-maven-auto-import-dependencies
        // get env vars from: https://github.com/cdimascio/dotenv-java
        Dotenv dotenv = Dotenv.load();
        BASE_URL = dotenv.get("BASE_URL");
        API_KEY = dotenv.get("API_KEY");
        SECRET_KEY = dotenv.get("SECRET_KEY");
        PASSPHRASE = dotenv.get("PASSPHRASE");
        HttpClient client = HttpClient.newHttpClient();

        // enter num coins you're watching
        Scanner sc = new Scanner(System.in);
        System.out.println("Number of coins to watch:");
        int numCoins = sc.nextInt();
        sc.nextLine();
        Coin[] coins = new Coin[numCoins];

        for(int i = 0; i < numCoins; i++) {
            // Ex: BTC-USD
            System.out.println("Coin ticker " + (i + 1) + ") ($):");
            String name = sc.nextLine();

            System.out.println("Target price to sell coin " + (i + 1) + ") ($):");
            double sellPrice = sc.nextDouble();

            System.out.println("Target price to buy coin " + (i + 1) + ") ($):");
            double buyPrice = sc.nextDouble();

            System.out.println("Maximum amount of coin " + (i + 1) + ") to sell at a time  ($):");
            double maxBuyAmount = sc.nextDouble();

            System.out.println("Maximum amount of coin " + (i + 1) + ") to buy at a time ($):");
            double maxSellAmount = sc.nextDouble();

            Coin currCoin = new Coin(name, sellPrice, buyPrice, maxBuyAmount, maxSellAmount);
            coins[i] = currCoin;
            System.out.println(currCoin.getName() + " added.");
            sc.nextLine();
        }
        // TODO: ask about risk tolerance
        getCoinPrice(client, "BTC-USD");
        // for each Coin
            // check price of coin:
                // buy / sell if ...
            // sleep for like 10-ish mins
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        startProcess();
    }
}

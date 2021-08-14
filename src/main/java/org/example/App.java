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

    static boolean isRunning = true;
    static HttpClient client; // TODO: move this somewhere else idk

    public static double calculateAverage(String responseBody) {
        JSONArray arr = new JSONArray(responseBody);
        double sum = 0;
        int n = arr.length();

        for(int i = 0; i < n; i++) {
            JSONArray curr = arr.getJSONArray(i);
            double low = curr.getDouble(1);
            double high = curr.getDouble(2);
            sum += low + (high - low) / 2;
        }
        return sum / n;
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

    // TODO: add limit order?
    // https://docs.pro.coinbase.com/#place-a-new-order
    // name = "BTC-USD"
    public static HttpResponse<String> buyCoin(String name, double amount) throws IOException, InterruptedException {
        // TODO: add check that they have enough in their account
        JSONObject requestBody = new JSONObject();
        requestBody.put("size", String.valueOf(amount)); // place market order for amount BTC
        requestBody.put("type", "market");
        requestBody.put("side", "buy");
        requestBody.put("product_id", name);
        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/orders";
        String METHOD = "POST";
        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .setHeader(CB_ACCESS_SIGN, SIGN)
                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
                .setHeader(CB_ACCESS_KEY, API_KEY)
                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
                .setHeader("content-type", "application/json")
                .uri(URI.create(BASE_URL + REQUEST_PATH))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    // TODO: test this
    // TODO: merge this with withdrawToCoinbase function?
    // pass in coinbase pro wallet address of the corresponding cryptocurrency
    // name = "BTC"
    public static HttpResponse<String> withdrawToWallet(String name, double amount, String walletId) throws IOException, InterruptedException {
        JSONObject requestBody = new JSONObject();
        requestBody.put("amount", String.valueOf(amount));
        requestBody.put("currency", name);
        requestBody.put("crypto_address", walletId);
        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/withdrawals/crypto";
        String METHOD = "POST";
        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .setHeader(CB_ACCESS_SIGN, SIGN)
                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
                .setHeader(CB_ACCESS_KEY, API_KEY)
                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
                .setHeader("content-type", "application/json")
                .uri(URI.create(BASE_URL + REQUEST_PATH))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    // https://docs.pro.coinbase.com/#coinbase56
    // withdraws to a regular Coinbase account (not pro)
    // name = "BTC"
    public static HttpResponse<String> withdrawToCoinbase(String name, double amount, String coinbaseAccId) throws IOException, InterruptedException {
        JSONObject requestBody = new JSONObject();
        requestBody.put("amount", String.valueOf(amount));
        requestBody.put("currency", name);
        requestBody.put("coinbase_account_id", coinbaseAccId);
        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/withdrawals/coinbase-account";
        String METHOD = "POST";
        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .setHeader(CB_ACCESS_SIGN, SIGN)
                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
                .setHeader(CB_ACCESS_KEY, API_KEY)
                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
                .setHeader("content-type", "application/json")
                .uri(URI.create(BASE_URL + REQUEST_PATH))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    // TODO: delete this method?
    public static HttpResponse<String> convert() throws IOException, InterruptedException {
        // https://docs.pro.coinbase.com/#create-conversion
        JSONObject requestBody = new JSONObject();
        requestBody.put("from", "USD");
        requestBody.put("to", "USDC");
        requestBody.put("amount", "100");
        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/conversions";
        String METHOD = "POST";
        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
        HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .setHeader(CB_ACCESS_SIGN, SIGN)
                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
                .setHeader(CB_ACCESS_KEY, API_KEY)
                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
                .setHeader("content-type", "application/json")
                .uri(URI.create(BASE_URL + REQUEST_PATH))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    // TODO: either delete this function or change it to get Moving Average (which takes in how far back we should go)
    // https://docs.pro.coinbase.com/#get-historic-rates
    public static double getMovingAverage(String coinTicker, long maSeconds) throws IOException, InterruptedException {
        int granularity = 86400; // since we only get MA from past day and past week, keep this
        // smaller intervals need smaller granularity or else gives error

        String MAEnd = Instant.now().toString();
        String MAStart = Instant.now().minusSeconds(maSeconds).toString();

        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/products/" + coinTicker + "/candles?start=" + MAStart + "&end=" + MAEnd + "&granularity=" + granularity;
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
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("RESPONSE:");
        System.out.println(response);
        // https://www.youtube.com/watch?v=qzRKa8I36Ww&ab_channel=CodingMaster-ProgrammingTutorials
        return calculateAverage(response.body());
    }

    public static String getAction(String coinTicker, long longMaSeconds, long shortMaSeconds) throws IOException, InterruptedException {
        // make 2 calls to getMovingAverage
        double shortMa = getMovingAverage(coinTicker, shortMaSeconds);
        double longMA = getMovingAverage(coinTicker, longMaSeconds);
        // compare

        // return "BUY", "SELL", or "NO_ACTION"
        return "NO_ACTION";
    }

    public static HttpResponse<String> getCoinbaseAccounts() throws IOException, InterruptedException {
        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/coinbase-accounts";
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
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    // response contains a bunch of accounts, 1 for each coin
    public static HttpResponse<String> getAllAccounts() throws IOException, InterruptedException {
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
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    // TODO: finish this
    public static String analyze() {
        String action = "NO_ACTION";
        // returns "BUY", "SELL", or "NO_ACTION"

        // get MA of past day?
        // get MA of past hour?
        // if short term MA crosses above long term MA by X amount, buy because trend is shifting up
        // if short term MA crosses below long term MA by Y amount, sell because trend is shifting down
        // https://www.investopedia.com/articles/active-trading/052014/how-use-moving-average-buy-stocks.asp
        return action;
    }

    // TODO: extract all POST requests into 1 function
    // TODO: extract all GET requests into 1 function (or try and combine these?)
    public static void startProcess() throws IOException, InterruptedException {
        // Helpful: https://stackoverflow.com/questions/61281364/coinbase-pro-sandbox-how-to-deposit-test-money
        // Helpful: https://stackoverflow.com/questions/59364615/coinbase-pro-and-sandbox-login-endpoints
        // Helpful: https://stackoverflow.com/questions/63243193/has-intellij-idea2020-1-removed-maven-auto-import-dependencies
        // Helpful: https://github.com/cdimascio/dotenv-java

        Dotenv dotenv = Dotenv.load();
        // BASE_URL = dotenv.get("BASE_URL");
        // API_KEY = dotenv.get("API_KEY");
        // SECRET_KEY = dotenv.get("SECRET_KEY");
        // PASSPHRASE = dotenv.get("PASSPHRASE");
        // TODO: uncomment top, comment bottom once done with sandbox api
        BASE_URL = dotenv.get("SANDBOX_URL");
        API_KEY = dotenv.get("SANDBOX_API_KEY");
        SECRET_KEY = dotenv.get("SANDBOX_SECRET_KEY");
        PASSPHRASE = dotenv.get("SANDBOX_PASSPHRASE");
        client = HttpClient.newHttpClient(); // !!

        Scanner sc = new Scanner(System.in);
        System.out.println("Number of coins to watch:");
        int numCoins = sc.nextInt();
        sc.nextLine();
        Coin[] coins = new Coin[numCoins];
        // TODO: also ask about risk tolerance here
        for (int i = 0; i < numCoins; i++) {
            // BTC
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

        long secondsInAWeek = 604800; // long MA
        long secondsInADay = 86400; // short MA
        double longMa = getMovingAverage("BTC-USD", secondsInAWeek);
        double shortMa = getMovingAverage("BTC-USD", secondsInADay);
        System.out.println(longMa);
        System.out.println(shortMa);

        // TODO: finish this
        //  while(isRunning) {
        // for each Coin
        // analyze
        // then shleep
        // }

        // TODO: for any function, if we get back any status code >= 300, set isRunning = false
        // usually is bc insufficient funds
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        startProcess();
    }
}

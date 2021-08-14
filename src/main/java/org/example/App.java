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

    private static final String BUY_ACTION = "BUY";
    private static final String SELL_ACTION = "SELL";
    private static final String NO_ACTION = "NO_ACTION";

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
            System.out.println("CloneNotSupportedException | InvalidKeyException: " + e.getMessage());
            throw new RuntimeErrorException(new Error("Cannot set up authentication headers."));
        } catch (NoSuchAlgorithmException e) {
            System.err.println("NoSuchAlgorithmException: " + e.getMessage());
            throw new RuntimeErrorException(new Error("Cannot set up authentication headers."));
        }
    }

    // TODO: add limit order?
    // https://docs.pro.coinbase.com/#place-a-new-order
    // name = "BTC-USD"
    // action has to be buy or sell
    public static HttpResponse<String> placeOrder(String name, double amount, String action) throws IOException, InterruptedException {
        // TODO: add check that they have enough in their account
        JSONObject requestBody = new JSONObject();
        requestBody.put("size", String.valueOf(amount)); // place market order for amount BTC
        requestBody.put("type", "market");
        requestBody.put("side", action);
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

    // TODO: delete?
    // pass in coinbase pro wallet address of the corresponding cryptocurrency
    // name = "BTC"
//    public static HttpResponse<String> withdrawToWallet(String name, double amount, String walletId) throws IOException, InterruptedException {
//        JSONObject requestBody = new JSONObject();
//        requestBody.put("amount", String.valueOf(amount));
//        requestBody.put("currency", name);
//        requestBody.put("crypto_address", walletId);
//        String TIMESTAMP = Instant.now().getEpochSecond() + "";
//        String REQUEST_PATH = "/withdrawals/crypto";
//        String METHOD = "POST";
//        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
//        HttpRequest request = HttpRequest.newBuilder()
//                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
//                .setHeader(CB_ACCESS_SIGN, SIGN)
//                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
//                .setHeader(CB_ACCESS_KEY, API_KEY)
//                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
//                .setHeader("content-type", "application/json")
//                .uri(URI.create(BASE_URL + REQUEST_PATH))
//                .build();
//        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//        return response;
//    }

    // https://docs.pro.coinbase.com/#coinbase56
    // withdraws to a regular Coinbase account (not pro)
    // name = "BTC"
    // TODO: delete?
//    public static HttpResponse<String> withdrawToCoinbase(String name, double amount, String coinbaseAccId) throws IOException, InterruptedException {
//        JSONObject requestBody = new JSONObject();
//        requestBody.put("amount", String.valueOf(amount));
//        requestBody.put("currency", name);
//        requestBody.put("coinbase_account_id", coinbaseAccId);
//        String TIMESTAMP = Instant.now().getEpochSecond() + "";
//        String REQUEST_PATH = "/withdrawals/coinbase-account";
//        String METHOD = "POST";
//        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
//        HttpRequest request = HttpRequest.newBuilder()
//                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
//                .setHeader(CB_ACCESS_SIGN, SIGN)
//                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
//                .setHeader(CB_ACCESS_KEY, API_KEY)
//                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
//                .setHeader("content-type", "application/json")
//                .uri(URI.create(BASE_URL + REQUEST_PATH))
//                .build();
//        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//        return response;
//    }

    // TODO: delete?
//    public static HttpResponse<String> convert() throws IOException, InterruptedException {
//        // https://docs.pro.coinbase.com/#create-conversion
//        JSONObject requestBody = new JSONObject();
//        requestBody.put("from", "USD");
//        requestBody.put("to", "USDC");
//        requestBody.put("amount", "100");
//        String TIMESTAMP = Instant.now().getEpochSecond() + "";
//        String REQUEST_PATH = "/conversions";
//        String METHOD = "POST";
//        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
//        HttpRequest request = HttpRequest.newBuilder()
//                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
//                .setHeader(CB_ACCESS_SIGN, SIGN)
//                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
//                .setHeader(CB_ACCESS_KEY, API_KEY)
//                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
//                .setHeader("content-type", "application/json")
//                .uri(URI.create(BASE_URL + REQUEST_PATH))
//                .build();
//        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//        return response;
//    }

    // TODO: either delete this function or change it to get Moving Average (which takes in how far back we should go)
    // https://docs.pro.coinbase.com/#get-historic-rates
    // BTC-USD
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
        return calculateAverage(response.body());
    }

    // TODO: delete?
//    public static HttpResponse<String> getCoinbaseAccounts() throws IOException, InterruptedException {
//        String TIMESTAMP = Instant.now().getEpochSecond() + "";
//        String REQUEST_PATH = "/coinbase-accounts";
//        String METHOD = "GET";
//        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, "", TIMESTAMP);
//        HttpRequest request = HttpRequest.newBuilder()
//                .GET()
//                .setHeader(CB_ACCESS_SIGN, SIGN)
//                .setHeader(CB_ACCESS_TIMESTAMP, TIMESTAMP)
//                .setHeader(CB_ACCESS_KEY, API_KEY)
//                .setHeader(CB_ACCESS_PASSPHRASE, PASSPHRASE)
//                .setHeader("content-type", "application/json")
//                .uri(URI.create(BASE_URL + REQUEST_PATH))
//                .build();
//        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//        return response;
//    }

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

    // TODO: make this better
    /*
    * Other exit methods could be when the price crosses below a moving average (not shown), or when an indicator such as the stochastic oscillator crosses its signal line.
    * https://www.investopedia.com/terms/s/stochasticoscillator.asp
    * */
    public static String getAction(String coinTicker, long shortMaSeconds, long longMaSeconds) throws IOException, InterruptedException {
        double shortMa = getMovingAverage(coinTicker, shortMaSeconds);
        double longMa = getMovingAverage(coinTicker, longMaSeconds);
        System.out.println("[" + coinTicker +"][7 DAY M.A.]:" + longMa);
        System.out.println("[" + coinTicker +"]][1 DAY M.A.]: " + shortMa);
        if(shortMa > longMa) {
            return BUY_ACTION;
        } else if(shortMa < longMa) {
            return SELL_ACTION;
        }
        return NO_ACTION;
    }

    // TODO: extract all POST requests into 1 function
    // TODO: extract all GET requests into 1 function (or try and combine these?)
    public static void startProcess() throws IOException, InterruptedException {
        // Helpful: https://stackoverflow.com/questions/61281364/coinbase-pro-sandbox-how-to-deposit-test-money
        // https://stackoverflow.com/questions/59364615/coinbase-pro-and-sandbox-login-endpoints
        // https://stackoverflow.com/questions/63243193/has-intellij-idea2020-1-removed-maven-auto-import-dependencies
        // https://github.com/cdimascio/dotenv-java
        // https://www.youtube.com/watch?v=qzRKa8I36Ww&ab_channel=CodingMaster-ProgrammingTutorials

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

        client = HttpClient.newHttpClient();
        Scanner sc = new Scanner(System.in);
        System.out.println("Number of coins to watch:");
        int numCoins = sc.nextInt();
        sc.nextLine();
        Coin[] coins = new Coin[numCoins];

        for (int i = 0; i < numCoins; i++) {
            // BTC
            System.out.println("Coin ticker " + (i + 1) + ") ($):");
            String name = sc.nextLine();
            // TODO: add some factor so we don't buy if shortMa > longMa by just a little bit
            System.out.println("TODO: some question about risk tolerance here");
            double factor = sc.nextDouble();
            Coin currCoin = new Coin(name, name, factor);
            coins[i] = currCoin;
            System.out.println(currCoin.getName() + " added.");
            sc.nextLine();
        }

        long secondsInADay = 86400; // short MA
        long secondsInAWeek = 604800; // long MA
        // withdraw coin was the only one that used BTC instead of BTC-USD but can delete that now

        // this converts to USD, not USDC
        // HttpResponse<String> res = placeOrder("BTC-USD", 0.05, "sell");

        while(isRunning) {
            for(Coin curr : coins) {
                String action = getAction(curr.getProdId(), secondsInADay, secondsInAWeek);
                try {
                    if(action.equals(BUY_ACTION)) {
                        placeOrder(curr.getProdId(), 0.01, "buy");
                    } else if(action.equals(SELL_ACTION)) {
                        placeOrder(curr.getProdId(), 0.01, "sell");
                    }
                } catch (IndexOutOfBoundsException e) {
                    System.err.println("IndexOutOfBoundsException: " + e.getMessage());
                } catch (IOException e) {
                    System.err.println("IOException: " + e.getMessage());
                }
                System.out.println("[" + action + "] " + curr.getProdId());
            }

            try {
                System.out.println("[...SLEEP...]");
                Thread.sleep(60000); // milliseconds
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        startProcess();
    }
}

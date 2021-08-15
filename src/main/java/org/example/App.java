package org.example;

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
import java.time.Instant;
import java.util.*;

public class App {
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
    static HttpClient client;

    // TODO: make all these internal function private
    private static double calculateAverage(String responseBody) {
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

    private static HttpResponse<String> makePOSTRequest(JSONObject requestBody, String SIGN, String TIMESTAMP, String REQUEST_PATH) throws IOException, InterruptedException {
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

    private static HttpResponse<String> makeGETRequest(String SIGN, String TIMESTAMP, String REQUEST_PATH) throws IOException, InterruptedException {
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

    // ref: https://stackoverflow.com/questions/49679288/gdax-api-returning-invalid-signature-on-post-requests
    private static String generateSignedHeader(String requestPath, String method, String body, String timestamp) {
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

    // https://docs.pro.coinbase.com/#place-a-new-order
    // requires "BTC-USD" format
    // action has to be buy or sell
    private static HttpResponse<String> placeOrder(String name, double amount, String action) throws IOException, InterruptedException {
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
        return makePOSTRequest(requestBody, SIGN, TIMESTAMP, REQUEST_PATH);
    }

    // pass in coinbase pro wallet address of the corresponding cryptocurrency
    // requires "BTC" format
//    private static HttpResponse<String> withdrawToWallet(String name, double amount, String walletId) throws IOException, InterruptedException {
//        JSONObject requestBody = new JSONObject();
//        requestBody.put("amount", String.valueOf(amount));
//        requestBody.put("currency", name);
//        requestBody.put("crypto_address", walletId);
//        String TIMESTAMP = Instant.now().getEpochSecond() + "";
//        String REQUEST_PATH = "/withdrawals/crypto";
//        String METHOD = "POST";
//        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
//        return makePOSTRequest(requestBody, SIGN, TIMESTAMP, REQUEST_PATH);
//    }

    // https://docs.pro.coinbase.com/#coinbase56
    // withdraws to a regular Coinbase account (not pro)
    // requires "BTC" format
//    private static HttpResponse<String> withdrawToCoinbase(String name, double amount, String coinbaseAccId) throws IOException, InterruptedException {
//        JSONObject requestBody = new JSONObject();
//        requestBody.put("amount", String.valueOf(amount));
//        requestBody.put("currency", name);
//        requestBody.put("coinbase_account_id", coinbaseAccId);
//        String TIMESTAMP = Instant.now().getEpochSecond() + "";
//        String REQUEST_PATH = "/withdrawals/coinbase-account";
//        String METHOD = "POST";
//        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
//        return makePOSTRequest(requestBody, SIGN, TIMESTAMP, REQUEST_PATH);
//    }

//    private static HttpResponse<String> convert() throws IOException, InterruptedException {
//        // https://docs.pro.coinbase.com/#create-conversion
//        JSONObject requestBody = new JSONObject();
//        requestBody.put("from", "USD");
//        requestBody.put("to", "USDC");
//        requestBody.put("amount", "100");
//        String TIMESTAMP = Instant.now().getEpochSecond() + "";
//        String REQUEST_PATH = "/conversions";
//        String METHOD = "POST";
//        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, requestBody.toString(), TIMESTAMP);
//        return makePOSTRequest(requestBody, SIGN, TIMESTAMP, REQUEST_PATH);
//    }

    // requires "BTC-USD" format
    private static double getMovingAverage(String coinTicker, long maSeconds) throws IOException, InterruptedException {
        int granularity = 86400; // since we only get MA from past day and past week, keep this
        // smaller intervals need smaller granularity or else gives error

        String MAEnd = Instant.now().toString();
        String MAStart = Instant.now().minusSeconds(maSeconds).toString();

        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/products/" + coinTicker + "/candles?start=" + MAStart + "&end=" + MAEnd + "&granularity=" + granularity;
        String METHOD = "GET";
        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, "", TIMESTAMP);
        HttpResponse<String> response = makeGETRequest(SIGN, TIMESTAMP, REQUEST_PATH);
        return calculateAverage(response.body());
    }

//    public private HttpResponse<String> getCoinbaseAccounts() throws IOException, InterruptedException {
//        String TIMESTAMP = Instant.now().getEpochSecond() + "";
//        String REQUEST_PATH = "/coinbase-accounts";
//        String METHOD = "GET";
//        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, "", TIMESTAMP);
//        return makeGETRequest(SIGN, TIMESTAMP, REQUEST_PATH);
//    }

    // requires "BTC" format
    private static double getBalance(String name) throws IOException, InterruptedException {
        HttpResponse<String> responseBody = getAllAccounts();
        JSONArray arr = new JSONArray(responseBody);
        for(int i = 0; i < arr.length(); i++) {
            JSONObject curr = arr.getJSONObject(i);
            if(curr.get("currency").equals(name)) {
                return (double)curr.get("available");
            }
        }
        return -1.0;
    }

    // returns a JSON array of accounts, 1 for each coin
    private static HttpResponse<String> getAllAccounts() throws IOException, InterruptedException {
        String TIMESTAMP = Instant.now().getEpochSecond() + "";
        String REQUEST_PATH = "/accounts";
        String METHOD = "GET";
        String SIGN = generateSignedHeader(REQUEST_PATH, METHOD, "", TIMESTAMP);
        return makeGETRequest(SIGN, TIMESTAMP, REQUEST_PATH);
    }

    // TODO: make this better
    /*
    * Other exit methods could be when the price crosses below a moving average (not shown), or when an indicator such as the stochastic oscillator crosses its signal line.
    * https://www.investopedia.com/terms/s/stochasticoscillator.asp
    * */
    private static String getAction(Coin coin, long shortMaSeconds, long longMaSeconds) throws IOException, InterruptedException {
        double shortMa = getMovingAverage(coin.getProdId(), shortMaSeconds);
        double longMa = getMovingAverage(coin.getProdId(), longMaSeconds);
        System.out.printf("[%s][7 DAY M.A.]: %.2f\n", coin.getProdId(), longMa);
        System.out.printf("[%s][1 DAY M.A.]: %.2f\n", coin.getProdId(), shortMa);
        if(shortMa > longMa + coin.getDiffAmtRequired()) return BUY_ACTION;
        else if(shortMa + coin.getDiffAmtRequired() < longMa) return SELL_ACTION;
        return NO_ACTION;
    }

    private static void startProcess() throws IOException, InterruptedException {
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

            System.out.println("Enter your risk tolerance as 1, 2, or 3 (1 is low risk, 3 is high risk):");
            int riskFactor = sc.nextInt();

            // so we don't buy if shortMa > longMa by just a little bit
            System.out.println("Amount difference needed between long & short term moving average to yield action:");
            double diffAmtRequired = sc.nextDouble();

            System.out.println("Amount to buy in first order:");
            double firstOrderAmt = sc.nextDouble();

            Coin currCoin = new Coin(name, riskFactor, diffAmtRequired, firstOrderAmt);
            coins[i] = currCoin;
            System.out.printf("[%s][ADDED]\n", currCoin.getName());
            sc.nextLine();
        }

        long secondsInADay = 86400;
        long secondsInAWeek = 604800;
        // withdraw coin was the only one that used BTC instead of BTC-USD but can delete that now?

        // this converts to USD, not USDC
        // HttpResponse<String> res = placeOrder("BTC-USD", 0.1, "sell");

        while(isRunning) {
            for(Coin curr : coins) {
                String action = getAction(curr, secondsInADay, secondsInAWeek);
                try {
                    if(action.equals(BUY_ACTION)) {
                        placeOrder(curr.getProdId(), curr.getMadeInitialOrder() ? curr.getBuyFactor() * getBalance(curr.getName()) : curr.getFirstOrderAmt(), "buy");
                        // if just made initial order, change that boolean to true now
                        if(!curr.getMadeInitialOrder()) {
                            curr.triggerMadeInitialOrder();
                        }
                    } else if(action.equals(SELL_ACTION)) {
                        placeOrder(curr.getProdId(), curr.getSellFactor() * getBalance(curr.getName()), "sell");
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
                Thread.sleep(60000);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Helpful:
    // https://stackoverflow.com/questions/61281364/coinbase-pro-sandbox-how-to-deposit-test-money
    // https://stackoverflow.com/questions/59364615/coinbase-pro-and-sandbox-login-endpoints
    // https://stackoverflow.com/questions/63243193/has-intellij-idea2020-1-removed-maven-auto-import-dependencies
    // https://github.com/cdimascio/dotenv-java
    // https://www.youtube.com/watch?v=qzRKa8I36Ww&ab_channel=CodingMaster-ProgrammingTutorials
    public static void main(String[] args) throws IOException, InterruptedException {
        startProcess();
    }
}

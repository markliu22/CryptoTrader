package org.example;

public class Coin {
    // TODO: change these based on percent. Ex: % from OG price you can stomach before you have to sell
    private final String name;
    private final double sellPrice;
    private final double buyPrice;
    private final double maxBuyAmount;
    private final double maxSellAmount;

    public Coin(String name, double sellPrice, double buyPrice, double maxBuyAmount, double maxSellAmount) {
        this.name = name;
        this.sellPrice = sellPrice;
        this.buyPrice = buyPrice;
        this.maxBuyAmount = maxBuyAmount;
        this.maxSellAmount = maxSellAmount;
    }

    public String getName() {
        return name;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getMaxBuyAmount() {
        return maxBuyAmount;
    }

    public double getMaxSellAmount() {
        return maxSellAmount;
    }
}

package org.example;

public class Coin {
    private final String name;
    private final String ticker;
    private final double factor;
    private final String prodId;

    public Coin(String name, String ticker, double factor) {
        this.name = name;
        this.ticker = ticker;
        this.factor = factor;
        prodId = this.name + "-USD";
    }

    public String getName() {
        return name;
    }

    public String getProdId() {
        return prodId;
    }

    public String getTicker() {
        return ticker;
    }

    public double getFactor() {
        return factor;
    }
}

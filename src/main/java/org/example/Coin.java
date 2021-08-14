package org.example;

public class Coin {
    private final String name;
    private final String ticker;
    private final double factor;

    public Coin(String name, String ticker, double factor) {
        this.name = name;
        this.ticker = ticker;
        this.factor = factor;
    }

    public String getName() {
        return name;
    }

    public String getTicker() {
        return ticker;
    }

    public double getFactor() {
        return factor;
    }
}

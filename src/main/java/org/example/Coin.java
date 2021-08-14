package org.example;

public class Coin {
    private final String name;
    private final String prodId;
    private final int riskFactor;
    private final double diffAmtRequired;
    private final double firstOrderAmt;
    private boolean madeInitialOrder;
    // 3 => buy a lot when action says buy, sell a little when action says sell
    // 2 => buy / sell equal
    // 1 => buy a little when action says buy, sell a lot when action says sell
    private double[] factors = new double[]{ 0.25, 0.5, 0.75 };

    public Coin(String name, int riskFactor, double diffAmtRequired, double firstOrderAmt) {
        this.name = name;
        prodId = this.name + "-USD";
        this.riskFactor = riskFactor;
        this.diffAmtRequired = diffAmtRequired;
        this.firstOrderAmt = firstOrderAmt;
        madeInitialOrder = false;
    }

    public String getName() {
        return name;
    }

    public String getProdId() {
        return prodId;
    }

    public int getRiskFactor() {
        return riskFactor;
    }

    public double getBuyFactor() {
        return factors[riskFactor - 1];
    }

    public double getSellFactor() {
        return factors[factors.length - riskFactor];
    }

    public double getDiffAmtRequired() {
        return diffAmtRequired;
    }

    public double getFirstOrderAmt() {
        return firstOrderAmt;
    }

    public boolean getMadeInitialOrder() {
        return madeInitialOrder;
    }

    public void triggerMadeInitialOrder() {
        madeInitialOrder = true;
    }
}

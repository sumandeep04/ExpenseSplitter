package com.expensesplitter.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility methods for exact monetary calculations, rounding, and formatting.
 * Uses BigDecimal throughout to prevent floating-point inaccuracies.
 */
public final class MoneyUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    public static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private static String defaultCurrencySymbol = "₹";

    private MoneyUtils() {}

    public static synchronized void setDefaultCurrencySymbol(String symbol) {
        if (symbol != null && !symbol.trim().isEmpty()) {
            defaultCurrencySymbol = symbol.trim();
        }
    }

    public static synchronized String getDefaultCurrencySymbol() {
        return defaultCurrencySymbol;
    }

    /**
     * Formats a BigDecimal amount as e.g. "₹ 1,250.00"
     */
    public static String format(BigDecimal amount) {
        return format(amount, defaultCurrencySymbol);
    }

    /**
     * Formats a BigDecimal with a specific currency symbol.
     */
    public static String format(BigDecimal amount, String currencySymbol) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,##0.00", symbols);
        String symbol = (currencySymbol != null && !currencySymbol.isEmpty()) ? currencySymbol + " " : "";
        return symbol + formatter.format(amount.setScale(SCALE, ROUNDING));
    }

    /**
     * Formats amount without currency symbol, e.g. "1250.00"
     */
    public static String formatPlain(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(SCALE, ROUNDING).toPlainString();
    }

    /**
     * Parses a string input into a BigDecimal scaled to 2 decimal places.
     * Returns null if invalid.
     */
    public static BigDecimal parse(String input) {
        if (input == null) return null;
        String clean = input.trim().replace(",", "").replace(defaultCurrencySymbol, "").replace("$", "").trim();
        if (clean.isEmpty()) return null;
        try {
            BigDecimal val = new BigDecimal(clean);
            return val.setScale(SCALE, ROUNDING);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Splits a total amount equally among a given number of participants.
     * Guarantees that the sum of the returned shares equals the total amount exactly.
     * Any rounding difference (pennies/cents/paisa) is added deterministically
     * to the first remainder participants.
     *
     * Example: 1000.00 among 3 participants -> [333.34, 333.33, 333.33], sum = 1000.00
     */
    public static List<BigDecimal> splitEqually(BigDecimal totalAmount, int participantCount) {
        if (participantCount <= 0) {
            throw new IllegalArgumentException("Participant count must be greater than zero");
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
        totalAmount = totalAmount.setScale(SCALE, ROUNDING);

        List<BigDecimal> shares = new ArrayList<>(participantCount);
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            for (int i = 0; i < participantCount; i++) {
                shares.add(ZERO);
            }
            return shares;
        }

        // Calculate base share using down rounding to ensure we don't overshoot
        BigDecimal countBD = BigDecimal.valueOf(participantCount);
        BigDecimal baseShare = totalAmount.divide(countBD, SCALE, RoundingMode.FLOOR);

        // Calculate sum of base shares
        BigDecimal totalBase = baseShare.multiply(countBD).setScale(SCALE, ROUNDING);
        BigDecimal remainder = totalAmount.subtract(totalBase).setScale(SCALE, ROUNDING);

        // Remainder in cents (e.g. 0.02 -> 2 cents)
        int remainderCents = remainder.movePointRight(SCALE).intValueExact();
        BigDecimal oneCent = new BigDecimal("0.01");

        for (int i = 0; i < participantCount; i++) {
            if (i < remainderCents) {
                shares.add(baseShare.add(oneCent));
            } else {
                shares.add(baseShare);
            }
        }

        return shares;
    }

    /**
     * Splits total amount by given percentages.
     * Each percentage is converted to a monetary share, rounded to 2 decimal places.
     * Any remainder discrepancy (e.g. +/- 0.01) is reconciled against the largest percentage share
     * so that the sum of shares strictly equals the total amount.
     */
    public static List<BigDecimal> splitByPercentages(BigDecimal totalAmount, List<BigDecimal> percentages) {
        if (percentages == null || percentages.isEmpty()) {
            throw new IllegalArgumentException("Percentages list cannot be empty");
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
        totalAmount = totalAmount.setScale(SCALE, ROUNDING);

        // Check percentage sum
        BigDecimal totalPct = BigDecimal.ZERO;
        for (BigDecimal p : percentages) {
            if (p != null) {
                totalPct = totalPct.add(p);
            }
        }
        if (totalPct.setScale(SCALE, ROUNDING).compareTo(ONE_HUNDRED) != 0) {
            throw new IllegalArgumentException("Total percentage must equal 100.00%. Current sum: " + totalPct);
        }

        List<BigDecimal> shares = new ArrayList<>(percentages.size());
        BigDecimal runningSum = BigDecimal.ZERO;
        int maxIndex = 0;
        BigDecimal maxPct = BigDecimal.valueOf(-1);

        for (int i = 0; i < percentages.size(); i++) {
            BigDecimal p = percentages.get(i) != null ? percentages.get(i) : BigDecimal.ZERO;
            if (p.compareTo(maxPct) > 0) {
                maxPct = p;
                maxIndex = i;
            }
            BigDecimal share = totalAmount.multiply(p).divide(ONE_HUNDRED, SCALE, ROUNDING);
            shares.add(share);
            runningSum = runningSum.add(share);
        }

        // Adjust rounding discrepancy to the participant with the largest percentage
        BigDecimal diff = totalAmount.subtract(runningSum);
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal adjusted = shares.get(maxIndex).add(diff);
            shares.set(maxIndex, adjusted);
        }

        return shares;
    }

    /**
     * Verifies that the sum of custom shares matches the total amount within currency precision.
     */
    public static boolean validateCustomShares(BigDecimal totalAmount, List<BigDecimal> shares) {
        if (totalAmount == null || shares == null) return false;
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal share : shares) {
            if (share == null || share.compareTo(BigDecimal.ZERO) < 0) {
                return false;
            }
            sum = sum.add(share);
        }
        return sum.setScale(SCALE, ROUNDING).compareTo(totalAmount.setScale(SCALE, ROUNDING)) == 0;
    }
}

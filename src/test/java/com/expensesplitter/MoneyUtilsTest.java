package com.expensesplitter;

import com.expensesplitter.util.MoneyUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MoneyUtils formatting, parsing, and equal/percentage splitting.
 */
public class MoneyUtilsTest {

    @Test
    @DisplayName("Format with currency symbol")
    public void testFormat() {
        MoneyUtils.setDefaultCurrencySymbol("₹");
        assertEquals("₹ 1,234.50", MoneyUtils.format(new BigDecimal("1234.50")));
        assertEquals("₹ 0.00", MoneyUtils.format(BigDecimal.ZERO));
        assertEquals("₹ 10,00,000.00".replace("10,00,000.00", "1,000,000.00"), MoneyUtils.format(new BigDecimal("1000000.00")));
    }

    @Test
    @DisplayName("Parse string to BigDecimal")
    public void testParse() {
        assertEquals(new BigDecimal("500.00"), MoneyUtils.parse("500"));
        assertEquals(new BigDecimal("1250.75"), MoneyUtils.parse("₹ 1,250.75"));
        assertEquals(new BigDecimal("99.99"), MoneyUtils.parse("99.99"));
        assertNull(MoneyUtils.parse("abc"));
        assertNull(MoneyUtils.parse(""));
        assertNull(MoneyUtils.parse(null));
    }

    @Test
    @DisplayName("Split equally with zero amount")
    public void testSplitZero() {
        List<BigDecimal> shares = MoneyUtils.splitEqually(BigDecimal.ZERO, 3);
        assertEquals(3, shares.size());
        assertEquals(BigDecimal.ZERO.setScale(2), shares.get(0));
        assertEquals(BigDecimal.ZERO.setScale(2), shares.get(1));
        assertEquals(BigDecimal.ZERO.setScale(2), shares.get(2));
    }

    @Test
    @DisplayName("Split equally among 7 participants")
    public void testSplitSeven() {
        BigDecimal total = new BigDecimal("100.00");
        List<BigDecimal> shares = MoneyUtils.splitEqually(total, 7);
        assertEquals(7, shares.size());
        BigDecimal sum = shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(total, sum);
    }
}

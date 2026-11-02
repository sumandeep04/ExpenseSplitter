package com.expensesplitter;

import com.expensesplitter.model.ExpenseSplit;
import com.expensesplitter.model.Member;
import com.expensesplitter.service.ExpenseService;
import com.expensesplitter.util.MoneyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Equal, Unequal/Custom, and Percentage expense splitting.
 * Verifies rounding remainder penny distribution and exact precision.
 */
public class ExpenseSplitTest {

    private ExpenseService expenseService;
    private Member alice;
    private Member bob;
    private Member charlie;
    private Member david;

    @BeforeEach
    public void setup() {
        expenseService = new ExpenseService();
        alice = new Member(1, "Alice", "alice@test.com", "123", null);
        bob = new Member(2, "Bob", "bob@test.com", "456", null);
        charlie = new Member(3, "Charlie", "charlie@test.com", "789", null);
        david = new Member(4, "David", "david@test.com", "000", null);
    }

    @Test
    @DisplayName("1. Equal splitting among two members")
    public void testEqualSplitTwoMembers() {
        BigDecimal total = new BigDecimal("100.00");
        List<ExpenseSplit> splits = expenseService.calculateEqualSplits(total, List.of(alice, bob));

        assertEquals(2, splits.size());
        assertEquals(new BigDecimal("50.00"), splits.get(0).getShareAmount());
        assertEquals(new BigDecimal("50.00"), splits.get(1).getShareAmount());

        BigDecimal sum = splits.get(0).getShareAmount().add(splits.get(1).getShareAmount());
        assertEquals(total, sum);
    }

    @Test
    @DisplayName("2. Equal splitting with rounding remainder among three members (1000 / 3)")
    public void testEqualSplitThreeMembersRounding() {
        BigDecimal total = new BigDecimal("1000.00");
        List<ExpenseSplit> splits = expenseService.calculateEqualSplits(total, List.of(alice, bob, charlie));

        assertEquals(3, splits.size());
        // 1000.00 / 3 = 333.33 with 0.01 remainder distributed to the first participant
        assertEquals(new BigDecimal("333.34"), splits.get(0).getShareAmount());
        assertEquals(new BigDecimal("333.33"), splits.get(1).getShareAmount());
        assertEquals(new BigDecimal("333.33"), splits.get(2).getShareAmount());

        BigDecimal sum = splits.stream().map(ExpenseSplit::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("1000.00"), sum);
    }

    @Test
    @DisplayName("3. Equal splitting among four members")
    public void testEqualSplitFourMembers() {
        BigDecimal total = new BigDecimal("1200.00");
        List<ExpenseSplit> splits = expenseService.calculateEqualSplits(total, List.of(alice, bob, charlie, david));

        assertEquals(4, splits.size());
        for (ExpenseSplit split : splits) {
            assertEquals(new BigDecimal("300.00"), split.getShareAmount());
        }

        BigDecimal sum = splits.stream().map(ExpenseSplit::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("1200.00"), sum);
    }

    @Test
    @DisplayName("4. Custom split validation - valid matching sum")
    public void testCustomSplitValid() {
        BigDecimal total = new BigDecimal("2000.00");
        Map<Member, BigDecimal> customShares = new LinkedHashMap<>();
        customShares.put(alice, new BigDecimal("500.00"));
        customShares.put(bob, new BigDecimal("500.00"));
        customShares.put(charlie, new BigDecimal("700.00"));
        customShares.put(david, new BigDecimal("300.00"));

        List<ExpenseSplit> splits = expenseService.calculateCustomSplits(total, customShares);
        assertEquals(4, splits.size());

        BigDecimal sum = splits.stream().map(ExpenseSplit::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(total, sum);
    }

    @Test
    @DisplayName("5. Custom split validation - mismatching sum throws exception")
    public void testCustomSplitMismatchThrows() {
        BigDecimal total = new BigDecimal("2000.00");
        Map<Member, BigDecimal> customShares = new LinkedHashMap<>();
        customShares.put(alice, new BigDecimal("500.00"));
        customShares.put(bob, new BigDecimal("500.00"));
        customShares.put(charlie, new BigDecimal("700.00"));
        customShares.put(david, new BigDecimal("200.00")); // Sum is 1900.00, not 2000.00!

        assertThrows(IllegalArgumentException.class, () -> {
            expenseService.calculateCustomSplits(total, customShares);
        });
    }

    @Test
    @DisplayName("6. Percentage split validation - converts to exact monetary amounts")
    public void testPercentageSplitValid() {
        BigDecimal total = new BigDecimal("1000.00");
        Map<Member, BigDecimal> percentages = new LinkedHashMap<>();
        percentages.put(alice, new BigDecimal("40.00"));
        percentages.put(bob, new BigDecimal("30.00"));
        percentages.put(charlie, new BigDecimal("20.00"));
        percentages.put(david, new BigDecimal("10.00"));

        List<ExpenseSplit> splits = expenseService.calculatePercentageSplits(total, percentages);
        assertEquals(4, splits.size());

        assertEquals(new BigDecimal("400.00"), splits.get(0).getShareAmount());
        assertEquals(new BigDecimal("300.00"), splits.get(1).getShareAmount());
        assertEquals(new BigDecimal("200.00"), splits.get(2).getShareAmount());
        assertEquals(new BigDecimal("100.00"), splits.get(3).getShareAmount());

        BigDecimal sum = splits.stream().map(ExpenseSplit::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(total, sum);
    }

    @Test
    @DisplayName("7. Percentage split validation - invalid percentage total throws exception")
    public void testPercentageSplitInvalidTotalThrows() {
        BigDecimal total = new BigDecimal("1000.00");
        Map<Member, BigDecimal> percentages = new LinkedHashMap<>();
        percentages.put(alice, new BigDecimal("50.00"));
        percentages.put(bob, new BigDecimal("40.00")); // Sum is 90%, not 100%!

        assertThrows(IllegalArgumentException.class, () -> {
            expenseService.calculatePercentageSplits(total, percentages);
        });
    }

    @Test
    @DisplayName("8. Percentage split with repeating fractions (33.33%, 33.33%, 33.34%)")
    public void testPercentageSplitRoundingReconciliation() {
        BigDecimal total = new BigDecimal("100.00");
        Map<Member, BigDecimal> percentages = new LinkedHashMap<>();
        percentages.put(alice, new BigDecimal("33.34"));
        percentages.put(bob, new BigDecimal("33.33"));
        percentages.put(charlie, new BigDecimal("33.33"));

        List<ExpenseSplit> splits = expenseService.calculatePercentageSplits(total, percentages);
        BigDecimal sum = splits.stream().map(ExpenseSplit::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("100.00"), sum);
    }
}

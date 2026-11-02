package com.expensesplitter;

import com.expensesplitter.dao.ExpenseDAO;
import com.expensesplitter.dao.GroupDAO;
import com.expensesplitter.dao.MemberDAO;
import com.expensesplitter.dao.SettlementDAO;
import com.expensesplitter.database.Database;
import com.expensesplitter.database.DatabaseInitializer;
import com.expensesplitter.model.*;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.ExpenseService;
import com.expensesplitter.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Balance Calculation (Feature G) and Group Isolation.
 */
public class BalanceServiceTest {

    private GroupDAO groupDAO;
    private MemberDAO memberDAO;
    private ExpenseDAO expenseDAO;
    private SettlementDAO settlementDAO;
    private ExpenseService expenseService;
    private BalanceService balanceService;
    private SettlementService settlementService;

    private Member alice;
    private Member bob;
    private Member charlie;
    private Member david;
    private Group tripGroup;

    @BeforeEach
    public void setup() throws Exception {
        // Use fresh isolated temporary SQLite database file for each test
        java.io.File tempDb = java.io.File.createTempFile("test_balance_", ".db");
        tempDb.deleteOnExit();
        Database.setCustomJdbcUrl("jdbc:sqlite:" + tempDb.getAbsolutePath());
        DatabaseInitializer.initialize();

        groupDAO = new GroupDAO();
        memberDAO = new MemberDAO();
        expenseDAO = new ExpenseDAO();
        settlementDAO = new SettlementDAO();

        expenseService = new ExpenseService(expenseDAO, memberDAO);
        balanceService = new BalanceService(memberDAO, expenseDAO, settlementDAO);
        settlementService = new SettlementService(balanceService, settlementDAO);

        alice = memberDAO.create(new Member("Alice", "alice@example.com", "111"));
        bob = memberDAO.create(new Member("Bob", "bob@example.com", "222"));
        charlie = memberDAO.create(new Member("Charlie", "charlie@example.com", "333"));
        david = memberDAO.create(new Member("David", "david@example.com", "444"));

        tripGroup = new Group("Goa Trip", "Trip expenses", GroupCategory.TRIP);
        tripGroup.setMembers(List.of(alice, bob, charlie, david));
        tripGroup = groupDAO.create(tripGroup);
    }

    @Test
    @DisplayName("1. Single expense balance calculation (A pays 1200 for 4 members)")
    public void testSingleExpenseEqualBalance() {
        // Alice pays 1200 split equally among A, B, C, D (300 each)
        List<ExpenseSplit> splits = expenseService.calculateEqualSplits(new BigDecimal("1200.00"), List.of(alice, bob, charlie, david));
        expenseService.createExpense(tripGroup.getId(), "Villa Stay", new BigDecimal("1200.00"),
                alice.getId(), LocalDate.now(), ExpenseCategory.ACCOMMODATION, null, SplitMethod.EQUAL, splits);

        Map<Integer, BigDecimal> balances = balanceService.getNetBalancesMap(tripGroup.getId());

        // Alice: Paid 1200, Share 300 -> Net +900.00
        assertEquals(new BigDecimal("900.00"), balances.get(alice.getId()));
        // Bob: Paid 0, Share 300 -> Net -300.00
        assertEquals(new BigDecimal("-300.00"), balances.get(bob.getId()));
        // Charlie: Paid 0, Share 300 -> Net -300.00
        assertEquals(new BigDecimal("-300.00"), balances.get(charlie.getId()));
        // David: Paid 0, Share 300 -> Net -300.00
        assertEquals(new BigDecimal("-300.00"), balances.get(david.getId()));
    }

    @Test
    @DisplayName("2. Multiple expenses balance calculations")
    public void testMultipleExpensesBalance() {
        // Expense 1: Alice pays 1200 equal for all 4 (300 each)
        List<ExpenseSplit> s1 = expenseService.calculateEqualSplits(new BigDecimal("1200.00"), List.of(alice, bob, charlie, david));
        expenseService.createExpense(tripGroup.getId(), "Villa", new BigDecimal("1200.00"),
                alice.getId(), LocalDate.now(), ExpenseCategory.ACCOMMODATION, null, SplitMethod.EQUAL, s1);

        // Expense 2: Bob pays 400 equal for all 4 (100 each)
        List<ExpenseSplit> s2 = expenseService.calculateEqualSplits(new BigDecimal("400.00"), List.of(alice, bob, charlie, david));
        expenseService.createExpense(tripGroup.getId(), "Dinner", new BigDecimal("400.00"),
                bob.getId(), LocalDate.now(), ExpenseCategory.FOOD, null, SplitMethod.EQUAL, s2);

        Map<Integer, BigDecimal> balances = balanceService.getNetBalancesMap(tripGroup.getId());

        // Alice: Paid 1200, Share 300 + 100 = 400 -> Net +800.00
        assertEquals(new BigDecimal("800.00"), balances.get(alice.getId()));
        // Bob: Paid 400, Share 300 + 100 = 400 -> Net 0.00 (Settled!)
        assertEquals(new BigDecimal("0.00"), balances.get(bob.getId()));
        // Charlie: Paid 0, Share 300 + 100 = 400 -> Net -400.00
        assertEquals(new BigDecimal("-400.00"), balances.get(charlie.getId()));
        // David: Paid 0, Share 300 + 100 = 400 -> Net -400.00
        assertEquals(new BigDecimal("-400.00"), balances.get(david.getId()));
    }

    @Test
    @DisplayName("3. Expense involving only a subset of members")
    public void testSubsetMemberExpense() {
        // Charlie pays 600 only shared between Charlie and David (300 each), Alice and Bob are excluded
        List<ExpenseSplit> s = expenseService.calculateEqualSplits(new BigDecimal("600.00"), List.of(charlie, david));
        expenseService.createExpense(tripGroup.getId(), "Rental Car", new BigDecimal("600.00"),
                charlie.getId(), LocalDate.now(), ExpenseCategory.TRAVEL, null, SplitMethod.EQUAL, s);

        Map<Integer, BigDecimal> balances = balanceService.getNetBalancesMap(tripGroup.getId());

        assertEquals(new BigDecimal("0.00"), balances.get(alice.getId()));
        assertEquals(new BigDecimal("0.00"), balances.get(bob.getId()));
        assertEquals(new BigDecimal("300.00"), balances.get(charlie.getId()));
        assertEquals(new BigDecimal("-300.00"), balances.get(david.getId()));
    }

    @Test
    @DisplayName("4. Group with no expenses returns zero balances")
    public void testEmptyGroupBalances() {
        Group emptyGroup = new Group("Empty Group", "No expenses", GroupCategory.OTHER);
        emptyGroup.setMembers(List.of(alice, bob));
        emptyGroup = groupDAO.create(emptyGroup);

        List<MemberBalance> balances = balanceService.calculateGroupBalances(emptyGroup.getId());
        assertEquals(2, balances.size());
        for (MemberBalance mb : balances) {
            assertEquals(new BigDecimal("0.00"), mb.getNetBalance());
            assertTrue(mb.isSettled());
        }
    }

    @Test
    @DisplayName("5. Group isolation: expenses in Group A do not affect Group B")
    public void testGroupIsolation() {
        // Group A: Alice pays 500 for Alice and Bob
        List<ExpenseSplit> sA = expenseService.calculateEqualSplits(new BigDecimal("500.00"), List.of(alice, bob));
        expenseService.createExpense(tripGroup.getId(), "Group A Expense", new BigDecimal("500.00"),
                alice.getId(), LocalDate.now(), ExpenseCategory.FOOD, null, SplitMethod.EQUAL, sA);

        // Group B: Flatmates
        Group flatGroup = new Group("Flat 402", "Apartment", GroupCategory.HOME);
        flatGroup.setMembers(List.of(alice, bob));
        flatGroup = groupDAO.create(flatGroup);

        // Group B: Bob pays 200 for Alice and Bob
        List<ExpenseSplit> sB = expenseService.calculateEqualSplits(new BigDecimal("200.00"), List.of(alice, bob));
        expenseService.createExpense(flatGroup.getId(), "Group B Expense", new BigDecimal("200.00"),
                bob.getId(), LocalDate.now(), ExpenseCategory.BILLS, null, SplitMethod.EQUAL, sB);

        // Verify Group A balances
        Map<Integer, BigDecimal> balancesA = balanceService.getNetBalancesMap(tripGroup.getId());
        assertEquals(new BigDecimal("250.00"), balancesA.get(alice.getId()));
        assertEquals(new BigDecimal("-250.00"), balancesA.get(bob.getId()));

        // Verify Group B balances
        Map<Integer, BigDecimal> balancesB = balanceService.getNetBalancesMap(flatGroup.getId());
        assertEquals(new BigDecimal("-100.00"), balancesB.get(alice.getId()));
        assertEquals(new BigDecimal("100.00"), balancesB.get(bob.getId()));
    }
}

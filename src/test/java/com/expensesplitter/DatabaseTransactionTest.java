package com.expensesplitter;

import com.expensesplitter.dao.ExpenseDAO;
import com.expensesplitter.dao.GroupDAO;
import com.expensesplitter.dao.MemberDAO;
import com.expensesplitter.database.Database;
import com.expensesplitter.database.DatabaseInitializer;
import com.expensesplitter.model.*;
import com.expensesplitter.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Database Transactions, Rollbacks, and Cascade Integrity.
 */
public class DatabaseTransactionTest {

    private GroupDAO groupDAO;
    private MemberDAO memberDAO;
    private ExpenseDAO expenseDAO;
    private ExpenseService expenseService;

    private Member alice;
    private Member bob;
    private Group group;

    @BeforeEach
    public void setup() throws Exception {
        java.io.File tempDb = java.io.File.createTempFile("test_tx_", ".db");
        tempDb.deleteOnExit();
        Database.setCustomJdbcUrl("jdbc:sqlite:" + tempDb.getAbsolutePath());
        DatabaseInitializer.initialize();

        groupDAO = new GroupDAO();
        memberDAO = new MemberDAO();
        expenseDAO = new ExpenseDAO();
        expenseService = new ExpenseService(expenseDAO, memberDAO);

        alice = memberDAO.create(new Member("Alice", "alice@test.com", "111"));
        bob = memberDAO.create(new Member("Bob", "bob@test.com", "222"));

        group = new Group("Trip", "Vacation", GroupCategory.TRIP);
        group.setMembers(List.of(alice, bob));
        group = groupDAO.create(group);
    }

    @Test
    @DisplayName("9. Expense editing updates split records and maintains consistency")
    public void testExpenseEditing() {
        // Initial expense: 100.00 split 50/50
        List<ExpenseSplit> initialSplits = expenseService.calculateEqualSplits(new BigDecimal("100.00"), List.of(alice, bob));
        Expense expense = expenseService.createExpense(group.getId(), "Initial Lunch", new BigDecimal("100.00"),
                alice.getId(), LocalDate.now(), ExpenseCategory.FOOD, "Notes", SplitMethod.EQUAL, initialSplits);

        // Edit expense: change amount to 200.00, split 100/100
        List<ExpenseSplit> updatedSplits = expenseService.calculateEqualSplits(new BigDecimal("200.00"), List.of(alice, bob));
        expenseService.updateExpense(expense.getId(), group.getId(), "Updated Fancy Lunch", new BigDecimal("200.00"),
                alice.getId(), LocalDate.now(), ExpenseCategory.FOOD, "Updated Notes", SplitMethod.EQUAL, updatedSplits);

        Expense reloaded = expenseDAO.findById(expense.getId());
        assertEquals("Updated Fancy Lunch", reloaded.getDescription());
        assertEquals(new BigDecimal("200.00"), reloaded.getAmount());
        assertEquals(2, reloaded.getSplits().size());
        assertEquals(new BigDecimal("100.00"), reloaded.getSplits().get(0).getShareAmount());
        assertEquals(new BigDecimal("100.00"), reloaded.getSplits().get(1).getShareAmount());
    }

    @Test
    @DisplayName("10. Expense deletion removes expense and associated splits")
    public void testExpenseDeletion() {
        List<ExpenseSplit> splits = expenseService.calculateEqualSplits(new BigDecimal("100.00"), List.of(alice, bob));
        Expense expense = expenseService.createExpense(group.getId(), "Snacks", new BigDecimal("100.00"),
                alice.getId(), LocalDate.now(), ExpenseCategory.FOOD, null, SplitMethod.EQUAL, splits);

        assertEquals(1, expenseDAO.findByGroupId(group.getId()).size());

        // Delete expense
        expenseService.deleteExpense(expense.getId());

        assertEquals(0, expenseDAO.findByGroupId(group.getId()).size());
        assertNull(expenseDAO.findById(expense.getId()));
        assertEquals(0, expenseDAO.findSplitsByExpenseId(expense.getId()).size());
    }

    @Test
    @DisplayName("12. Database transaction rollback when split insert fails (Atomicity)")
    public void testTransactionRollbackOnSplitFailure() {
        // Attempt creating an expense with an invalid split targeting a non-existent member (e.g. member ID 99999)
        // With SQLite foreign keys enabled, inserting member_id 99999 fails with a FOREIGN KEY constraint violation.
        ExpenseSplit invalidSplit = new ExpenseSplit(99999, "Ghost", new BigDecimal("100.00"));

        Expense expense = new Expense();
        expense.setGroupId(group.getId());
        expense.setDescription("Failed Expense");
        expense.setAmount(new BigDecimal("100.00"));
        expense.setPaidByMemberId(alice.getId());
        expense.setExpenseDate(LocalDate.now());
        expense.setCategory(ExpenseCategory.FOOD);
        expense.setSplitMethod(SplitMethod.EQUAL);
        expense.setSplits(List.of(invalidSplit));

        assertThrows(RuntimeException.class, () -> {
            expenseDAO.create(expense);
        });

        // Verify transaction rolled back: NO expenses were created
        List<Expense> allExpenses = expenseDAO.findByGroupId(group.getId());
        assertTrue(allExpenses.isEmpty(), "Expense row should have rolled back completely");
    }

    @Test
    @DisplayName("13. Foreign-key cascading: Deleting a group cascades to its expenses")
    public void testCascadeGroupDeletion() {
        List<ExpenseSplit> splits = expenseService.calculateEqualSplits(new BigDecimal("100.00"), List.of(alice, bob));
        expenseService.createExpense(group.getId(), "Cab", new BigDecimal("100.00"),
                alice.getId(), LocalDate.now(), ExpenseCategory.TRAVEL, null, SplitMethod.EQUAL, splits);

        assertEquals(1, expenseDAO.findByGroupId(group.getId()).size());

        // Delete group
        groupDAO.delete(group.getId());

        // Group deleted
        assertNull(groupDAO.findById(group.getId()));
        // Expenses for that group cascaded
        assertEquals(0, expenseDAO.findByGroupId(group.getId()).size());
    }
}

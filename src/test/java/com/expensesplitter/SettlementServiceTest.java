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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Settlement Optimization, Partial Payments, and Duplicate Prevention.
 */
public class SettlementServiceTest {

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
    private Group group;

    @BeforeEach
    public void setup() throws Exception {
        java.io.File tempDb = java.io.File.createTempFile("test_settlement_", ".db");
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

        alice = memberDAO.create(new Member("Alice", "alice@test.com", "111"));
        bob = memberDAO.create(new Member("Bob", "bob@test.com", "222"));
        charlie = memberDAO.create(new Member("Charlie", "charlie@test.com", "333"));
        david = memberDAO.create(new Member("David", "david@test.com", "444"));

        group = new Group("Trip", "Vacation", GroupCategory.TRIP);
        group.setMembers(List.of(alice, bob, charlie, david));
        group = groupDAO.create(group);
    }

    @Test
    @DisplayName("7. Settlement optimization algorithm minimizes transactions")
    public void testSettlementOptimization() {
        // Alice pays 1200 for all 4 (300 each). Balances: Alice +900, Bob -300, Charlie -300, David -300
        List<ExpenseSplit> s = expenseService.calculateEqualSplits(new BigDecimal("1200.00"), List.of(alice, bob, charlie, david));
        expenseService.createExpense(group.getId(), "Stay", new BigDecimal("1200.00"),
                alice.getId(), LocalDate.now(), ExpenseCategory.ACCOMMODATION, null, SplitMethod.EQUAL, s);

        List<SettlementSuggestion> suggestions = settlementService.calculateOptimizedSettlements(group.getId());

        assertEquals(3, suggestions.size());

        BigDecimal totalSettled = BigDecimal.ZERO;
        for (SettlementSuggestion sugg : suggestions) {
            assertEquals(alice.getId(), sugg.getToMemberId()); // All pay Alice
            assertEquals(new BigDecimal("300.00"), sugg.getAmount());
            totalSettled = totalSettled.add(sugg.getAmount());
        }
        assertEquals(new BigDecimal("900.00"), totalSettled);
    }

    @Test
    @DisplayName("8. Partial settlement payment tracks remaining balance accurately")
    public void testPartialSettlementPayment() {
        Settlement s = settlementService.createSettlement(
                group.getId(), bob.getId(), bob.getName(),
                alice.getId(), alice.getName(),
                new BigDecimal("500.00"), LocalDate.now().plusDays(5), "Partial test"
        );

        assertEquals(SettlementStatus.PENDING, s.getStatus());
        assertEquals(new BigDecimal("500.00"), s.getRemainingAmount());

        // Pay 200.00 partially
        settlementService.recordPayment(s.getId(), new BigDecimal("200.00"), LocalDate.now(), "First installment");

        Settlement after1 = settlementDAO.findById(s.getId());
        assertEquals(SettlementStatus.PENDING, after1.getStatus());
        assertEquals(new BigDecimal("200.00"), after1.getAmountPaid());
        assertEquals(new BigDecimal("300.00"), after1.getRemainingAmount());

        // Pay remaining 300.00
        settlementService.recordPayment(s.getId(), new BigDecimal("300.00"), LocalDate.now(), "Final installment");

        Settlement after2 = settlementDAO.findById(s.getId());
        assertEquals(SettlementStatus.COMPLETED, after2.getStatus());
        assertEquals(new BigDecimal("500.00"), after2.getAmountPaid());
        assertEquals(new BigDecimal("0.00"), after2.getRemainingAmount());
        assertTrue(after2.isFullyPaid());
    }

    @Test
    @DisplayName("11. Prevention of duplicate settlement processing")
    public void testPreventDuplicateSettlement() {
        Settlement s = settlementService.createSettlement(
                group.getId(), bob.getId(), bob.getName(),
                alice.getId(), alice.getName(),
                new BigDecimal("100.00"), LocalDate.now(), "Duplicate test"
        );

        // Complete settlement
        settlementService.markFullySettled(s.getId(), LocalDate.now(), "Paid");

        Settlement completed = settlementDAO.findById(s.getId());
        assertEquals(SettlementStatus.COMPLETED, completed.getStatus());

        // Attempting to pay again on completed settlement must throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            settlementService.recordPayment(s.getId(), new BigDecimal("50.00"), LocalDate.now(), "Duplicate");
        });
    }
}

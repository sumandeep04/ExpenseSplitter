package com.expensesplitter;

import com.expensesplitter.database.Database;
import com.expensesplitter.database.DatabaseInitializer;
import com.expensesplitter.dao.GroupDAO;
import com.expensesplitter.dao.MemberDAO;
import com.expensesplitter.model.Expense;
import com.expensesplitter.model.Member;
import com.expensesplitter.model.MemberBalance;
import com.expensesplitter.model.Settlement;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.ExpenseService;
import com.expensesplitter.service.SettlementService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest
class WebPagesTest {
    private static final Path TEST_DATABASE = Path.of("target", "web-pages-test.db").toAbsolutePath();

    static {
        Database.setCustomJdbcUrl("jdbc:sqlite:" + TEST_DATABASE);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GroupDAO groupDAO;
    @Autowired
    private MemberDAO memberDAO;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private BalanceService balanceService;
    @Autowired
    private SettlementService settlementService;

    @BeforeAll
    static void initializeEmptyDatabase() {
        DatabaseInitializer.initialize();
    }

    @AfterAll
    static void removeTestDatabase() throws Exception {
        Database.setCustomJdbcUrl(null);
        Files.deleteIfExists(TEST_DATABASE);
    }

    @Test
    void rendersAllMainPagesWithAnEmptyDatabase() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No groups yet")));
        mockMvc.perform(get("/groups"))
                .andExpect(status().isOk())
                .andExpect(view().name("groups"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create a group")));
        mockMvc.perform(get("/members"))
                .andExpect(status().isOk())
                .andExpect(view().name("members"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create a group first")));
        mockMvc.perform(get("/expenses"))
                .andExpect(status().isOk())
                .andExpect(view().name("expenses"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create a group first")));
        mockMvc.perform(get("/settlements"))
                .andExpect(status().isOk())
                .andExpect(view().name("settlements"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tracked settlements")));

        mockMvc.perform(post("/groups/save")
                        .param("name", "Weekend trip")
                        .param("description", "Shared getaway")
                        .param("category", "TRIP"))
                .andExpect(redirectedUrl("/groups"));
        int groupId = groupDAO.findAll().get(0).getId();
        mockMvc.perform(post("/members/save").param("groupId", Integer.toString(groupId)).param("name", "Alice"))
                .andExpect(redirectedUrl("/members?groupId=" + groupId));
        mockMvc.perform(post("/members/save").param("groupId", Integer.toString(groupId)).param("name", "Bob"))
                .andExpect(redirectedUrl("/members?groupId=" + groupId));
        List<Member> members = memberDAO.findByGroupId(groupId);
        int aliceId = members.get(0).getId();
        int bobId = members.get(1).getId();

        mockMvc.perform(post("/expenses/save")
                        .param("groupId", Integer.toString(groupId))
                        .param("description", "Dinner")
                        .param("amount", "100.00")
                        .param("paidByMemberId", Integer.toString(aliceId))
                        .param("expenseDate", "2026-10-22")
                        .param("category", "FOOD")
                        .param("splitMethod", "CUSTOM")
                        .param("participantIds", Integer.toString(aliceId), Integer.toString(bobId))
                        .param("share_" + aliceId, "60.00")
                        .param("share_" + bobId, "40.00"))
                .andExpect(redirectedUrl("/expenses?groupId=" + groupId));
        List<Expense> expenses = expenseService.getExpensesByGroup(groupId);
        assertEquals(1, expenses.size());
        assertEquals(2, expenses.get(0).getSplits().size());
        mockMvc.perform(get("/").param("groupId", Integer.toString(groupId)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Weekend trip")));
        mockMvc.perform(get("/groups"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Weekend trip")));
        mockMvc.perform(get("/members").param("groupId", Integer.toString(groupId)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alice")));
        mockMvc.perform(get("/expenses").param("groupId", Integer.toString(groupId)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dinner")));
        mockMvc.perform(get("/settlements").param("groupId", Integer.toString(groupId)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bob")));

        List<MemberBalance> balances = balanceService.calculateGroupBalances(groupId);
        assertEquals(new java.math.BigDecimal("40.00"), balances.get(0).getNetBalance());
        assertEquals(new java.math.BigDecimal("-40.00"), balances.get(1).getNetBalance());
        assertEquals(1, settlementService.calculateOptimizedSettlements(groupId).size());

        mockMvc.perform(post("/settlements/create")
                        .param("groupId", Integer.toString(groupId))
                        .param("fromMemberId", Integer.toString(bobId))
                        .param("toMemberId", Integer.toString(aliceId))
                        .param("amount", "40.00"))
                .andExpect(redirectedUrl("/settlements?groupId=" + groupId));
        Settlement settlement = settlementService.getSettlementsByGroup(groupId).get(0);
        mockMvc.perform(post("/settlements/" + settlement.getId() + "/pay").param("groupId", Integer.toString(groupId)))
                .andExpect(redirectedUrl("/settlements?groupId=" + groupId));

        int expenseId = expenses.get(0).getId();
        mockMvc.perform(post("/expenses/save")
                        .param("groupId", Integer.toString(groupId))
                        .param("expenseId", Integer.toString(expenseId))
                        .param("description", "Updated dinner")
                        .param("amount", "90.00")
                        .param("paidByMemberId", Integer.toString(bobId))
                        .param("expenseDate", "2026-10-22")
                        .param("category", "FOOD")
                        .param("splitMethod", "EQUAL")
                        .param("participantIds", Integer.toString(aliceId), Integer.toString(bobId)))
                .andExpect(redirectedUrl("/expenses?groupId=" + groupId));
        assertEquals("Updated dinner", expenseService.getExpenseById(expenseId).getDescription());
        mockMvc.perform(post("/expenses/" + expenseId + "/delete").param("groupId", Integer.toString(groupId)))
                .andExpect(redirectedUrl("/expenses?groupId=" + groupId));
        assertTrue(expenseService.getExpensesByGroup(groupId).isEmpty());

        mockMvc.perform(post("/expenses/save")
                        .param("groupId", Integer.toString(groupId))
                        .param("description", "Shared fee")
                        .param("amount", "20.00")
                        .param("paidByMemberId", Integer.toString(aliceId))
                        .param("expenseDate", "2026-10-22")
                        .param("category", "OTHER")
                        .param("splitMethod", "PERCENTAGE")
                        .param("participantIds", Integer.toString(aliceId), Integer.toString(bobId))
                        .param("percentage_" + aliceId, "25.00")
                        .param("percentage_" + bobId, "75.00"))
                .andExpect(redirectedUrl("/expenses?groupId=" + groupId));
        Expense percentageExpense = expenseService.getExpensesByGroup(groupId).get(0);
        assertEquals(new java.math.BigDecimal("5.00"), percentageExpense.getSplits().get(0).getShareAmount());
        mockMvc.perform(post("/expenses/" + percentageExpense.getId() + "/delete").param("groupId", Integer.toString(groupId)))
                .andExpect(redirectedUrl("/expenses?groupId=" + groupId));
        assertTrue(expenseService.getExpensesByGroup(groupId).isEmpty());
        mockMvc.perform(post("/groups/" + groupId + "/delete"))
                .andExpect(redirectedUrl("/groups"));
        assertTrue(groupDAO.findAll().isEmpty());
    }
}

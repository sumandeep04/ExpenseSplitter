package com.expensesplitter.database;

import com.expensesplitter.dao.ExpenseDAO;
import com.expensesplitter.dao.GroupDAO;
import com.expensesplitter.dao.MemberDAO;
import com.expensesplitter.dao.SettlementDAO;
import com.expensesplitter.model.*;
import com.expensesplitter.service.ExpenseService;
import com.expensesplitter.service.SettlementService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

/**
 * Service to load or clear realistic sample demonstration data for viva / presentations.
 * Strictly optional — never automatically injected on startup.
 */
public class DemoDataService {

    private final MemberDAO memberDAO = new MemberDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final SettlementDAO settlementDAO = new SettlementDAO();
    private final ExpenseService expenseService = new ExpenseService(expenseDAO, memberDAO);
    private final SettlementService settlementService = new SettlementService();

    /**
     * Loads a complete, realistic demo dataset including groups, members,
     * equal, unequal, and percentage split expenses, and a sample settlement reminder.
     */
    public void loadDemoData() {
        // 1. Create Members
        Member aarav = memberDAO.create(new Member("Aarav Sharma (You)", "aarav@example.com", "+91 98765 43210"));
        Member diya = memberDAO.create(new Member("Diya Patel", "diya@example.com", "+91 98765 43211"));
        Member rohan = memberDAO.create(new Member("Rohan Mehta", "rohan@example.com", "+91 98765 43212"));
        Member sneha = memberDAO.create(new Member("Sneha Rao", "sneha@example.com", "+91 98765 43213"));

        // 2. Create Group 1: Goa Beach Trip
        Group goaTrip = new Group("Goa Beach Trip", "Annual friends holiday trip to North Goa", GroupCategory.TRIP);
        goaTrip.setMembers(List.of(aarav, diya, rohan, sneha));
        goaTrip = groupDAO.create(goaTrip);

        // Group 1 - Expense 1: Villa Booking (Equal split - ₹12,000 paid by Aarav)
        List<ExpenseSplit> villaSplits = expenseService.calculateEqualSplits(new BigDecimal("12000.00"), List.of(aarav, diya, rohan, sneha));
        expenseService.createExpense(goaTrip.getId(), "Beachside Villa 3-Night Stay", new BigDecimal("12000.00"),
                aarav.getId(), LocalDate.now().minusDays(5), ExpenseCategory.ACCOMMODATION,
                "Booked 3 BHK private pool villa in Anjuna", SplitMethod.EQUAL, villaSplits);

        // Group 1 - Expense 2: Seafood Dinner (Equal split - ₹4,500 paid by Diya)
        List<ExpenseSplit> dinnerSplits = expenseService.calculateEqualSplits(new BigDecimal("4500.00"), List.of(aarav, diya, rohan, sneha));
        expenseService.createExpense(goaTrip.getId(), "Seafood Dinner & Live Music", new BigDecimal("4500.00"),
                diya.getId(), LocalDate.now().minusDays(4), ExpenseCategory.FOOD,
                "Dinner at Fisherman's Wharf", SplitMethod.EQUAL, dinnerSplits);

        // Group 1 - Expense 3: Scooter Rentals (Custom Unequal Split - ₹2,400 paid by Rohan)
        Map<Member, BigDecimal> scooterShares = new LinkedHashMap<>();
        scooterShares.put(aarav, new BigDecimal("600.00"));
        scooterShares.put(diya, new BigDecimal("600.00"));
        scooterShares.put(rohan, new BigDecimal("600.00"));
        scooterShares.put(sneha, new BigDecimal("600.00"));
        List<ExpenseSplit> scooterSplits = expenseService.calculateCustomSplits(new BigDecimal("2400.00"), scooterShares);
        expenseService.createExpense(goaTrip.getId(), "4x Activa Scooter Rentals", new BigDecimal("2400.00"),
                rohan.getId(), LocalDate.now().minusDays(3), ExpenseCategory.TRAVEL,
                "2 days rental + fuel", SplitMethod.CUSTOM, scooterSplits);

        // Group 1 - Expense 4: Water Sports (Percentage Split - ₹5,000 paid by Sneha)
        Map<Member, BigDecimal> sportsPercentages = new LinkedHashMap<>();
        sportsPercentages.put(aarav, new BigDecimal("30.00")); // Aarav did extra parasailing
        sportsPercentages.put(diya, new BigDecimal("20.00"));
        sportsPercentages.put(rohan, new BigDecimal("25.00"));
        sportsPercentages.put(sneha, new BigDecimal("25.00"));
        List<ExpenseSplit> sportsSplits = expenseService.calculatePercentageSplits(new BigDecimal("5000.00"), sportsPercentages);
        expenseService.createExpense(goaTrip.getId(), "Water Sports at Calangute", new BigDecimal("5000.00"),
                sneha.getId(), LocalDate.now().minusDays(2), ExpenseCategory.ENTERTAINMENT,
                "Jet ski and parasailing package", SplitMethod.PERCENTAGE, sportsSplits);

        // 3. Create Group 2: Flat 402 - Roommates
        Group flat = new Group("Flat 402 - Roommates", "Shared apartment expenses and monthly groceries", GroupCategory.HOME);
        flat.setMembers(List.of(aarav, rohan, diya));
        flat = groupDAO.create(flat);

        // Flat Expense 1: Groceries & Wi-Fi (₹3,300 paid by Aarav)
        List<ExpenseSplit> flatSplits1 = expenseService.calculateEqualSplits(new BigDecimal("3300.00"), List.of(aarav, rohan, diya));
        expenseService.createExpense(flat.getId(), "Monthly Fiber Internet & Kitchen Staples", new BigDecimal("3300.00"),
                aarav.getId(), LocalDate.now().minusDays(6), ExpenseCategory.BILLS,
                "Airtel Xstream broadband + supermarket restock", SplitMethod.EQUAL, flatSplits1);

        // Flat Expense 2: Electricity Bill (₹1,500 paid by Rohan)
        List<ExpenseSplit> flatSplits2 = expenseService.calculateEqualSplits(new BigDecimal("1500.00"), List.of(aarav, rohan, diya));
        expenseService.createExpense(flat.getId(), "Electricity & Water Utility Bill", new BigDecimal("1500.00"),
                rohan.getId(), LocalDate.now().minusDays(1), ExpenseCategory.BILLS,
                "State electricity board bill for previous month", SplitMethod.EQUAL, flatSplits2);

        // 4. Create Group 3: College Final Project
        Group college = new Group("CSE Final Year Project", "Hardware parts, cloud hosting and printing", GroupCategory.COLLEGE);
        college.setMembers(List.of(aarav, diya, sneha));
        college = groupDAO.create(college);

        // College Expense 1: Cloud Server & GPU (₹1,800 paid by Aarav)
        List<ExpenseSplit> collegeSplits = expenseService.calculateEqualSplits(new BigDecimal("1800.00"), List.of(aarav, diya, sneha));
        expenseService.createExpense(college.getId(), "AWS Cloud Hosting & GPU Instance", new BigDecimal("1800.00"),
                aarav.getId(), LocalDate.now().minusDays(7), ExpenseCategory.OTHER,
                "Model training compute and backend deployment", SplitMethod.EQUAL, collegeSplits);

        // 5. Add a pending settlement reminder for demonstration
        settlementService.createSettlement(goaTrip.getId(), diya.getId(), diya.getName(),
                aarav.getId(), aarav.getName(), new BigDecimal("1250.00"),
                LocalDate.now().plusDays(2), "Goa Trip Villa partial settlement");
    }

    /**
     * Resets and purges all application database tables safely.
     */
    public void resetAllData() {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM settlements;");
            stmt.execute("DELETE FROM expense_splits;");
            stmt.execute("DELETE FROM expenses;");
            stmt.execute("DELETE FROM group_members;");
            stmt.execute("DELETE FROM groups;");
            stmt.execute("DELETE FROM members;");
            stmt.execute("UPDATE SQLITE_SEQUENCE SET SEQ=0 WHERE NAME IN ('settlements', 'expense_splits', 'expenses', 'group_members', 'groups', 'members');");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset database: " + e.getMessage(), e);
        }
    }
}

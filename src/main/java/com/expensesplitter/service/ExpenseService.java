package com.expensesplitter.service;

import com.expensesplitter.dao.ExpenseDAO;
import com.expensesplitter.dao.MemberDAO;
import com.expensesplitter.model.*;
import com.expensesplitter.util.MoneyUtils;
import com.expensesplitter.util.ValidationUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating expense operations, validations, and split distributions.
 */
public class ExpenseService {

    private final ExpenseDAO expenseDAO;
    private final MemberDAO memberDAO;

    public ExpenseService() {
        this.expenseDAO = new ExpenseDAO();
        this.memberDAO = new MemberDAO();
    }

    public ExpenseService(ExpenseDAO expenseDAO, MemberDAO memberDAO) {
        this.expenseDAO = expenseDAO;
        this.memberDAO = memberDAO;
    }

    /**
     * Prepares splits for an Equal Split.
     */
    public List<ExpenseSplit> calculateEqualSplits(BigDecimal totalAmount, List<Member> participants) {
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("At least one participant must be selected.");
        }
        if (!ValidationUtils.isPositive(totalAmount)) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }

        List<BigDecimal> amounts = MoneyUtils.splitEqually(totalAmount, participants.size());
        List<ExpenseSplit> splits = new ArrayList<>();
        BigDecimal equalPct = MoneyUtils.ONE_HUNDRED.divide(BigDecimal.valueOf(participants.size()), MoneyUtils.SCALE, MoneyUtils.ROUNDING);

        for (int i = 0; i < participants.size(); i++) {
            Member m = participants.get(i);
            splits.add(new ExpenseSplit(m.getId(), m.getName(), amounts.get(i), equalPct));
        }
        return splits;
    }

    /**
     * Prepares splits for an Unequal / Custom Split.
     */
    public List<ExpenseSplit> calculateCustomSplits(BigDecimal totalAmount, Map<Member, BigDecimal> memberShares) {
        if (memberShares == null || memberShares.isEmpty()) {
            throw new IllegalArgumentException("Participant shares must be provided.");
        }
        if (!ValidationUtils.isPositive(totalAmount)) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }

        List<BigDecimal> shareList = new ArrayList<>(memberShares.values());
        if (!MoneyUtils.validateCustomShares(totalAmount, shareList)) {
            BigDecimal sum = shareList.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            throw new IllegalArgumentException(String.format(
                    "The sum of custom shares (%s) must exactly equal the total expense (%s).",
                    MoneyUtils.format(sum), MoneyUtils.format(totalAmount)
            ));
        }

        List<ExpenseSplit> splits = new ArrayList<>();
        for (Map.Entry<Member, BigDecimal> entry : memberShares.entrySet()) {
            Member m = entry.getKey();
            BigDecimal share = entry.getValue();
            BigDecimal pct = share.multiply(MoneyUtils.ONE_HUNDRED).divide(totalAmount, MoneyUtils.SCALE, MoneyUtils.ROUNDING);
            splits.add(new ExpenseSplit(m.getId(), m.getName(), share, pct));
        }
        return splits;
    }

    /**
     * Prepares splits for a Percentage Split.
     */
    public List<ExpenseSplit> calculatePercentageSplits(BigDecimal totalAmount, Map<Member, BigDecimal> memberPercentages) {
        if (memberPercentages == null || memberPercentages.isEmpty()) {
            throw new IllegalArgumentException("Participant percentages must be provided.");
        }
        if (!ValidationUtils.isPositive(totalAmount)) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }

        List<Member> members = new ArrayList<>(memberPercentages.keySet());
        List<BigDecimal> percentages = new ArrayList<>();
        for (Member m : members) {
            percentages.add(memberPercentages.get(m));
        }

        List<BigDecimal> calculatedShares = MoneyUtils.splitByPercentages(totalAmount, percentages);
        List<ExpenseSplit> splits = new ArrayList<>();

        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            splits.add(new ExpenseSplit(m.getId(), m.getName(), calculatedShares.get(i), percentages.get(i)));
        }
        return splits;
    }

    /**
     * Validates and creates an expense with all split records inside a database transaction.
     */
    public Expense createExpense(int groupId, String description, BigDecimal amount,
                                 int paidByMemberId, LocalDate expenseDate, ExpenseCategory category,
                                 String notes, SplitMethod splitMethod, List<ExpenseSplit> splits) {

        validateExpenseInput(groupId, description, amount, paidByMemberId, expenseDate, splits);

        Expense expense = new Expense();
        expense.setGroupId(groupId);
        expense.setDescription(description.trim());
        expense.setAmount(amount.setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING));
        expense.setPaidByMemberId(paidByMemberId);
        expense.setExpenseDate(expenseDate != null ? expenseDate : LocalDate.now());
        expense.setCategory(category != null ? category : ExpenseCategory.OTHER);
        expense.setNotes(notes != null ? notes.trim() : null);
        expense.setSplitMethod(splitMethod != null ? splitMethod : SplitMethod.EQUAL);
        expense.setSplits(splits);

        return expenseDAO.create(expense);
    }

    /**
     * Updates an existing expense and its splits.
     */
    public void updateExpense(int expenseId, int groupId, String description, BigDecimal amount,
                              int paidByMemberId, LocalDate expenseDate, ExpenseCategory category,
                              String notes, SplitMethod splitMethod, List<ExpenseSplit> splits) {

        validateExpenseInput(groupId, description, amount, paidByMemberId, expenseDate, splits);

        Expense expense = expenseDAO.findById(expenseId);
        if (expense == null) {
            throw new IllegalArgumentException("Expense not found with ID: " + expenseId);
        }

        expense.setGroupId(groupId);
        expense.setDescription(description.trim());
        expense.setAmount(amount.setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING));
        expense.setPaidByMemberId(paidByMemberId);
        expense.setExpenseDate(expenseDate != null ? expenseDate : LocalDate.now());
        expense.setCategory(category != null ? category : ExpenseCategory.OTHER);
        expense.setNotes(notes != null ? notes.trim() : null);
        expense.setSplitMethod(splitMethod != null ? splitMethod : SplitMethod.EQUAL);
        expense.setSplits(splits);

        expenseDAO.update(expense);
    }

    public void deleteExpense(int expenseId) {
        expenseDAO.delete(expenseId);
    }

    public Expense getExpenseById(int id) {
        return expenseDAO.findById(id);
    }

    public List<Expense> getExpensesByGroup(int groupId) {
        return expenseDAO.findByGroupId(groupId);
    }

    public List<Expense> getAllExpenses() {
        return expenseDAO.findAll();
    }

    public List<Expense> getRecentExpenses(int limit) {
        return expenseDAO.findRecent(limit);
    }

    public BigDecimal getTotalExpenses() {
        return expenseDAO.getTotalExpenseAmount();
    }

    public List<Expense> searchAndFilter(Integer groupId, ExpenseCategory category,
                                         String searchKeyword, LocalDate fromDate, LocalDate toDate) {
        return expenseDAO.searchAndFilter(groupId, category, searchKeyword, fromDate, toDate);
    }

    private void validateExpenseInput(int groupId, String description, BigDecimal amount,
                                      int paidByMemberId, LocalDate expenseDate, List<ExpenseSplit> splits) {
        if (!ValidationUtils.isNotBlank(description)) {
            throw new IllegalArgumentException("Description cannot be blank.");
        }
        if (!ValidationUtils.isPositive(amount)) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (groupId <= 0) {
            throw new IllegalArgumentException("A valid group must be selected.");
        }
        if (paidByMemberId <= 0) {
            throw new IllegalArgumentException("A payer must be selected.");
        }
        if (expenseDate == null) {
            throw new IllegalArgumentException("Expense date cannot be null.");
        }
        if (splits == null || splits.isEmpty()) {
            throw new IllegalArgumentException("At least one participant must be included in the expense split.");
        }

        // Verify total sum of splits equals amount
        BigDecimal splitSum = BigDecimal.ZERO;
        for (ExpenseSplit split : splits) {
            if (split.getShareAmount() == null || split.getShareAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Split share cannot be negative.");
            }
            splitSum = splitSum.add(split.getShareAmount());
        }

        if (splitSum.setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING)
                    .compareTo(amount.setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING)) != 0) {
            throw new IllegalArgumentException(String.format(
                    "The sum of participant shares (%s) does not match total expense amount (%s).",
                    MoneyUtils.format(splitSum), MoneyUtils.format(amount)
            ));
        }
    }
}

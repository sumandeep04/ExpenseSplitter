package com.expensesplitter.service;

import com.expensesplitter.dao.ExpenseDAO;
import com.expensesplitter.dao.MemberDAO;
import com.expensesplitter.dao.SettlementDAO;
import com.expensesplitter.model.*;
import com.expensesplitter.util.MoneyUtils;

import java.math.BigDecimal;
import java.util.*;

/**
 * Service for calculating balances per group and across the entire application.
 *
 * Core Formula:
 * Net Balance = Total Amount Paid - Total Expense Share + Settlements Paid Out - Settlements Received
 *
 * Interpretation:
 * - Positive (> 0): Member is a creditor (should receive money)
 * - Negative (< 0): Member is a debtor (owes money)
 * - Zero (== 0): Member is completely settled
 */
public class BalanceService {

    private final MemberDAO memberDAO;
    private final ExpenseDAO expenseDAO;
    private final SettlementDAO settlementDAO;

    public BalanceService() {
        this.memberDAO = new MemberDAO();
        this.expenseDAO = new ExpenseDAO();
        this.settlementDAO = new SettlementDAO();
    }

    public BalanceService(MemberDAO memberDAO, ExpenseDAO expenseDAO, SettlementDAO settlementDAO) {
        this.memberDAO = memberDAO;
        this.expenseDAO = expenseDAO;
        this.settlementDAO = settlementDAO;
    }

    /**
     * Calculates the balances for all members in a specific group.
     * Guaranteed strictly isolated per group.
     */
    public List<MemberBalance> calculateGroupBalances(int groupId) {
        List<Member> members = memberDAO.findByGroupId(groupId);
        List<Expense> expenses = expenseDAO.findByGroupId(groupId);
        List<Settlement> settlements = settlementDAO.findByGroupId(groupId);

        Map<Integer, BigDecimal> totalPaidMap = new HashMap<>();
        Map<Integer, BigDecimal> totalShareMap = new HashMap<>();
        Map<Integer, BigDecimal> settlementPaidOutMap = new HashMap<>();
        Map<Integer, BigDecimal> settlementReceivedMap = new HashMap<>();

        for (Member m : members) {
            totalPaidMap.put(m.getId(), BigDecimal.ZERO);
            totalShareMap.put(m.getId(), BigDecimal.ZERO);
            settlementPaidOutMap.put(m.getId(), BigDecimal.ZERO);
            settlementReceivedMap.put(m.getId(), BigDecimal.ZERO);
        }

        // Sum expenses and splits
        for (Expense expense : expenses) {
            int payerId = expense.getPaidByMemberId();
            BigDecimal currentPaid = totalPaidMap.getOrDefault(payerId, BigDecimal.ZERO);
            totalPaidMap.put(payerId, currentPaid.add(expense.getAmount()));

            for (ExpenseSplit split : expense.getSplits()) {
                int participantId = split.getMemberId();
                BigDecimal currentShare = totalShareMap.getOrDefault(participantId, BigDecimal.ZERO);
                totalShareMap.put(participantId, currentShare.add(split.getShareAmount()));
            }
        }

        // Account for completed or partial settlements
        for (Settlement s : settlements) {
            if (s.getStatus() == SettlementStatus.CANCELLED) {
                continue;
            }
            BigDecimal actualPaid = s.getAmountPaid() != null ? s.getAmountPaid() : BigDecimal.ZERO;
            if (actualPaid.compareTo(BigDecimal.ZERO) > 0) {
                int fromId = s.getFromMemberId();
                int toId = s.getToMemberId();

                BigDecimal fromCurrent = settlementPaidOutMap.getOrDefault(fromId, BigDecimal.ZERO);
                settlementPaidOutMap.put(fromId, fromCurrent.add(actualPaid));

                BigDecimal toCurrent = settlementReceivedMap.getOrDefault(toId, BigDecimal.ZERO);
                settlementReceivedMap.put(toId, toCurrent.add(actualPaid));
            }
        }

        List<MemberBalance> balances = new ArrayList<>();
        for (Member m : members) {
            BigDecimal paid = totalPaidMap.getOrDefault(m.getId(), BigDecimal.ZERO).setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING);
            BigDecimal share = totalShareMap.getOrDefault(m.getId(), BigDecimal.ZERO).setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING);
            BigDecimal sOut = settlementPaidOutMap.getOrDefault(m.getId(), BigDecimal.ZERO).setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING);
            BigDecimal sIn = settlementReceivedMap.getOrDefault(m.getId(), BigDecimal.ZERO).setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING);

            // Effective paid = actual paid for expenses + money paid to settle debt
            BigDecimal effectivePaid = paid.add(sOut);
            // Effective share = consumption share + settlement received from others
            BigDecimal effectiveShare = share.add(sIn);

            balances.add(new MemberBalance(m.getId(), m.getName(), effectivePaid, effectiveShare));
        }

        return balances;
    }

    /**
     * Calculates net balances map keyed by Member ID for a given group.
     */
    public Map<Integer, BigDecimal> getNetBalancesMap(int groupId) {
        List<MemberBalance> balances = calculateGroupBalances(groupId);
        Map<Integer, BigDecimal> map = new HashMap<>();
        for (MemberBalance mb : balances) {
            map.put(mb.getMemberId(), mb.getNetBalance());
        }
        return map;
    }

    /**
     * Calculates aggregate financial metrics across all groups for a given user member ID.
     */
    public Map<String, BigDecimal> calculateUserAggregateMetrics(int userMemberId, List<Group> allGroups) {
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal amountOwed = BigDecimal.ZERO;
        BigDecimal amountToReceive = BigDecimal.ZERO;

        for (Group g : allGroups) {
            List<MemberBalance> balances = calculateGroupBalances(g.getId());
            for (MemberBalance mb : balances) {
                if (mb.getMemberId() == userMemberId) {
                    totalPaid = totalPaid.add(mb.getTotalPaid());
                    if (mb.isDebtor()) {
                        amountOwed = amountOwed.add(mb.getNetBalance().abs());
                    } else if (mb.isCreditor()) {
                        amountToReceive = amountToReceive.add(mb.getNetBalance());
                    }
                }
            }
        }

        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("totalPaid", totalPaid.setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING));
        metrics.put("amountOwed", amountOwed.setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING));
        metrics.put("amountToReceive", amountToReceive.setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING));
        return metrics;
    }
}

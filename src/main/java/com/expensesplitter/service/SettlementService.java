package com.expensesplitter.service;

import com.expensesplitter.dao.SettlementDAO;
import com.expensesplitter.model.MemberBalance;
import com.expensesplitter.model.Settlement;
import com.expensesplitter.model.SettlementStatus;
import com.expensesplitter.model.SettlementSuggestion;
import com.expensesplitter.util.MoneyUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service for optimizing group debt transfers, recording settlements,
 * and managing payment reminders.
 */
public class SettlementService {

    private final BalanceService balanceService;
    private final SettlementDAO settlementDAO;

    public SettlementService() {
        this.balanceService = new BalanceService();
        this.settlementDAO = new SettlementDAO();
    }

    public SettlementService(BalanceService balanceService, SettlementDAO settlementDAO) {
        this.balanceService = balanceService;
        this.settlementDAO = settlementDAO;
    }

    private static class DebtorNode {
        int memberId;
        String name;
        BigDecimal debt;

        DebtorNode(int memberId, String name, BigDecimal debt) {
            this.memberId = memberId;
            this.name = name;
            this.debt = debt;
        }
    }

    private static class CreditorNode {
        int memberId;
        String name;
        BigDecimal credit;

        CreditorNode(int memberId, String name, BigDecimal credit) {
            this.memberId = memberId;
            this.name = name;
            this.credit = credit;
        }
    }

    /**
     * Calculates the minimal list of debt transfer suggestions to completely settle all members in a group.
     * Uses a deterministic greedy algorithm matching the largest debtor with the largest creditor.
     */
    public List<SettlementSuggestion> calculateOptimizedSettlements(int groupId) {
        List<MemberBalance> balances = balanceService.calculateGroupBalances(groupId);

        List<DebtorNode> debtors = new ArrayList<>();
        List<CreditorNode> creditors = new ArrayList<>();

        for (MemberBalance mb : balances) {
            if (mb.isDebtor()) {
                debtors.add(new DebtorNode(mb.getMemberId(), mb.getMemberName(), mb.getNetBalance().abs()));
            } else if (mb.isCreditor()) {
                creditors.add(new CreditorNode(mb.getMemberId(), mb.getMemberName(), mb.getNetBalance()));
            }
        }

        // Sort descending to greedily settle largest amounts first
        debtors.sort(Comparator.comparing((DebtorNode d) -> d.debt).reversed());
        creditors.sort(Comparator.comparing((CreditorNode c) -> c.credit).reversed());

        List<SettlementSuggestion> suggestions = new ArrayList<>();
        int dIndex = 0;
        int cIndex = 0;

        while (dIndex < debtors.size() && cIndex < creditors.size()) {
            DebtorNode debtor = debtors.get(dIndex);
            CreditorNode creditor = creditors.get(cIndex);

            BigDecimal transfer = debtor.debt.min(creditor.credit).setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING);

            if (transfer.compareTo(BigDecimal.ZERO) > 0) {
                suggestions.add(new SettlementSuggestion(
                        debtor.memberId, debtor.name,
                        creditor.memberId, creditor.name,
                        transfer
                ));
            }

            debtor.debt = debtor.debt.subtract(transfer);
            creditor.credit = creditor.credit.subtract(transfer);

            if (debtor.debt.compareTo(BigDecimal.ZERO) == 0) {
                dIndex++;
            }
            if (creditor.credit.compareTo(BigDecimal.ZERO) == 0) {
                cIndex++;
            }
        }

        return suggestions;
    }

    /**
     * Creates a new pending settlement record in SQLite from an optimized suggestion or manual plan.
     */
    public Settlement createSettlement(int groupId, int fromMemberId, String fromMemberName,
                                       int toMemberId, String toMemberName,
                                       BigDecimal amount, LocalDate dueDate, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Settlement amount must be greater than zero.");
        }
        if (fromMemberId == toMemberId) {
            throw new IllegalArgumentException("Payer and payee cannot be the same member.");
        }

        Settlement settlement = new Settlement();
        settlement.setGroupId(groupId);
        settlement.setFromMemberId(fromMemberId);
        settlement.setFromMemberName(fromMemberName);
        settlement.setToMemberId(toMemberId);
        settlement.setToMemberName(toMemberName);
        settlement.setAmount(amount.setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING));
        settlement.setAmountPaid(BigDecimal.ZERO);
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setDueDate(dueDate);
        settlement.setNotes(notes);

        return settlementDAO.create(settlement);
    }

    /**
     * Records a payment against an existing settlement.
     */
    public void recordPayment(int settlementId, BigDecimal paymentAmount, LocalDate settlementDate, String notes) {
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        settlementDAO.recordPayment(settlementId, paymentAmount, settlementDate, notes);
    }

    /**
     * Marks a settlement as completely settled in one step.
     */
    public void markFullySettled(int settlementId, LocalDate settlementDate, String notes) {
        Settlement s = settlementDAO.findById(settlementId);
        if (s == null) {
            throw new IllegalArgumentException("Settlement not found: " + settlementId);
        }
        BigDecimal remaining = s.getRemainingAmount();
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Settlement is already settled.");
        }
        settlementDAO.recordPayment(settlementId, remaining, settlementDate, notes != null ? notes : "Fully Settled");
    }

    /**
     * Cancels a pending settlement.
     */
    public void cancelSettlement(int settlementId) {
        settlementDAO.cancelSettlement(settlementId);
    }

    public List<Settlement> getSettlementsByGroup(int groupId) {
        return settlementDAO.findByGroupId(groupId);
    }

    public List<Settlement> getAllSettlements() {
        return settlementDAO.findAll();
    }

    public List<Settlement> getPendingSettlements() {
        return settlementDAO.findAllPending();
    }

    public List<Settlement> getOverdueSettlements() {
        List<Settlement> pending = settlementDAO.findAllPending();
        List<Settlement> overdue = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Settlement s : pending) {
            if (s.getDueDate() != null && s.getDueDate().isBefore(today)) {
                overdue.add(s);
            }
        }
        return overdue;
    }
}

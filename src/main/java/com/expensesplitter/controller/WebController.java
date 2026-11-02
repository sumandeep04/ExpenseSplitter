package com.expensesplitter.controller;

import com.expensesplitter.dao.ExpenseDAO;
import com.expensesplitter.dao.GroupDAO;
import com.expensesplitter.dao.MemberDAO;
import com.expensesplitter.dao.SettlementDAO;
import com.expensesplitter.model.*;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.ExpenseService;
import com.expensesplitter.service.SettlementService;
import com.expensesplitter.util.MoneyUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller
public class WebController {
    private final GroupDAO groupDAO;
    private final MemberDAO memberDAO;
    private final ExpenseDAO expenseDAO;
    private final SettlementDAO settlementDAO;
    private final ExpenseService expenseService;
    private final BalanceService balanceService;
    private final SettlementService settlementService;

    public WebController(GroupDAO groupDAO, MemberDAO memberDAO, ExpenseDAO expenseDAO,
                         SettlementDAO settlementDAO, ExpenseService expenseService,
                         BalanceService balanceService, SettlementService settlementService) {
        this.groupDAO = groupDAO;
        this.memberDAO = memberDAO;
        this.expenseDAO = expenseDAO;
        this.settlementDAO = settlementDAO;
        this.expenseService = expenseService;
        this.balanceService = balanceService;
        this.settlementService = settlementService;
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) Integer groupId, Model model) {
        List<Group> groups = groupDAO.findAll();
        Group selected = findSelectedGroup(groups, groupId);
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroup", selected);
        model.addAttribute("groupCount", groups.size());
        model.addAttribute("totalExpenses", expenseService.getTotalExpenses());
        model.addAttribute("balances", List.of());
        model.addAttribute("suggestions", List.of());
        model.addAttribute("groupExpenseTotal", BigDecimal.ZERO);
        model.addAttribute("recentExpenses", selected == null
                ? expenseService.getRecentExpenses(8) : expenseService.getExpensesByGroup(selected.getId()).stream().limit(8).toList());
        if (selected != null) {
            model.addAttribute("balances", balanceService.calculateGroupBalances(selected.getId()));
            model.addAttribute("suggestions", settlementService.calculateOptimizedSettlements(selected.getId()));
            model.addAttribute("groupExpenseTotal", expenseDAO.getTotalExpenseAmountByGroup(selected.getId()));
        }
        return "dashboard";
    }

    @GetMapping("/groups")
    public String groups(@RequestParam(required = false) Integer editId, Model model) {
        List<Group> groups = groupDAO.findAll();
        model.addAttribute("groups", groups);
        model.addAttribute("groupCategories", GroupCategory.values());
        model.addAttribute("editGroup", editId == null ? null : groupDAO.findById(editId));
        return "groups";
    }

    @PostMapping("/groups/save")
    public String saveGroup(@RequestParam(required = false) Integer groupId,
                            @RequestParam String name,
                            @RequestParam(required = false) String description,
                            @RequestParam String category,
                            RedirectAttributes redirect) {
        try {
            GroupCategory groupCategory = GroupCategory.valueOf(category);
            if (name.isBlank()) throw new IllegalArgumentException("Group name cannot be blank.");
            if (groupId == null) {
                groupDAO.create(new Group(name.trim(), description, groupCategory));
                redirect.addFlashAttribute("success", "Group created.");
            } else {
                Group group = requireGroup(groupId);
                group.setName(name.trim());
                group.setDescription(description);
                group.setCategory(groupCategory);
                groupDAO.update(group);
                redirect.addFlashAttribute("success", "Group updated.");
            }
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/groups";
        }
        return "redirect:/groups";
    }

    @PostMapping("/groups/{groupId}/delete")
    public String deleteGroup(@PathVariable int groupId, RedirectAttributes redirect) {
        groupDAO.delete(groupId);
        redirect.addFlashAttribute("success", "Group and its expenses were deleted.");
        return "redirect:/groups";
    }

    @GetMapping("/members")
    public String members(@RequestParam(required = false) Integer groupId,
                          @RequestParam(required = false) Integer editId, Model model) {
        List<Group> groups = groupDAO.findAll();
        Group selected = findSelectedGroup(groups, groupId);
        Member editMember = editId == null ? null : memberDAO.findById(editId);
        if (editMember != null && (selected == null || !memberDAO.isMemberInGroup(selected.getId(), editMember.getId()))) {
            editMember = null;
        }
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroup", selected);
        model.addAttribute("members", selected == null ? List.of() : memberDAO.findByGroupId(selected.getId()));
        model.addAttribute("editMember", editMember);
        return "members";
    }

    @PostMapping("/members/save")
    public String saveMember(@RequestParam int groupId,
                             @RequestParam(required = false) Integer memberId,
                             @RequestParam String name,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String phone,
                             RedirectAttributes redirect) {
        try {
            requireGroup(groupId);
            if (name.isBlank()) throw new IllegalArgumentException("Member name cannot be blank.");
            if (memberId == null) {
                Member member = memberDAO.create(new Member(name.trim(), emptyToNull(email), emptyToNull(phone)));
                memberDAO.addMemberToGroup(groupId, member.getId());
                redirect.addFlashAttribute("success", "Member added to the group.");
            } else {
                if (!memberDAO.isMemberInGroup(groupId, memberId)) throw new IllegalArgumentException("Member is not in this group.");
                Member member = memberDAO.findById(memberId);
                if (member == null) throw new IllegalArgumentException("Member not found.");
                member.setName(name.trim());
                member.setEmail(emptyToNull(email));
                member.setPhone(emptyToNull(phone));
                memberDAO.update(member);
                redirect.addFlashAttribute("success", "Member updated.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/members?groupId=" + groupId;
    }

    @PostMapping("/members/{memberId}/remove")
    public String removeMember(@PathVariable int memberId, @RequestParam int groupId, RedirectAttributes redirect) {
        try {
            memberDAO.removeMemberFromGroup(groupId, memberId);
            redirect.addFlashAttribute("success", "Member removed from the group.");
        } catch (IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/members?groupId=" + groupId;
    }

    @GetMapping("/expenses")
    public String expenses(@RequestParam(required = false) Integer groupId,
                           @RequestParam(required = false) Integer editId, Model model) {
        List<Group> groups = groupDAO.findAll();
        Group selected = findSelectedGroup(groups, groupId);
        Expense editExpense = editId == null ? null : expenseService.getExpenseById(editId);
        if (editExpense != null && (selected == null || editExpense.getGroupId() != selected.getId())) {
            editExpense = null;
        }
        List<Member> members = selected == null ? List.of() : memberDAO.findByGroupId(selected.getId());
        Set<Integer> editParticipantIds = new HashSet<>();
        Map<Integer, BigDecimal> editShares = new HashMap<>();
        if (editExpense != null) {
            for (ExpenseSplit split : editExpense.getSplits()) {
                editParticipantIds.add(split.getMemberId());
                editShares.put(split.getMemberId(), split.getShareAmount());
            }
        }
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroup", selected);
        model.addAttribute("members", members);
        model.addAttribute("expenses", selected == null ? List.of() : expenseService.getExpensesByGroup(selected.getId()));
        model.addAttribute("editExpense", editExpense);
        model.addAttribute("editParticipantIds", editParticipantIds);
        model.addAttribute("editShares", editShares);
        model.addAttribute("categories", ExpenseCategory.values());
        model.addAttribute("splitMethods", SplitMethod.values());
        return "expenses";
    }

    @PostMapping("/expenses/save")
    public String saveExpense(@RequestParam int groupId,
                              @RequestParam(required = false) Integer expenseId,
                              @RequestParam String description,
                              @RequestParam BigDecimal amount,
                              @RequestParam int paidByMemberId,
                              @RequestParam LocalDate expenseDate,
                              @RequestParam String category,
                              @RequestParam(required = false) String notes,
                              @RequestParam String splitMethod,
                              @RequestParam(name = "participantIds", required = false) List<Integer> participantIds,
                              HttpServletRequest request, RedirectAttributes redirect) {
        try {
            Group group = requireGroup(groupId);
            List<Member> members = memberDAO.findByGroupId(groupId);
            Map<Integer, Member> memberById = new HashMap<>();
            members.forEach(member -> memberById.put(member.getId(), member));
            if (!memberById.containsKey(paidByMemberId)) throw new IllegalArgumentException("Select a payer who belongs to this group.");
            if (participantIds == null || participantIds.isEmpty()) throw new IllegalArgumentException("Select at least one participant.");
            List<Member> participants = new ArrayList<>();
            for (Integer id : new LinkedHashSet<>(participantIds)) {
                Member member = memberById.get(id);
                if (member == null) throw new IllegalArgumentException("Every participant must belong to this group.");
                participants.add(member);
            }
            SplitMethod method = SplitMethod.valueOf(splitMethod);
            List<ExpenseSplit> splits;
            if (method == SplitMethod.EQUAL) {
                splits = expenseService.calculateEqualSplits(amount, participants);
            } else {
                Map<Member, BigDecimal> enteredShares = new LinkedHashMap<>();
                for (Member member : participants) {
                    String value = request.getParameter((method == SplitMethod.CUSTOM ? "share_" : "percentage_") + member.getId());
                    if (value == null || value.isBlank()) throw new IllegalArgumentException("Enter a value for each selected participant.");
                    BigDecimal parsed = new BigDecimal(value);
                    if (parsed.signum() < 0) throw new IllegalArgumentException("Split values cannot be negative.");
                    enteredShares.put(member, parsed);
                }
                splits = method == SplitMethod.CUSTOM
                        ? expenseService.calculateCustomSplits(amount, enteredShares)
                        : expenseService.calculatePercentageSplits(amount, enteredShares);
            }
            ExpenseCategory expenseCategory = ExpenseCategory.valueOf(category);
            if (expenseId == null) {
                expenseService.createExpense(group.getId(), description, amount, paidByMemberId, expenseDate,
                        expenseCategory, notes, method, splits);
                redirect.addFlashAttribute("success", "Expense recorded.");
            } else {
                Expense existing = expenseService.getExpenseById(expenseId);
                if (existing == null || existing.getGroupId() != groupId) throw new IllegalArgumentException("Expense not found in this group.");
                expenseService.updateExpense(expenseId, groupId, description, amount, paidByMemberId, expenseDate,
                        expenseCategory, notes, method, splits);
                redirect.addFlashAttribute("success", "Expense updated.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/expenses?groupId=" + groupId;
    }

    @PostMapping("/expenses/{expenseId}/delete")
    public String deleteExpense(@PathVariable int expenseId, @RequestParam int groupId, RedirectAttributes redirect) {
        Expense expense = expenseService.getExpenseById(expenseId);
        if (expense != null && expense.getGroupId() == groupId) {
            expenseService.deleteExpense(expenseId);
            redirect.addFlashAttribute("success", "Expense deleted.");
        }
        return "redirect:/expenses?groupId=" + groupId;
    }

    @GetMapping("/settlements")
    public String settlements(@RequestParam(required = false) Integer groupId, Model model) {
        List<Group> groups = groupDAO.findAll();
        Group selected = findSelectedGroup(groups, groupId);
        model.addAttribute("groups", groups);
        model.addAttribute("selectedGroup", selected);
        model.addAttribute("settlements", selected == null ? settlementService.getAllSettlements()
                : settlementService.getSettlementsByGroup(selected.getId()));
        model.addAttribute("suggestions", selected == null ? List.of()
                : settlementService.calculateOptimizedSettlements(selected.getId()));
        return "settlements";
    }

    @PostMapping("/settlements/create")
    public String createSettlement(@RequestParam int groupId, @RequestParam int fromMemberId,
                                   @RequestParam int toMemberId, @RequestParam BigDecimal amount,
                                   RedirectAttributes redirect) {
        try {
            requireGroup(groupId);
            if (!memberDAO.isMemberInGroup(groupId, fromMemberId) || !memberDAO.isMemberInGroup(groupId, toMemberId)) {
                throw new IllegalArgumentException("Both people must belong to this group.");
            }
            Member from = memberDAO.findById(fromMemberId);
            Member to = memberDAO.findById(toMemberId);
            settlementService.createSettlement(groupId, fromMemberId, from.getName(), toMemberId, to.getName(),
                    amount, null, "Created from settlement suggestion");
            redirect.addFlashAttribute("success", "Settlement saved. Mark it paid when the transfer is complete.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/settlements?groupId=" + groupId;
    }

    @PostMapping("/settlements/{settlementId}/pay")
    public String paySettlement(@PathVariable int settlementId, @RequestParam int groupId, RedirectAttributes redirect) {
        try {
            Settlement settlement = settlementDAO.findById(settlementId);
            if (settlement == null || settlement.getGroupId() != groupId) throw new IllegalArgumentException("Settlement not found in this group.");
            settlementService.markFullySettled(settlementId, LocalDate.now(), "Paid in full");
            redirect.addFlashAttribute("success", "Settlement marked as paid.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/settlements?groupId=" + groupId;
    }

    @ModelAttribute("currency")
    public String currency() {
        return MoneyUtils.getDefaultCurrencySymbol();
    }

    private Group requireGroup(int id) {
        Group group = groupDAO.findById(id);
        if (group == null) throw new IllegalArgumentException("Group not found.");
        return group;
    }

    private Group findSelectedGroup(List<Group> groups, Integer groupId) {
        if (groups.isEmpty()) return null;
        if (groupId != null) {
            return groups.stream().filter(group -> group.getId() == groupId).findFirst().orElse(groups.get(0));
        }
        return groups.get(0);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

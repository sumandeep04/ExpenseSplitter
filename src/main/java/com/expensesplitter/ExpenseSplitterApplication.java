package com.expensesplitter;

import com.expensesplitter.dao.ExpenseDAO;
import com.expensesplitter.dao.GroupDAO;
import com.expensesplitter.dao.MemberDAO;
import com.expensesplitter.dao.SettlementDAO;
import com.expensesplitter.database.DatabaseInitializer;
import com.expensesplitter.service.BalanceService;
import com.expensesplitter.service.ExpenseService;
import com.expensesplitter.service.SettlementService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ExpenseSplitterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseSplitterApplication.class, args);
    }

    @Bean
    CommandLineRunner initializeDatabase() {
        return args -> DatabaseInitializer.initialize();
    }

    @Bean
    GroupDAO groupDAO() {
        return new GroupDAO();
    }

    @Bean
    MemberDAO memberDAO() {
        return new MemberDAO();
    }

    @Bean
    ExpenseDAO expenseDAO() {
        return new ExpenseDAO();
    }

    @Bean
    SettlementDAO settlementDAO() {
        return new SettlementDAO();
    }

    @Bean
    ExpenseService expenseService(ExpenseDAO expenseDAO, MemberDAO memberDAO) {
        return new ExpenseService(expenseDAO, memberDAO);
    }

    @Bean
    BalanceService balanceService(MemberDAO memberDAO, ExpenseDAO expenseDAO, SettlementDAO settlementDAO) {
        return new BalanceService(memberDAO, expenseDAO, settlementDAO);
    }

    @Bean
    SettlementService settlementService(BalanceService balanceService, SettlementDAO settlementDAO) {
        return new SettlementService(balanceService, settlementDAO);
    }
}

# Expense Splitter

Expense Splitter is a local web application for organizing group expenses, calculating each person's share, and tracking how a group can settle up. It runs as a Java 21 Spring Boot application and uses server-rendered Thymeleaf pages with HTML, CSS, and JavaScript. Group, member, expense, split, and settlement records are stored in SQLite on the computer running the application.

The application is intended for local, personal use. It does not require a hosted service or a third-party account. Anyone with access to the running application and its local database can view and manage its data; there is currently no login or user/role system.

## Contents

- [Requirements](#requirements)
- [Run the application](#run-the-application)
- [Build and test](#build-and-test)
- [Features and workflows](#features-and-workflows)
- [How expense splitting works](#how-expense-splitting-works)
- [Balances and settlements](#balances-and-settlements)
- [Database and persistence](#database-and-persistence)
- [Application routes](#application-routes)
- [Architecture and project structure](#architecture-and-project-structure)
- [Configuration](#configuration)
- [Current scope](#current-scope)

## Requirements

- Java Development Kit (JDK) 21 or newer. Maven compiles the project with Java 21 language and bytecode compatibility.
- Apache Maven 3.6 or newer.
- Google Chrome or another modern browser.

Maven downloads project dependencies the first time it is run, so internet access may be needed for the initial build.

## Run the application

Open a terminal in the project directory and run:

```text
mvn spring-boot:run
```

When Spring Boot reports that it has started, open [http://localhost:8080](http://localhost:8080) in Chrome.

On Windows, `run.bat` runs the same Maven command. Keep the terminal window open while using the application. To stop the server, focus that terminal and press `Ctrl+C`.

If port 8080 is already in use, stop the other application using that port or change `server.port` in `src/main/resources/application.properties`, then open the application at the corresponding port.

## Build and test

Run the complete test suite:

```text
mvn test
```

Clean and test from a fresh build:

```text
mvn clean test
```

Create the executable Spring Boot JAR:

```text
mvn package
```

The packaged application is written to `target/expense-splitter-1.0.0.jar`. It can be started with:

```text
java -jar target/expense-splitter-1.0.0.jar
```

Tests cover equal/custom/percentage split calculations, rounding and money utilities, group balance and settlement calculations, transactional database behavior, and Spring MVC page/form workflows.

## Features and workflows

### Dashboard

The Overview page summarizes the number of groups, recorded spending, a selected group's balances, suggested transfers, and recent expenses. Select a group to see its members' current totals and suggested settlements. The dashboard links to group members, expenses, and settlement tracking.

### Groups

Create a group with a name, category, and optional description. Group categories are Trip & Vacation, Home & Flatmates, Food & Hangouts, College & Projects, and Other. Existing groups can be edited or deleted. Deleting a group removes its related expenses, splits, settlements, and membership links through database foreign-key cascades.

### Members

Choose a group to add a member with a name and optional email address and phone number. A member's details can be edited, or the member can be removed from that group. Removing someone from a group is blocked when the member is referenced by an expense or settlement in that group, to preserve financial history. A new member entered into a group is created as a member record for that group.

### Expenses

Choose a group and record an expense with a description, amount, date, category, payer, participants, optional notes, and split method. The payer and every selected participant must be members of that group. Expenses can be edited or deleted.

Expense categories are Food & Dining, Travel & Transport, Accommodation, Shopping, Bills & Utilities, Entertainment, and Other. The amount must be positive, at least one participant must be selected, and all shares must add up to the total.

### Settlements

The Settlements page shows optimized transfer suggestions for a selected group and a list of saved settlements. Save a suggested transfer to track it as pending. When payment is complete, use **Mark paid** to record the full payment. A completed settlement remains in the history.

## How expense splitting works

All monetary calculations use Java `BigDecimal` with two decimal places and `HALF_UP` rounding; floating-point arithmetic is not used for financial calculations.

- **Equal:** The expense amount is divided evenly among the selected participants. Any remaining cents are distributed deterministically so the shares sum exactly to the expense total.
- **Custom amounts:** Enter an amount for each selected participant. Values cannot be negative, and their total must match the expense amount.
- **Percentages:** Enter a percentage for each selected participant. Percentages must total exactly 100%. The app converts those percentages to amounts and reconciles any rounding difference against the largest percentage share so all shares total the expense amount.

The payer is selected separately from the participants: the payer may be included in the split or may have paid for the other selected participants.

## Balances and settlements

For a member in a group, the net position is calculated from expense payments and shares, including payments already recorded for settlements:

```text
Net balance = expenses paid - expense shares
              + settlement payments made - settlement payments received
```

- A positive balance means the member should receive money.
- A negative balance means the member owes money.
- A zero balance means the member is settled.

Settlement suggestions are generated by sorting debtors and creditors by balance, then matching the largest remaining debt to the largest remaining credit until they are covered. The suggestions are calculated from the current group balances. Saving a suggestion creates a pending settlement record; suggestions themselves do not move money or mark a settlement paid.

## Database and persistence

SQLite tables are created automatically when the application starts. The default database file is:

```text
<user-home>/.expensesplitter/expensesplitter.db
```

For example, on Windows this is typically:

```text
C:\Users\<your-user>\.expensesplitter\expensesplitter.db
```

The application creates the schema and default settings only. It does **not** automatically create demo groups, members, expenses, or settlements. Existing records are not cleared when the application starts. Back up the database file before making manual changes to it.

The schema contains:

| Table | Purpose |
| --- | --- |
| `groups` | Group name, description, category, and timestamps. |
| `members` | Member names and optional email and phone details. |
| `group_members` | Membership links between groups and members. |
| `expenses` | Expense details, payer, group, category, date, and split method. |
| `expense_splits` | Each participant's share amount and optional percentage for an expense. |
| `settlements` | Suggested/recorded transfers, paid amount, status, dates, and notes. |
| `settings` | Application settings, including the default currency-symbol setting. |

SQLite foreign-key checks are enabled on every database connection. Expense creation and editing write the expense and its split rows in a transaction so the changes succeed or roll back together.

## Application routes

The application uses Spring MVC form posts and server-rendered Thymeleaf pages; it is not a separate JSON REST API.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/` | Dashboard and optional `groupId` selection. |
| `GET` | `/groups` | List and create groups; `editId` loads a group into the edit form. |
| `POST` | `/groups/save` | Create or update a group. |
| `POST` | `/groups/{groupId}/delete` | Delete a group. |
| `GET` | `/members` | Choose a group and list its members; optional `editId` opens member editing. |
| `POST` | `/members/save` | Add or update a group member. |
| `POST` | `/members/{memberId}/remove?groupId=...` | Remove a member from a group when safe. |
| `GET` | `/expenses` | List expenses for a group; optional `editId` opens expense editing. |
| `POST` | `/expenses/save` | Create or update an expense and its participant shares. |
| `POST` | `/expenses/{expenseId}/delete?groupId=...` | Delete an expense in the selected group. |
| `GET` | `/settlements` | Show suggestions and tracked settlements, optionally for a group. |
| `POST` | `/settlements/create` | Save a transfer as a pending settlement. |
| `POST` | `/settlements/{settlementId}/pay?groupId=...` | Mark a pending settlement fully paid. |

## Architecture and project structure

The project uses a small MVC + service + DAO architecture:

```text
ExpenseSplitter/
├── pom.xml
├── run.bat
├── README.md
└── src/
    ├── main/
    │   ├── java/com/expensesplitter/
    │   │   ├── ExpenseSplitterApplication.java
    │   │   ├── controller/WebController.java
    │   │   ├── dao/
    │   │   ├── database/
    │   │   ├── model/
    │   │   ├── service/
    │   │   └── util/
    │   └── resources/
    │       ├── application.properties
    │       ├── templates/
    │       │   ├── fragments/layout.html
    │       │   ├── dashboard.html
    │       │   ├── groups.html
    │       │   ├── members.html
    │       │   ├── expenses.html
    │       │   └── settlements.html
    │       └── static/
    │           ├── css/app.css
    │           └── js/app.js
    └── test/java/com/expensesplitter/
```

- **`ExpenseSplitterApplication`** starts Spring Boot, registers the existing service/DAO beans, and initializes the SQLite schema.
- **`WebController`** maps browser requests to page models and handles submitted forms.
- **Thymeleaf templates** render the dashboard and management pages on the server.
- **Static CSS and JavaScript** style the responsive interface and switch the expense split inputs based on the chosen split method.
- **DAOs** perform SQLite operations using JDBC and prepared statements.
- **Services** contain expense split validation, balance calculations, and settlement suggestions.
- **Models** represent groups, members, expenses, splits, balances, and settlements.
- **Utility classes** handle monetary precision and validation.

## Configuration

The default runtime configuration is in `src/main/resources/application.properties`:

```properties
spring.application.name=expense-splitter
server.port=8080
spring.thymeleaf.cache=false
```

Change `server.port` to use another local port. Thymeleaf template caching is disabled to make local template changes visible without rebuilding the application.

## Current scope

- The application is local-first and has no authentication, authorization, multi-user accounts, cloud sync, or hosted database.
- Group/member/expense management and settlement payment actions are available in the browser. Settlements can be created and marked paid; settlement editing, cancellation, and deletion are not currently exposed as web-page actions.
- Currency display defaults to the rupee symbol. Although the SQLite settings table stores a currency-symbol setting, there is no currency settings page in the current web interface.
- `DemoDataService` remains in the source tree for legacy/demo support but is not called during application startup.

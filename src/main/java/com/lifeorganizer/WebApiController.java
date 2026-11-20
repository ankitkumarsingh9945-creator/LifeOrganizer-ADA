package com.lifeorganizer;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api")
public class WebApiController {
    private record Field(String name, String label, String type, boolean required, List<String> options) {}
    private record Module(String key, String title, String table, String idColumn, List<Field> fields) {}

    private static Field text(String name, String label, boolean required) {
        return new Field(name, label, "text", required, List.of());
    }

    private static Field select(String name, String label, boolean required, String... options) {
        return new Field(name, label, "select", required, List.of(options));
    }

    private static Field number(String name, String label, boolean required) {
        return new Field(name, label, "number", required, List.of());
    }

    private static Field integer(String name, String label, boolean required) {
        return new Field(name, label, "integer", required, List.of());
    }

    private static Field date(String name, String label, boolean required) {
        return new Field(name, label, "date", required, List.of());
    }

    private static Field time(String name, String label, boolean required) {
        return new Field(name, label, "time", required, List.of());
    }

    private static final Map<String, Module> MODULES = moduleDefinitions();
    private static final List<String> BACKUP_TABLES = List.of(
        "calendar_events", "reminder_actions", "expense_budgets", "expenses", "bills",
        "tasks", "groceries", "medicines", "documents", "audit_logs");
    private static final Map<String, List<String>> BACKUP_COLUMNS = backupColumns();

    private final JdbcTemplate jdbc;
    private final String searchCastType;
    private final boolean postgresqlDatabase;

    public WebApiController(JdbcTemplate jdbc,
                            @Value("${lifeorganizer.database-dialect:mysql}") String databaseDialect) {
        this.jdbc = jdbc;
        this.postgresqlDatabase = databaseDialect.equalsIgnoreCase("postgresql");
        this.searchCastType = postgresqlDatabase ? "VARCHAR" : "CHAR";
    }

    private static Map<String, Module> moduleDefinitions() {
        Map<String, Module> modules = new LinkedHashMap<>();
        add(modules, new Module("groceries", "Grocery", "groceries", "grocery_id", List.of(
            text("item_name", "Item name", true), text("category", "Category", true),
            text("quantity", "Quantity", true),
            select("purchased_status", "Status", true, "Pending", "Purchased"))));
        add(modules, new Module("bills", "Bills", "bills", "bill_id", List.of(
            text("bill_name", "Bill name", true), number("amount", "Amount", true),
            date("due_date", "Due date", true),
            select("payment_status", "Payment status", true, "Unpaid", "Paid"))));
        add(modules, new Module("expenses", "Expense Records", "expenses", "expense_id", List.of(
            text("category", "Category", true), number("amount", "Amount", true),
            date("expense_date", "Expense date", true), text("notes", "Notes", false))));
        add(modules, new Module("documents", "Documents", "documents", "document_id", List.of(
            text("document_name", "Document name", true), text("category", "Category", true),
            text("file_path", "File path", false), text("notes", "Notes", false))));
        add(modules, new Module("medicines", "Medicines", "medicines", "medicine_id", List.of(
            text("medicine_name", "Medicine name", true), text("dosage", "Dosage", false),
            time("reminder_time", "Reminder time", true), text("notes", "Notes", false))));
        add(modules, new Module("tasks", "Tasks", "tasks", "task_id", List.of(
            text("task_name", "Task name", true), date("deadline", "Deadline", true),
            select("status", "Status", true, "Pending", "In Progress", "Completed"),
            integer("priority", "Priority (1 is highest)", true))));
        add(modules, new Module("calendar", "Calendar", "calendar_events", "event_id", List.of(
            text("event_title", "Event title", true), date("event_date", "Event date", true),
            time("event_time", "Event time", false), text("description", "Description", false),
            integer("reminder_minutes", "Reminder minutes before", true))));
        return Map.copyOf(modules);
    }

    private static void add(Map<String, Module> modules, Module module) {
        modules.put(module.key(), module);
    }

    private static Map<String, List<String>> backupColumns() {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        columns.put("calendar_events", List.of("event_id", "event_title", "event_date", "event_time", "description", "reminder_minutes", "created_at"));
        columns.put("reminder_actions", List.of("action_id", "reminder_key", "action_type", "snooze_until", "updated_at"));
        columns.put("expense_budgets", List.of("budget_id", "category", "month_start", "budget_amount", "created_at"));
        columns.put("expenses", List.of("expense_id", "category", "amount", "expense_date", "notes", "created_at"));
        columns.put("bills", List.of("bill_id", "bill_name", "amount", "due_date", "payment_status", "created_at"));
        columns.put("tasks", List.of("task_id", "task_name", "deadline", "status", "priority", "created_at"));
        columns.put("groceries", List.of("grocery_id", "item_name", "category", "quantity", "purchased_status", "created_at"));
        columns.put("medicines", List.of("medicine_id", "medicine_name", "dosage", "reminder_time", "notes"));
        columns.put("documents", List.of("document_id", "document_name", "category", "file_path", "notes", "created_at"));
        columns.put("audit_logs", List.of("audit_id", "entity_name", "record_id", "action_name", "details", "created_at"));
        return Map.copyOf(columns);
    }

    private int userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        Integer id = jdbc.query("SELECT user_id FROM users WHERE email=?",
            rs -> rs.next() ? rs.getInt(1) : null, authentication.getName());
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        return id;
    }

    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap() {
        int id = userId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", jdbc.queryForMap("SELECT user_id AS id, name, email FROM users WHERE user_id=?", id));
        result.put("modules", MODULES.values().stream().map(m -> Map.of(
            "key", m.key(), "title", m.title(), "fields", m.fields())).toList());
        result.put("dashboard", dashboard(id));
        return result;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return dashboard(userId());
    }

    @PostMapping("/demo-data")
    @Transactional
    public Map<String, Object> addDemoData() {
        int owner = userId();
        String marker = "life-organizer-demo-v1";
        Integer seeded = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE user_id=? AND entity_name='demo-data' "
                + "AND action_name='SEED' AND details=?",
            Integer.class, owner, marker);
        if (seeded != null && seeded > 0) {
            return Map.of("message", "Demo data has already been added for this account.",
                "recordsAdded", 0, "alreadyAdded", true);
        }

        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.now();
        int recordsAdded = 0;
        jdbc.batchUpdate("INSERT INTO groceries(user_id,item_name,category,quantity,purchased_status) VALUES(?,?,?,?,?)",
            Arrays.asList(
                new Object[] { owner, "Demo · Bananas", "Demo Produce", "6 pcs", "Pending" },
                new Object[] { owner, "Demo · Spinach", "Demo Produce", "1 bunch", "Pending" },
                new Object[] { owner, "Demo · Milk", "Demo Dairy", "2 L", "Purchased" },
                new Object[] { owner, "Demo · Oats", "Demo Pantry", "1 pack", "Pending" },
                new Object[] { owner, "Demo · Rice", "Demo Pantry", "2 kg", "Purchased" },
                new Object[] { owner, "Demo · Dish soap", "Demo Home", "1 bottle", "Pending" }));
        recordsAdded += 6;

        jdbc.batchUpdate("INSERT INTO bills(user_id,bill_name,amount,due_date,payment_status) VALUES(?,?,?,?,?)",
            Arrays.asList(
                new Object[] { owner, "Demo · Electricity", new BigDecimal("1450.00"),
                    Date.valueOf(today.plusDays(3)), "Unpaid" },
                new Object[] { owner, "Demo · Internet", new BigDecimal("899.00"),
                    Date.valueOf(today.plusDays(10)), "Unpaid" },
                new Object[] { owner, "Demo · Phone", new BigDecimal("599.00"),
                    Date.valueOf(today.minusDays(5)), "Paid" }));
        recordsAdded += 3;

        jdbc.batchUpdate("INSERT INTO expenses(user_id,category,amount,expense_date,notes) VALUES(?,?,?,?,?)",
            Arrays.asList(
                new Object[] { owner, "Demo Food", new BigDecimal("680.00"),
                    Date.valueOf(today), "Fictional demo entry" },
                new Object[] { owner, "Demo Transport", new BigDecimal("240.00"),
                    Date.valueOf(today.minusDays(1)), "Fictional demo entry" },
                new Object[] { owner, "Demo Utilities", new BigDecimal("1250.00"),
                    Date.valueOf(today.minusDays(3)), "Fictional demo entry" },
                new Object[] { owner, "Demo Household", new BigDecimal("430.00"),
                    Date.valueOf(today.minusDays(6)), "Fictional demo entry" },
                new Object[] { owner, "Demo Food", new BigDecimal("520.00"),
                    Date.valueOf(today.minusDays(9)), "Fictional demo entry" }));
        recordsAdded += 5;

        jdbc.batchUpdate("INSERT INTO documents(user_id,document_name,category,file_path,notes) VALUES(?,?,?,?,?)",
            Arrays.asList(
                new Object[] { owner, "Demo · Home inventory", "Demo Reference", null, "Fictional sample; no file attached." },
                new Object[] { owner, "Demo · Appliance warranty", "Demo Warranty", null, "Fictional sample; no file attached." },
                new Object[] { owner, "Demo · Travel checklist", "Demo Travel", null, "Fictional sample; no file attached." }));
        recordsAdded += 3;

        jdbc.batchUpdate("INSERT INTO tasks(user_id,task_name,deadline,status,priority) VALUES(?,?,?,?,?)",
            Arrays.asList(
                new Object[] { owner, "Demo · Plan weekly meals", Date.valueOf(today.plusDays(1)), "In Progress", 2 },
                new Object[] { owner, "Demo · Review monthly budget", Date.valueOf(today.plusDays(3)), "Pending", 1 },
                new Object[] { owner, "Demo · Tidy workspace", Date.valueOf(today.plusDays(6)), "Pending", 3 },
                new Object[] { owner, "Demo · Sort old receipts", Date.valueOf(today.minusDays(1)), "Completed", 4 }));
        recordsAdded += 4;

        jdbc.batchUpdate("INSERT INTO calendar_events(user_id,event_title,event_date,event_time,description,reminder_minutes) "
                + "VALUES(?,?,?,?,?,?)",
            Arrays.asList(
                new Object[] { owner, "Demo · Grocery shopping", Date.valueOf(today.plusDays(2)),
                    Time.valueOf(LocalTime.of(18, 0)), "Fictional sample event.", 30 },
                new Object[] { owner, "Demo · Weekly planning", Date.valueOf(today.plusDays(5)),
                    Time.valueOf(LocalTime.of(10, 0)), "Fictional sample event.", 0 }));
        recordsAdded += 2;

        jdbc.batchUpdate("INSERT INTO expense_budgets(user_id,category,month_start,budget_amount) VALUES(?,?,?,?)",
            Arrays.asList(
                new Object[] { owner, "Demo Food", Date.valueOf(month.atDay(1)), new BigDecimal("12000.00") },
                new Object[] { owner, "Demo Transport", Date.valueOf(month.atDay(1)), new BigDecimal("5000.00") },
                new Object[] { owner, "Demo Utilities", Date.valueOf(month.atDay(1)), new BigDecimal("7000.00") },
                new Object[] { owner, "Demo Household", Date.valueOf(month.atDay(1)), new BigDecimal("3500.00") }));
        recordsAdded += 4;

        jdbc.update("INSERT INTO audit_logs(user_id,entity_name,record_id,action_name,details) VALUES(?,?,NULL,?,?)",
            owner, "demo-data", "SEED", marker);
        return Map.of("message", "Added " + recordsAdded
            + " clearly labeled demo records. Existing data was preserved; medicine reminders were left untouched.",
            "recordsAdded", recordsAdded, "alreadyAdded", false);
    }

    private Map<String, Object> dashboard(int id) {
        String sql = """
            SELECT
             (SELECT COUNT(*) FROM groceries WHERE user_id=?) groceryCount,
             (SELECT COUNT(*) FROM groceries WHERE user_id=? AND purchased_status='Purchased') purchasedCount,
             (SELECT COUNT(*) FROM bills WHERE user_id=? AND payment_status='Unpaid') unpaidBills,
             (SELECT COALESCE(SUM(amount),0) FROM expenses WHERE user_id=?) totalExpenses,
             (SELECT COUNT(*) FROM tasks WHERE user_id=? AND status<>'Completed') activeTasks,
             (SELECT COUNT(*) FROM tasks WHERE user_id=? AND priority<=2 AND status<>'Completed') highPriorityTasks,
             (SELECT COUNT(*) FROM tasks WHERE user_id=? AND status<>'Completed' AND deadline<CURRENT_DATE) overdueTasks,
             (SELECT COUNT(*) FROM calendar_events WHERE user_id=? AND event_date>=CURRENT_DATE) upcomingEvents
            """;
        return jdbc.queryForMap(sql, id, id, id, id, id, id, id, id);
    }

    @GetMapping("/modules")
    public List<Map<String, Object>> modules() {
        return MODULES.values().stream().map(m -> Map.<String, Object>of(
            "key", m.key(), "title", m.title(), "fields", m.fields())).toList();
    }

    @GetMapping("/records/{module}")
    public List<Map<String, Object>> records(@PathVariable("module") String module) {
        Module definition = module(module);
        String columns = definition.fields().stream().map(Field::name).reduce((a, b) -> a + "," + b).orElse("");
        return jdbc.queryForList("SELECT " + definition.idColumn() + " AS id," + columns
            + " FROM " + definition.table() + " WHERE user_id=? ORDER BY " + definition.idColumn() + " DESC",
            userId());
    }

    @PostMapping("/records/{module}")
    @Transactional
    public Map<String, Object> saveRecord(@PathVariable("module") String module,
                                          @RequestBody Map<String, Object> body) {
        Module definition = module(module);
        int owner = userId();
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : definition.fields()) {
            Object raw = body.get(field.name());
            String value = raw == null ? "" : raw.toString().trim();
            if (field.required() && value.isBlank()) {
                throw new IllegalArgumentException(field.label() + " is required.");
            }
            values.put(field.name(), parse(field, value));
        }
        Object recordId = body.get("id");
        if (recordId == null || recordId.toString().isBlank()) {
            List<String> columns = new ArrayList<>(List.of("user_id"));
            columns.addAll(definition.fields().stream().map(Field::name).toList());
            List<Object> args = new ArrayList<>(List.of(owner));
            definition.fields().forEach(f -> args.add(values.get(f.name())));
            String marks = String.join(",", java.util.Collections.nCopies(columns.size(), "?"));
            String sql = "INSERT INTO " + definition.table() + "(" + String.join(",", columns) + ") VALUES(" + marks + ")";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql, new String[] { definition.idColumn() });
                for (int i = 0; i < args.size(); i++) {
                    statement.setObject(i + 1, args.get(i));
                }
                return statement;
            }, keyHolder);
            Number id = keyHolder.getKey();
            if (id == null) {
                throw new IllegalStateException("The database did not return the new record ID.");
            }
            audit(owner, definition.table(), id.longValue(), "CREATE", "Created " + definition.title() + " record");
            return Map.of("id", id.longValue(), "message", "Record saved.");
        }
        long id = parseId(recordId);
        String set = definition.fields().stream().map(f -> f.name() + "=?").reduce((a, b) -> a + "," + b).orElseThrow();
        List<Object> args = new ArrayList<>();
        definition.fields().forEach(f -> args.add(values.get(f.name())));
        args.add(id);
        args.add(owner);
        int updated = jdbc.update("UPDATE " + definition.table() + " SET " + set + " WHERE "
            + definition.idColumn() + "=? AND user_id=?", args.toArray());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found.");
        }
        audit(owner, definition.table(), id, "UPDATE", "Updated " + definition.title() + " record");
        return Map.of("id", id, "message", "Record updated.");
    }

    @DeleteMapping("/records/{module}/{id}")
    @Transactional
    public Map<String, Object> deleteRecord(@PathVariable("module") String module,
                                            @PathVariable("id") long id) {
        Module definition = module(module);
        int owner = userId();
        int deleted = jdbc.update("DELETE FROM " + definition.table() + " WHERE "
            + definition.idColumn() + "=? AND user_id=?", id, owner);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found.");
        }
        audit(owner, definition.table(), id, "DELETE", "Deleted " + definition.title() + " record");
        return Map.of("message", "Record deleted.");
    }

    @GetMapping("/calendar")
    public Map<String, Object> calendar(@RequestParam(name = "month", required = false) String month) {
        YearMonth requested = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        List<Map<String, Object>> events = jdbc.queryForList(
            "SELECT event_id AS id,event_title,event_date,event_time,description,reminder_minutes "
            + "FROM calendar_events WHERE user_id=? AND event_date>=? AND event_date<? ORDER BY event_date,event_time",
            userId(), Date.valueOf(requested.atDay(1)), Date.valueOf(requested.plusMonths(1).atDay(1)));
        return Map.of("month", requested.toString(), "events", events);
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam("q") String q) {
        if (q.isBlank()) {
            return List.of();
        }
        int owner = userId();
        List<Map<String, Object>> results = new ArrayList<>();
        String like = "%" + q.trim() + "%";
        for (Module definition : MODULES.values()) {
            String predicate = definition.fields().stream()
                .map(f -> "CAST(" + f.name() + " AS " + searchCastType + ") LIKE ?")
                .reduce((a, b) -> a + " OR " + b).orElseThrow();
            List<Object> args = new ArrayList<>();
            args.add(owner);
            for (int i = 0; i < definition.fields().size(); i++) args.add(like);
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + definition.idColumn()
                + " AS id," + definition.fields().get(0).name() + " AS name FROM " + definition.table()
                + " WHERE user_id=? AND (" + predicate + ") ORDER BY " + definition.idColumn() + " DESC LIMIT 50",
                args.toArray());
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("module", definition.key());
                item.put("title", definition.title());
                item.put("id", row.get("id"));
                item.put("name", row.get("name"));
                results.add(item);
            }
        }
        return results;
    }

    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        int owner = userId();
        YearMonth month = YearMonth.now();
        List<Map<String, Object>> categories = jdbc.queryForList(
            "SELECT category,SUM(amount) AS total FROM expenses WHERE user_id=? AND expense_date>=? "
            + "AND expense_date<? GROUP BY category ORDER BY total DESC", owner,
            Date.valueOf(month.atDay(1)), Date.valueOf(month.plusMonths(1).atDay(1)));
        List<Map<String, Object>> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth value = month.minusMonths(i);
            BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE user_id=? AND expense_date>=? AND expense_date<?",
                BigDecimal.class, owner, Date.valueOf(value.atDay(1)),
                Date.valueOf(value.plusMonths(1).atDay(1)));
            months.add(Map.of("month", value.toString(), "total", total));
        }
        List<Map<String, Object>> budgets = jdbc.queryForList(
            "SELECT b.category,b.budget_amount AS budget,COALESCE((SELECT SUM(e.amount) FROM expenses e "
            + "WHERE e.user_id=b.user_id AND e.category=b.category AND e.expense_date>=b.month_start "
            + "AND e.expense_date<?),0) AS spent "
            + "FROM expense_budgets b WHERE b.user_id=? AND b.month_start=? ORDER BY b.category",
            Date.valueOf(month.plusMonths(1).atDay(1)), owner, Date.valueOf(month.atDay(1)));
        return Map.of("month", month.toString(), "categories", categories, "months", months, "budgets", budgets);
    }

    @PostMapping("/budgets")
    @Transactional
    public Map<String, Object> saveBudget(@RequestBody Map<String, Object> body) {
        String category = Objects.toString(body.get("category"), "").trim();
        if (category.isBlank()) throw new IllegalArgumentException("Category is required.");
        YearMonth month = YearMonth.parse(Objects.toString(body.get("month"), YearMonth.now().toString()));
        BigDecimal amount = new BigDecimal(Objects.toString(body.get("amount"), ""));
        if (amount.signum() < 0) throw new IllegalArgumentException("Budget must be zero or greater.");
        storeBudget(userId(), category, Date.valueOf(month.atDay(1)), amount);
        return Map.of("message", "Budget saved.");
    }

    private void storeBudget(int owner, String category, Date monthStart, BigDecimal amount) {
        String sql = "INSERT INTO expense_budgets(user_id,category,month_start,budget_amount) VALUES(?,?,?,?) "
            + (postgresqlDatabase
                ? "ON CONFLICT (user_id,category,month_start) DO UPDATE SET budget_amount=EXCLUDED.budget_amount"
                : "ON DUPLICATE KEY UPDATE budget_amount=VALUES(budget_amount)");
        jdbc.update(sql, owner, category, monthStart, amount);
    }

    @GetMapping("/reports")
    public Map<String, Object> reports() {
        int owner = userId();
        return Map.of(
            "expenses", jdbc.queryForList("SELECT category,COALESCE(SUM(amount),0) AS total FROM expenses "
                + "WHERE user_id=? GROUP BY category ORDER BY total DESC", owner),
            "bills", jdbc.queryForList("SELECT payment_status,COUNT(*) AS count,COALESCE(SUM(amount),0) AS total "
                + "FROM bills WHERE user_id=? GROUP BY payment_status", owner),
            "tasks", jdbc.queryForList("SELECT status,COUNT(*) AS count FROM tasks WHERE user_id=? GROUP BY status", owner),
            "activity", jdbc.queryForList("SELECT entity_name,record_id,action_name,details,created_at FROM audit_logs "
                + "WHERE user_id=? ORDER BY created_at DESC LIMIT 50", owner));
    }

    @GetMapping("/reminders")
    public Map<String, Object> reminders() {
        int owner = userId();
        List<Map<String, Object>> upcoming = new ArrayList<>();
        addReminders(upcoming, jdbc.queryForList("SELECT medicine_id,medicine_name,reminder_time FROM medicines "
            + "WHERE user_id=? ORDER BY reminder_time", owner), "medicine", "medicine_id", "medicine_name", "reminder_time");
        addReminders(upcoming, jdbc.queryForList("SELECT event_id,event_title,event_date,event_time FROM calendar_events "
            + "WHERE user_id=? AND event_date>=CURRENT_DATE ORDER BY event_date,event_time LIMIT 100",
            owner), "event", "event_id", "event_title", "event_date");
        addReminders(upcoming, jdbc.queryForList("SELECT task_id,task_name,deadline FROM tasks "
            + "WHERE user_id=? AND status<>'Completed' AND deadline>=CURRENT_DATE ORDER BY deadline LIMIT 100",
            owner), "task", "task_id", "task_name", "deadline");
        addReminders(upcoming, jdbc.queryForList("SELECT bill_id,bill_name,due_date FROM bills "
            + "WHERE user_id=? AND payment_status='Unpaid' AND due_date>=CURRENT_DATE ORDER BY due_date LIMIT 100",
            owner), "bill", "bill_id", "bill_name", "due_date");
        List<Map<String, Object>> history = jdbc.queryForList("SELECT reminder_key,action_type,snooze_until,updated_at "
            + "FROM reminder_actions WHERE user_id=? ORDER BY updated_at DESC LIMIT 50", owner);
        return Map.of("upcoming", upcoming, "history", history);
    }

    private void addReminders(List<Map<String, Object>> output, List<Map<String, Object>> rows,
                              String prefix, String idColumn, String labelColumn, String dateColumn) {
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", prefix + ":" + row.get(idColumn) + ":" + Objects.toString(
                row.get("event_date") != null ? row.get("event_date") : row.get("deadline") != null
                    ? row.get("deadline") : LocalDate.now()));
            item.put("module", prefix.equals("event") ? "calendar" : prefix.equals("medicine") ? "medicines" : prefix + "s");
            item.put("label", row.get(labelColumn));
            item.put("when", row.get(dateColumn));
            output.add(item);
        }
    }

    @GetMapping("/reminders/due")
    @Transactional
    public List<Map<String, Object>> dueReminders() {
        int owner = userId();
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        List<Map<String, Object>> due = new ArrayList<>();
        List<Map<String, Object>> medicines = jdbc.queryForList(
            "SELECT medicine_id,medicine_name,dosage FROM medicines WHERE user_id=? AND reminder_time=?",
            owner, Time.valueOf(now.toLocalTime()));
        for (Map<String, Object> row : medicines) {
            due.add(due("medicines", "medicine:" + row.get("medicine_id") + ":" + now.toLocalDate(),
                "Medicine: " + row.get("medicine_name") + " | Dosage: " + Objects.toString(row.get("dosage"), "Not specified")));
        }
        List<Map<String, Object>> events = jdbc.queryForList("SELECT event_id,event_title,event_date,event_time,reminder_minutes "
            + "FROM calendar_events WHERE user_id=? AND event_date>=CURRENT_DATE AND event_date<=? "
            + "AND event_time IS NOT NULL AND reminder_minutes>0", owner, Date.valueOf(LocalDate.now().plusDays(30)));
        for (Map<String, Object> row : events) {
            LocalDate date = ((Date) row.get("event_date")).toLocalDate();
            LocalTime time = ((Time) row.get("event_time")).toLocalTime();
            LocalDateTime trigger = LocalDateTime.of(date, time).minusMinutes(((Number) row.get("reminder_minutes")).longValue());
            if (trigger.equals(now)) {
                due.add(due("calendar", "event:" + row.get("event_id") + ":" + date, "Event: " + row.get("event_title")));
            }
        }
        if (now.getHour() == 9 && now.getMinute() == 0) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT task_id,task_name FROM tasks WHERE user_id=? "
                + "AND deadline=CURRENT_DATE AND status<>'Completed'", owner)) {
                due.add(due("tasks", "task:" + row.get("task_id") + ":" + now.toLocalDate(), "Task due today: " + row.get("task_name")));
            }
            for (Map<String, Object> row : jdbc.queryForList("SELECT bill_id,bill_name,amount FROM bills WHERE user_id=? "
                + "AND due_date=CURRENT_DATE AND payment_status='Unpaid'", owner)) {
                due.add(due("bills", "bill:" + row.get("bill_id") + ":" + now.toLocalDate(),
                    "Bill due today: " + row.get("bill_name") + " | Amount: ₹" + row.get("amount")));
            }
        }
        List<Map<String, Object>> notifications = new ArrayList<>(
            due.stream().filter(item -> !isSuppressed(owner, Objects.toString(item.get("key")))).toList());
        List<Map<String, Object>> expiredSnoozes = jdbc.queryForList(
            "SELECT reminder_key FROM reminder_actions WHERE user_id=? AND action_type='SNOOZE' AND snooze_until<=?",
            owner, java.sql.Timestamp.valueOf(now));
        for (Map<String, Object> row : expiredSnoozes) {
            String key = Objects.toString(row.get("reminder_key"));
            jdbc.update("DELETE FROM reminder_actions WHERE user_id=? AND reminder_key=?", owner, key);
            String module = key.startsWith("medicine:") ? "medicines" : key.startsWith("event:") ? "calendar"
                : key.startsWith("task:") ? "tasks" : "bills";
            notifications.add(due(module, key, "Snoozed reminder: " + key));
        }
        return notifications;
    }

    private Map<String, Object> due(String module, String key, String message) {
        return Map.of("module", module, "key", key, "message", message);
    }

    private boolean isSuppressed(int owner, String key) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT action_type,snooze_until FROM reminder_actions WHERE user_id=? AND reminder_key=?", owner, key);
        if (rows.isEmpty()) return false;
        Map<String, Object> action = rows.get(0);
        if ("COMPLETED".equals(action.get("action_type"))) return true;
        Object until = action.get("snooze_until");
        return until instanceof java.sql.Timestamp timestamp && timestamp.toLocalDateTime().isAfter(LocalDateTime.now());
    }

    @PostMapping("/reminders/action")
    @Transactional
    public Map<String, Object> reminderAction(@RequestBody Map<String, Object> body) {
        String key = Objects.toString(body.get("key"), "").trim();
        String action = Objects.toString(body.get("action"), "").trim();
        if (key.isBlank() || !Arrays.asList("SNOOZE", "COMPLETED").contains(action)) {
            throw new IllegalArgumentException("Choose a reminder and a valid action.");
        }
        LocalDateTime until = action.equals("SNOOZE") ? LocalDateTime.now().plusMinutes(10) : null;
        storeReminderAction(userId(), key, action, until == null ? null : java.sql.Timestamp.valueOf(until));
        return Map.of("message", action.equals("SNOOZE") ? "Reminder snoozed for 10 minutes." : "Reminder marked complete.");
    }

    private void storeReminderAction(int owner, String key, String action, java.sql.Timestamp until) {
        String sql = "INSERT INTO reminder_actions(user_id,reminder_key,action_type,snooze_until) VALUES(?,?,?,?) "
            + (postgresqlDatabase
                ? "ON CONFLICT (user_id,reminder_key) DO UPDATE SET action_type=EXCLUDED.action_type,"
                    + "snooze_until=EXCLUDED.snooze_until,updated_at=CURRENT_TIMESTAMP"
                : "ON DUPLICATE KEY UPDATE action_type=VALUES(action_type),snooze_until=VALUES(snooze_until),"
                    + "updated_at=CURRENT_TIMESTAMP");
        jdbc.update(sql, owner, key, action, until);
    }

    @DeleteMapping("/reminders/action")
    public Map<String, Object> resetReminder(@RequestParam("key") String key) {
        jdbc.update("DELETE FROM reminder_actions WHERE user_id=? AND reminder_key=?", userId(), key);
        return Map.of("message", "Reminder state reset.");
    }

    @GetMapping("/backup")
    public Map<String, Object> backup() {
        int owner = userId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("format", "life-organizer-backup-v1");
        for (String table : BACKUP_TABLES) {
            data.put(table, jdbc.queryForList("SELECT * FROM " + table + " WHERE user_id=?", owner));
        }
        return data;
    }

    @PostMapping("/backup/restore")
    @Transactional
    public Map<String, Object> restore(@RequestBody Map<String, Object> backup) {
        if (!"life-organizer-backup-v1".equals(backup.get("format"))) {
            throw new IllegalArgumentException("This backup file format is not supported.");
        }
        int owner = userId();
        int rowCount = 0;
        for (Object value : backup.values()) {
            if (value instanceof List<?> rows) rowCount += rows.size();
        }
        if (rowCount > 10000) throw new IllegalArgumentException("Backup exceeds the 10,000-record safety limit.");

        for (String table : List.of("reminder_actions", "calendar_events", "expense_budgets", "expenses",
            "bills", "tasks", "groceries", "medicines", "documents", "audit_logs")) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id=?", owner);
        }
        for (String table : List.of("calendar_events", "expense_budgets", "expenses", "bills", "tasks",
            "groceries", "medicines", "documents", "reminder_actions", "audit_logs")) {
            Object value = backup.get(table);
            if (!(value instanceof List<?> rows)) continue;
            List<String> columns = BACKUP_COLUMNS.get(table);
            for (Object rowValue : rows) {
                if (!(rowValue instanceof Map<?, ?> row)) {
                    throw new IllegalArgumentException("Invalid record in backup table " + table + ".");
                }
                List<String> insertColumns = new ArrayList<>(List.of("user_id"));
                List<Object> args = new ArrayList<>(List.of(owner));
                for (String column : columns) {
                    if (row.containsKey(column)) {
                        insertColumns.add(column);
                        args.add(row.get(column));
                    }
                }
                String marks = String.join(",", java.util.Collections.nCopies(insertColumns.size(), "?"));
                jdbc.update("INSERT INTO " + table + "(" + String.join(",", insertColumns) + ") VALUES(" + marks + ")",
                    args.toArray());
            }
        }
        return Map.of("message", "Restore completed.", "records", rowCount);
    }

    private Object parse(Field field, String value) {
        if (value.isBlank()) return null;
        try {
            return switch (field.type()) {
                case "number" -> new BigDecimal(value);
                case "integer" -> Integer.valueOf(value);
                case "date" -> Date.valueOf(LocalDate.parse(value));
                case "time" -> Time.valueOf(LocalTime.parse(value));
                case "select" -> {
                    if (!field.options().contains(value)) throw new IllegalArgumentException();
                    yield value;
                }
                default -> value;
            };
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid value for " + field.label() + ".");
        }
    }

    private Module module(String key) {
        Module module = MODULES.get(key);
        if (module == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown module.");
        return module;
    }

    private long parseId(Object value) {
        try {
            long id = Long.parseLong(value.toString());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid record ID.");
        }
    }

    private void audit(int owner, String table, long id, String action, String details) {
        jdbc.update("INSERT INTO audit_logs(user_id,entity_name,record_id,action_name,details) VALUES(?,?,?,?,?)",
            owner, table, id, action, details);
    }
}

@RestControllerAdvice
class WebApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> databaseError(DataAccessException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Database operation failed. Please try again later."));
    }
}

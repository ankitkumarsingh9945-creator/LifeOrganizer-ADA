# Life Organizer – ADA Project (Enhanced Edition)

A Java 25 + Spring Boot + MySQL browser application for personal management and Analysis & Design of Algorithms demonstrations. The original JavaFX desktop client remains available as a legacy option.

## Included features

1. **Calendar:** monthly calendar grid, month navigation, today shortcut, event creation and date-wise event details.
2. **Module notification preferences:** independent popup and sound checkboxes for Medicines, Calendar, Tasks, Bills, Expenses, Groceries and Documents.
3. **Reminders:** medicine time reminders, calendar event reminders, due-today task and unpaid-bill reminders (at 9:00 AM), 10-minute snooze, mark completed, and reminder action history/reset.
4. **Dashboard and global search:** account-specific summary cards and search across tasks, bills, expenses, groceries, medicines, documents and calendar events.
5. **Expenses, budgets and reports:** current-month category pie chart, six-month spending bar chart, category/month budgets, overspending status, CSV export and activity/audit reports.
6. **ADA Algorithm Lab:** linear/binary search, bubble/selection/insertion/merge/quick sort, priority queue, task priority scheduling, 0/1 knapsack DP, fractional knapsack greedy and activity selection greedy. Output includes result and time/space complexity.
7. **Backup and restore:** account-scoped ZIP backup and restore for application records. Login credentials/password hashes are not exported.

Also includes existing grocery, bill, expense record, document, medicine and task CRUD modules, authentication and audit logs.

## Requirements
- Windows 10/11
- JDK 25 (LTS)
- Maven 3.9+
- MySQL Server and MySQL Workbench
- VS Code (Java Extension Pack recommended)

## Important: database upgrade (run once)

The upgrade is additive. It creates any missing tables for users, groceries, bills, expenses, documents, medicines, tasks, calendar events, budgets, reminders, and activity history. It does not drop or overwrite existing tables or records. It does not alter the definitions of tables that already exist.

1. Open MySQL Workbench and connect to the same local MySQL instance used by your existing project.
2. Open `database/upgrade_all.sql`.
3. If needed, create and select the database with `CREATE DATABASE life_organizer CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; USE life_organizer;`.
4. Execute the additive script against the selected database. The web app also applies this idempotent script at startup.
5. Do not run the original `database/schema.sql` again just to upgrade; use this additive script.

Global Search reads from the module tables; upcoming Reminders are generated from medicines, calendar events, tasks, and bills, with snooze/completion history stored in `reminder_actions`.

## Run the web app

Open the folder containing `pom.xml` (the inner `LifeOrganizer_ADA_Complete` folder). In the VS Code PowerShell terminal, run the launcher below. It selects the installed JDK 25 and securely prompts for MySQL credentials if they are not already set in that terminal:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\scripts\run.ps1 -Web
```

If you prefer to set credentials manually, set `LIFEORG_DB_USER` and `LIFEORG_DB_PASSWORD` in the same terminal before running the script. Keep the password private and do not commit it.

When startup completes, open **http://localhost:8081** in your browser. The service binds to `127.0.0.1` and is intended for use on this computer only. If port 8081 is occupied, choose another port before launch, for example `$env:PORT = "8090"`, and open `http://localhost:8090`.

The web edition supports separate accounts with sign-in and per-account records. Passwords are stored as PBKDF2 hashes. Do not use the local database for a public deployment; create a separate hosted database and keep credentials in the hosting provider's secret variables.

The original desktop UI can still be started with `.\scripts\run.ps1`.

Keep the terminal open while the web app runs. Browser reminders are checked while the website is open. Allow browser notifications in **Notification Settings** if you want desktop popups; preferences are stored in that browser on this computer.

## Sample data
On **Overview**, select **Add demo data** to add clearly labeled fictional records for groceries, bills, expenses, documents, tasks, calendar events and budgets. Existing records are preserved, and demo data is added only once per account. No medicine reminders are generated.

## Reminders: behavior and limits
- Medicine reminders use each medicine's saved reminder time.
- Calendar reminders use event date/time and the configured number of minutes before the event.
- Tasks and unpaid bills due today are checked at 9:00 AM.
- Popup/sound preferences are stored in browser local storage on this computer.
- Snooze is 10 minutes. Completed reminder actions are stored for that reminder date.
- Keep the web app running and the browser page open for scheduled checks. This edition does not install a Windows background service or startup task.

## Backup and restore
Use **Backup & Restore → Download backup JSON** to save the selected account's records. Store the file somewhere safe. **Restore** replaces the current account's application records with the contents of the selected backup, so use it carefully. The backup does not contain account passwords.

## Deploy for free on Vercel
1. Create a Vercel Hobby project from this GitHub repository. The root `Dockerfile` selects Vercel's Container runtime.
2. Create a free TiDB Cloud Starter cluster, then connect it to the Vercel project with the TiDB Cloud Marketplace integration.
3. Confirm the integration provides `TIDB_HOST`, `TIDB_PORT`, `TIDB_USER`, `TIDB_PASSWORD` and `TIDB_DATABASE`.
4. Set `PORT=80`, `SERVER_ADDRESS=0.0.0.0` and `SESSION_COOKIE_SECURE=true` for the Vercel deployment.

The app applies `database/upgrade_all.sql` at startup and stores sign-in sessions in the shared TiDB database so they survive container scale-down. Use a new hosted database; local records are not uploaded. Vercel Hobby and TiDB Starter are free within their current usage quotas, and deployments may pause when a quota is reached. Public signup currently has no email verification or password recovery.

## Notes
- Keep a copy of your previous project folder before replacing source files.
- Existing database records are preserved by `upgrade_all.sql`.
- Currency labels use ₹ for the expense interface; stored values are plain decimal amounts.
- Charts and reports reflect records saved in the database, including sample records if you add them.
- ## 🚀 Live Deployment

**Live Website:** https://lifeorganizer-adaproject.vercel.app

**GitHub Repository:** https://github.com/ankitkumarsingh9945-creator/LifeOrganizer-ADA

LifeOrganizer is deployed and accessible online.

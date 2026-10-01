package ph.notifly.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

val ledgerMigration = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.prepare("""CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, type TEXT NOT NULL, cardType TEXT, lastFour TEXT, freeTransfer INTEGER NOT NULL, balanceMinor INTEGER NOT NULL, balanceAsOfMillis INTEGER NOT NULL, dueDate INTEGER, statementDate INTEGER, archived INTEGER NOT NULL, legacyPackage TEXT, preferencesImported INTEGER NOT NULL)""").use { it.step() }
        connection.prepare("""CREATE UNIQUE INDEX index_accounts_nameKey ON accounts(nameKey)""").use { it.step() }
        connection.prepare("""CREATE TABLE IF NOT EXISTS account_apps (accountId INTEGER NOT NULL, packageName TEXT NOT NULL, PRIMARY KEY(accountId, packageName), FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE)""").use { it.step() }
        connection.prepare("""CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, type TEXT NOT NULL, archived INTEGER NOT NULL, budgetMinor INTEGER, preferencesImported INTEGER NOT NULL)""").use { it.step() }
        connection.prepare("""CREATE UNIQUE INDEX index_categories_type_nameKey ON categories(type,nameKey)""").use { it.step() }
        connection.prepare("""CREATE TABLE IF NOT EXISTS captured_drafts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, captureId INTEGER, title TEXT NOT NULL, amountMinor INTEGER NOT NULL, type TEXT NOT NULL, inbound INTEGER, occurredAtMillis INTEGER NOT NULL, sourceApp TEXT NOT NULL, accountId INTEGER, toAccountId INTEGER, accountHint TEXT)""").use { it.step() }
        connection.prepare("""INSERT INTO accounts(name,nameKey,type,freeTransfer,balanceMinor,balanceAsOfMillis,archived,preferencesImported) VALUES ('Legacy','legacy','WALLET',0,0,-9223372036854775808,0,1), ('Legacy transfer destination','legacy transfer destination','WALLET',0,0,-9223372036854775808,0,1)""").use { it.step() }
        connection.prepare("""INSERT INTO accounts(name,nameKey,type,freeTransfer,balanceMinor,balanceAsOfMillis,archived,legacyPackage,preferencesImported) SELECT COALESCE(a.label,p.pkg)||' ('||p.pkg||')', lower(COALESCE(a.label,p.pkg)||' ('||p.pkg||')'), CASE WHEN a.kind = 'Bank' THEN 'BANK' ELSE 'WALLET' END,0,0,-9223372036854775808,0,p.pkg,0 FROM (SELECT packageName AS pkg FROM allowed_apps WHERE finance = 1 UNION SELECT sourceApp FROM transactions WHERE sourceApp IS NOT NULL UNION SELECT fromApp FROM transactions WHERE fromApp IS NOT NULL UNION SELECT toApp FROM transactions WHERE toApp IS NOT NULL) p LEFT JOIN allowed_apps a ON a.packageName = p.pkg""").use { it.step() }
        connection.prepare("""INSERT INTO account_apps SELECT id,legacyPackage FROM accounts WHERE legacyPackage IS NOT NULL""").use { it.step() }
        connection.prepare("""INSERT OR IGNORE INTO categories(name,nameKey,type,archived,preferencesImported) SELECT category,lower(category),type,0,0 FROM transactions WHERE type IN ('INCOME','EXPENSE')""").use { it.step() }
        connection.prepare("""CREATE TABLE transactions_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, type TEXT NOT NULL, status TEXT NOT NULL, category TEXT NOT NULL, occurredAtMillis INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, sourceApp TEXT, captureId INTEGER, note TEXT NOT NULL, accountId INTEGER NOT NULL DEFAULT 0, toAccountId INTEGER, categoryId INTEGER, fromApp TEXT, toApp TEXT, FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(toAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""").use { it.step() }
        connection.prepare("""INSERT INTO transactions_new SELECT t.id,t.title,t.amountMinor,t.currency,t.type,t.status,t.category,t.occurredAtMillis,t.createdAtMillis,t.sourceApp,t.captureId,t.note,COALESCE((SELECT id FROM accounts WHERE legacyPackage = CASE WHEN t.type = 'TRANSFER' THEN t.fromApp ELSE t.sourceApp END),1),CASE WHEN t.type = 'TRANSFER' THEN CASE WHEN t.toApp = t.fromApp THEN 2 ELSE COALESCE((SELECT id FROM accounts WHERE legacyPackage = t.toApp),2) END ELSE NULL END,(SELECT id FROM categories WHERE nameKey = lower(t.category) AND type = t.type),t.fromApp,t.toApp FROM transactions t""").use { it.step() }
        connection.prepare("""DROP TABLE transactions""").use { it.step() }
        connection.prepare("""ALTER TABLE transactions_new RENAME TO transactions""").use { it.step() }
        connection.prepare("""CREATE INDEX index_transactions_status_occurredAtMillis_createdAtMillis ON transactions(status,occurredAtMillis,createdAtMillis)""").use { it.step() }
        connection.prepare("""CREATE INDEX index_transactions_accountId ON transactions(accountId)""").use { it.step() }
        connection.prepare("""CREATE INDEX index_transactions_toAccountId ON transactions(toAccountId)""").use { it.step() }
        connection.prepare("""CREATE INDEX index_transactions_categoryId ON transactions(categoryId)""").use { it.step() }
    }
}

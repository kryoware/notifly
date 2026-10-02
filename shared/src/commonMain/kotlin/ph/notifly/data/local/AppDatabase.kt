package ph.notifly.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [TransactionEntity::class, RawCaptureEntity::class, AllowedAppEntity::class, PendingChangeEntity::class, CaptureReceiptEntity::class, AccountEntity::class, AccountAppEntity::class, CategoryEntity::class, CapturedDraftEntity::class, BillEntity::class, BillPaymentEntity::class],
    version = 11,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun rawCaptureDao(): RawCaptureDao
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun billDao(): BillDao
}

// The Room compiler generates the `actual` implementation for each platform target.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

val databaseMigrations = arrayOf(
    object : androidx.room.migration.Migration(10, 11) {
        override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("CREATE TABLE IF NOT EXISTS bills (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, amountMinor INTEGER NOT NULL, category TEXT NOT NULL, accountId INTEGER, startsOnDay INTEGER NOT NULL, repeats TEXT NOT NULL, settled INTEGER NOT NULL, status TEXT NOT NULL, detected INTEGER NOT NULL, sourceApp TEXT, captureId INTEGER, remindedForDay INTEGER, createdAtMillis INTEGER NOT NULL)").use { it.step() }
            connection.prepare("CREATE TABLE IF NOT EXISTS bill_payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, billId INTEGER NOT NULL, dueOnDay INTEGER NOT NULL, transactionId INTEGER, settledAtMillis INTEGER NOT NULL, FOREIGN KEY(billId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE CASCADE)").use { it.step() }
            connection.prepare("CREATE INDEX IF NOT EXISTS index_bill_payments_billId ON bill_payments(billId)").use { it.step() }
        }
    },
    object : androidx.room.migration.Migration(9, 10) {
        override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE raw_captures ADD COLUMN extras TEXT").use { it.step() }
        }
    },
    ledgerMigration,
    object : androidx.room.migration.Migration(7, 8) {
        override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE transactions ADD COLUMN fromApp TEXT").use { it.step() }
            connection.prepare("ALTER TABLE transactions ADD COLUMN toApp TEXT").use { it.step() }
        }
    },
    object : androidx.room.migration.Migration(6, 7) {
        override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE allowed_apps ADD COLUMN finance INTEGER NOT NULL DEFAULT 0").use { it.step() }
        }
    },
    object : androidx.room.migration.Migration(5, 6) {
        override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("CREATE INDEX IF NOT EXISTS index_transactions_status_occurredAtMillis_createdAtMillis ON transactions(status, occurredAtMillis, createdAtMillis)").use { it.step() }
        }
    },
    object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE transactions ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0").use { it.step() }
            connection.prepare("UPDATE transactions SET createdAtMillis = occurredAtMillis WHERE createdAtMillis = 0").use { it.step() }
        }
    },
        object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.prepare("CREATE TABLE IF NOT EXISTS capture_receipts (fingerprint TEXT NOT NULL PRIMARY KEY)").use { it.step() }
                connection.prepare("INSERT OR IGNORE INTO capture_receipts SELECT fingerprint FROM raw_captures WHERE fingerprint IS NOT NULL").use { it.step() }
            }
        },
        object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.prepare("ALTER TABLE raw_captures ADD COLUMN fingerprint TEXT").use { it.step() }
                connection.prepare("CREATE UNIQUE INDEX index_raw_captures_fingerprint ON raw_captures(fingerprint)").use { it.step() }
            }
        },
        object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.prepare("CREATE TABLE IF NOT EXISTS pending_changes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, transactionId INTEGER NOT NULL, operation TEXT NOT NULL)").use { it.step() }
                connection.prepare("CREATE UNIQUE INDEX index_pending_changes_transactionId ON pending_changes(transactionId)").use { it.step() }
                connection.prepare("INSERT INTO pending_changes(transactionId, operation) SELECT id, 'UPSERT' FROM transactions WHERE status = 'CONFIRMED' AND currency = 'PHP'").use { it.step() }
            }
        },
)

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .addMigrations(*databaseMigrations)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

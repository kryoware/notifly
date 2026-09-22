package ph.notifly.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [TransactionEntity::class, RawCaptureEntity::class, AllowedAppEntity::class, PendingChangeEntity::class, CaptureReceiptEntity::class],
    version = 5,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun rawCaptureDao(): RawCaptureDao
    abstract fun allowedAppDao(): AllowedAppDao
}

// The Room compiler generates the `actual` implementation for each platform target.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

val databaseMigrations = arrayOf(
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

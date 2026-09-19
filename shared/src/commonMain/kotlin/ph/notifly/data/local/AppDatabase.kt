package ph.notifly.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [TransactionEntity::class, RawCaptureEntity::class, AllowedAppEntity::class, PendingChangeEntity::class],
    version = 3,
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

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .addMigrations(object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.prepare("ALTER TABLE raw_captures ADD COLUMN fingerprint TEXT").use { it.step() }
                connection.prepare("CREATE UNIQUE INDEX index_raw_captures_fingerprint ON raw_captures(fingerprint)").use { it.step() }
            }
        })
        .addMigrations(object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.prepare("CREATE TABLE IF NOT EXISTS pending_changes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, transactionId INTEGER NOT NULL, operation TEXT NOT NULL)").use { it.step() }
                connection.prepare("CREATE UNIQUE INDEX index_pending_changes_transactionId ON pending_changes(transactionId)").use { it.step() }
                connection.prepare("INSERT INTO pending_changes(transactionId, operation) SELECT id, 'UPSERT' FROM transactions WHERE status = 'CONFIRMED'").use { it.step() }
            }
        })
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

package ph.notifly.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @Test fun upgradeFromOriginalDatabasePreservesLedgerAndSeedsOnlyConfirmedPhpQueue() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${System.nanoTime()}.db"
        val schema = JSONObject(java.io.File("schemas/ph.notifly.data.local.AppDatabase/1.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            sqlite.execSQL("INSERT INTO transactions VALUES (1, 'Confirmed', 100, 'PHP', 'INCOME', 'CONFIRMED', 'Other', 0, NULL, NULL, '')")
            sqlite.execSQL("INSERT INTO transactions VALUES (2, 'Review', 999, 'PHP', 'EXPENSE', 'NEEDS_REVIEW', 'Other', 0, NULL, NULL, '')")
            sqlite.execSQL("INSERT INTO transactions VALUES (3, 'Legacy dollar', 500, 'USD', 'INCOME', 'CONFIRMED', 'Other', 0, NULL, NULL, '')")
            sqlite.version = 1
        }
        val db = Room.databaseBuilder<AppDatabase>(context, name).setDriver(AndroidSQLiteDriver())
            .addMigrations(*databaseMigrations).build()
        try {
            assertEquals(3, db.transactionDao().observeAll().first().size)
            assertEquals(100L, db.transactionDao().observeConfirmedNetMinor().first())
            assertEquals(1L, db.transactionDao().pendingChanges().single().transactionId)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun upgradeFromVersion6KeepsAllowedAppsAndDefaultsFinanceOff() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-6-${System.nanoTime()}.db"
        val schema = JSONObject(java.io.File("schemas/ph.notifly.data.local.AppDatabase/6.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            sqlite.execSQL("INSERT INTO allowed_apps VALUES ('com.maya', 'Maya', 'Wallet', 1, 3)")
            sqlite.version = 6
        }
        val db = Room.databaseBuilder<AppDatabase>(context, name).setDriver(AndroidSQLiteDriver())
            .addMigrations(*databaseMigrations).build()
        try {
            val app = db.allowedAppDao().observeAll().first().single()
            assertEquals(3, app.capturedCount)
            assertEquals(false, app.finance)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

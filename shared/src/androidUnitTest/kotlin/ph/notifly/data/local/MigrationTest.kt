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
    @Test fun upgradeFromOriginalDatabasePreservesLedgerAndSeedsOnlyConfirmedQueue() = runTest {
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
            sqlite.version = 1
        }
        val db = Room.databaseBuilder<AppDatabase>(context, name).setDriver(AndroidSQLiteDriver())
            .addMigrations(*databaseMigrations).build()
        try {
            assertEquals(2, db.transactionDao().observeAll().first().size)
            assertEquals(100L, db.transactionDao().observeConfirmedNetMinor().first())
            assertEquals(1L, db.transactionDao().pendingChanges().single().transactionId)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

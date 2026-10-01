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
    @Test fun upgradeFromVersion8PreservesAppLegsLegacyRowsAndAccountBaselines() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-8-${System.nanoTime()}.db"
        val schema = JSONObject(java.io.File("schemas/ph.notifly.data.local.AppDatabase/8.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            sqlite.execSQL("INSERT INTO allowed_apps VALUES ('bank','My bank','Bank',1,3,1),('wallet','Wallet','Wallet',1,4,1)")
            sqlite.execSQL("INSERT INTO transactions(id,title,amountMinor,currency,type,status,category,occurredAtMillis,createdAtMillis,sourceApp,captureId,note,fromApp,toApp) VALUES (10,'Transfer',500,'PHP','TRANSFER','CONFIRMED','Transfer',300,400,'bank',NULL,'','bank','wallet'), (11,'Manual',200,'PHP','EXPENSE','NEEDS_REVIEW','Coffee',100,100,NULL,NULL,'',NULL,NULL), (12,'Incomplete transfer',100,'PHP','TRANSFER','NEEDS_REVIEW','Transfer',500,500,'bank',NULL,'','bank',NULL)")
            sqlite.execSQL("INSERT INTO pending_changes(transactionId,operation) VALUES (10,'UPSERT')")
            sqlite.version = 8
        }
        val db = Room.databaseBuilder<AppDatabase>(context, name).setDriver(AndroidSQLiteDriver())
            .addMigrations(*databaseMigrations).build()
        try {
            val rows = db.transactionDao().observeAll().first()
            assertEquals(setOf(10L,11L,12L), rows.map { it.id }.toSet())
            val accounts = db.ledgerDao().accounts()
            val bank = accounts.single { it.legacyPackage == "bank" }
            val wallet = accounts.single { it.legacyPackage == "wallet" }
            val transfer = rows.single { it.id == 10L }
            assertEquals(bank.id, transfer.accountId)
            assertEquals(wallet.id, transfer.toAccountId)
            assertEquals("CONFIRMED", transfer.status)
            assertEquals(400L, transfer.createdAtMillis)
            assertEquals("Legacy", accounts.single { it.id == rows.single { it.id == 11L }.accountId }.name)
            assertEquals("Legacy transfer destination", accounts.single { it.id == rows.single { it.id == 12L }.toAccountId }.name)
            assertEquals("Coffee", db.ledgerDao().categoryById(rows.single { it.id == 11L }.categoryId!!)!!.name)
            val balance = ManualBalance(9000, kotlin.time.Instant.fromEpochMilliseconds(200))
            db.ledgerDao().importPreferences(mapOf("bank" to balance), mapOf("Coffee" to 1000L))
            assertEquals(9000L, db.ledgerDao().accountById(bank.id)!!.balanceMinor)
            assertEquals(200L, db.ledgerDao().accountById(bank.id)!!.balanceAsOfMillis)
            assertEquals(10L, db.transactionDao().pendingChanges().single().transactionId)
            db.ledgerDao().importPreferences(mapOf("bank" to balance), mapOf("Coffee" to 1000L))
            assertEquals(accounts.size, db.ledgerDao().accounts().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

}

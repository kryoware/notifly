package ph.notifly.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.applicationContext.getDatabasePath("notifly.db")
    return Room.databaseBuilder<AppDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath,
    )
}

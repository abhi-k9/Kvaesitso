package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_35_36 : Migration(35, 36) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE Searchable ADD COLUMN lastLaunchedAt INTEGER NOT NULL DEFAULT 0")
    }
}

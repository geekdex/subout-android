package io.github.geekdex.subout.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.geekdex.subout.data.db.dao.NodeDao
import io.github.geekdex.subout.data.db.dao.NodeGroupDao
import io.github.geekdex.subout.data.db.dao.SubscriptionDao
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.db.entities.NodeGroup
import io.github.geekdex.subout.data.db.entities.NodeGroupType
import io.github.geekdex.subout.data.db.entities.Subscription

@Database(
    entities = [Subscription::class, Node::class, NodeGroup::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun nodeDao(): NodeDao
    abstract fun nodeGroupDao(): NodeGroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `node_groups` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `testUrl` TEXT NOT NULL,
                        `testUrlName` TEXT NOT NULL,
                        `nodeTagsJson` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加新字段到 node_groups 表
                db.execSQL(
                    """
                    ALTER TABLE `node_groups` 
                    ADD COLUMN `type` TEXT NOT NULL DEFAULT '${NodeGroupType.MANUAL.name}'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE `node_groups` 
                    ADD COLUMN `dynamicRuleJson` TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "subout_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

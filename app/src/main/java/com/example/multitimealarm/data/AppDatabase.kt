package com.example.multitimealarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun taskTypeToString(type: TaskType): String = type.name

    @TypeConverter
    fun stringToTaskType(value: String): TaskType = TaskType.valueOf(value)
}

@Database(
    entities = [AlarmTaskEntity::class, AlarmTimeEntity::class],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2：新增星期/每月/间隔/贪睡配置字段 */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_tasks ADD COLUMN weekdaysMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarm_tasks ADD COLUMN monthDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarm_tasks ADD COLUMN intervalMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarm_tasks ADD COLUMN snoozeMinutes INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE alarm_tasks ADD COLUMN snoozeMaxCount INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE alarm_times ADD COLUMN snoozeCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 -> v3：时间点精确到秒；任务增加小憩标记（旧版小憩条目按名称归位） */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_times ADD COLUMN `second` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarm_tasks ADD COLUMN isNap INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE alarm_tasks SET isNap = 1 WHERE type = 'ONCE' AND name LIKE '小憩%'")
            }
        }

        /** v5 -> v6：每月类型支持多日期 */
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_tasks ADD COLUMN monthDays TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 -> v5：任务增加备注字段 */
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_tasks ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3 -> v4：修复在 v3 前创建的小憩条目未标记 isNap 的问题 */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("UPDATE alarm_tasks SET isNap = 1 WHERE type = 'ONCE' AND name LIKE '小憩%'")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "multitime_alarm.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}

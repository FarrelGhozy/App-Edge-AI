package com.facegate.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.facegate.core.data.local.dao.AttendanceLogDao
import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.data.local.entity.AttendanceLogEntity
import com.facegate.core.data.local.entity.CampusRuleEntity
import com.facegate.core.data.local.entity.FaceVectorEntity
import com.facegate.core.data.local.entity.StudentEntity
import com.facegate.core.data.local.converter.Converters

@Database(
    entities = [
        StudentEntity::class,
        FaceVectorEntity::class,
        AttendanceLogEntity::class,
        CampusRuleEntity::class,
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun faceVectorDao(): FaceVectorDao
    abstract fun attendanceLogDao(): AttendanceLogDao
    abstract fun campusRuleDao(): CampusRuleDao

    companion object {
        // #119: migrasi 2 → 3 menambah kolom client_id (nullable) pada
        // attendance_logs — idempotency key utk batch sync offline.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attendance_logs ADD COLUMN client_id TEXT")
            }
        }

        // Normalisasi nama kolom (fix mismatch kolom attendance_logs).
        //
        // Build lama (sebelum #119 di-@ColumnInfo) men-generate schema yang
        // menamai kolom `clientId` (nama field) padahal MIGRATION_2_3
        // menambah `client_id`. Akibatnya Room melempar:
        //   "Migration didn't properly handle: attendance_logs
        //    → expected client_id, found clientId"
        // bagi user yang sudah pernah menjalankan build buggy (DB sudah v3
        // dgn kolom `clientId`). Migrasi ini menormalisasi kolom ke `client_id`.
        // Jika DB belum pernah kena build buggy (v2 → v3 fresh), kolom sudah
        // benar `client_id`, dan migrasi ini tak melakukan apa-apa (no-op)
        // sehingga tetap lolos validasi Room.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasClientId = db.query("PRAGMA table_info(attendance_logs)").use { c ->
                    var found = false
                    while (c.moveToNext()) {
                        if (c.getString(1) == "clientId") { found = true; break }
                    }
                    found
                }
                val hasClientIdSnake = db.query("PRAGMA table_info(attendance_logs)").use { c ->
                    var found = false
                    while (c.moveToNext()) {
                        if (c.getString(1) == "client_id") { found = true; break }
                    }
                    found
                }
                // Hanya rename bila kolom salah-nama ada & kolom yang benar
                // belum ada (hindari konflik duplicate column).
                if (hasClientId && !hasClientIdSnake) {
                    db.execSQL("ALTER TABLE attendance_logs RENAME COLUMN clientId TO client_id")
                }
            }
        }

        // #132/#133: FaceVector PK [studentId, pose] → id auto-increment agar bisa
        // menampung banyak vektor FRONT_1..FRONT_N per santri (registrasi video
        // 10 detik). Ubah primary key TIDAK bisa via ALTER TABLE (Room validasi
        // skema) → pola create-new + copy + drop + rename. Kolom `vector`
        // disimpan sebagai BLOB (FloatArray ↔ ByteArray via Converters), sama
        // dengan skema lama, sehingga data vektor tersalin apa adanya.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS face_vectors_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        studentId TEXT NOT NULL,
                        pose TEXT NOT NULL,
                        vector BLOB NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO face_vectors_new (studentId, pose, vector, updatedAt)
                    SELECT studentId, pose, vector, updatedAt FROM face_vectors
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE face_vectors")
                db.execSQL("ALTER TABLE face_vectors_new RENAME TO face_vectors")
            }
        }
    }
}

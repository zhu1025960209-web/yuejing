package com.example.yuejing.data.database

import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.yuejing.data.model.PeriodRecord
import com.example.yuejing.data.model.RecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class SQLiteDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    companion object {
        private const val TAG = "YueJingDB"
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "period_records.db"
        
        private const val TABLE_PERIOD_RECORDS = "period_records"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TYPE = "type"
        private const val COLUMN_START_DATE = "start_date"
        private const val COLUMN_END_DATE = "end_date"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_MOOD = "mood"
        private const val COLUMN_SYMPTOMS = "symptoms"
        private const val COLUMN_INTIMACY_TYPE = "intimacy_type"
        private const val COLUMN_NOTE = "note"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_PERIOD_RECORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TYPE TEXT NOT NULL,
                $COLUMN_START_DATE TEXT,
                $COLUMN_END_DATE TEXT,
                $COLUMN_DATE TEXT,
                $COLUMN_MOOD TEXT,
                $COLUMN_SYMPTOMS TEXT,
                $COLUMN_INTIMACY_TYPE TEXT,
                $COLUMN_NOTE TEXT,
                $COLUMN_TIMESTAMP TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PERIOD_RECORDS")
        onCreate(db)
    }

    suspend fun insertRecord(record: PeriodRecord): Long {
        return withContext(Dispatchers.IO) {
            val db = this@SQLiteDatabaseHelper.writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_TYPE, record.type.name)
                put(COLUMN_START_DATE, record.startDate)
                put(COLUMN_END_DATE, record.endDate)
                put(COLUMN_DATE, record.date)
                put(COLUMN_MOOD, record.mood)
                put(COLUMN_SYMPTOMS, record.symptoms.joinToString(","))
                put(COLUMN_INTIMACY_TYPE, record.intimacyType)
                put(COLUMN_NOTE, record.note)
                put(COLUMN_TIMESTAMP, record.timestamp)
            }
            db.insert(TABLE_PERIOD_RECORDS, null, values)
        }
    }

    suspend fun updateRecord(record: PeriodRecord): Int {
        return withContext(Dispatchers.IO) {
            val db = this@SQLiteDatabaseHelper.writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_TYPE, record.type.name)
                put(COLUMN_START_DATE, record.startDate)
                put(COLUMN_END_DATE, record.endDate)
                put(COLUMN_DATE, record.date)
                put(COLUMN_MOOD, record.mood)
                put(COLUMN_SYMPTOMS, record.symptoms.joinToString(","))
                put(COLUMN_INTIMACY_TYPE, record.intimacyType)
                put(COLUMN_NOTE, record.note)
                put(COLUMN_TIMESTAMP, record.timestamp)
            }
            db.update(
                TABLE_PERIOD_RECORDS, 
                values, 
                "$COLUMN_ID = ?", 
                arrayOf(record.id)
            )
        }
    }

    suspend fun deleteRecord(record: PeriodRecord): Int {
        return withContext(Dispatchers.IO) {
            val db = this@SQLiteDatabaseHelper.writableDatabase
            db.delete(
                TABLE_PERIOD_RECORDS,
                "$COLUMN_ID = ?",
                arrayOf(record.id)
            )
        }
    }

    suspend fun getAllRecords(): List<PeriodRecord> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting to query all records")
            val db = this@SQLiteDatabaseHelper.readableDatabase
            val cursor = db.query(
                TABLE_PERIOD_RECORDS,
                null,
                null,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP DESC"
            )
            
            val records = mutableListOf<PeriodRecord>()
            cursor.use {
                while (it.moveToNext()) {
                    val record = cursorToRecord(it)
                    records.add(record)
                }
            }
            Log.d(TAG, "Query completed, total ${records.size} records")
            records
        }
    }

    suspend fun getRecordById(id: String): PeriodRecord? {
        return withContext(Dispatchers.IO) {
            val db = this@SQLiteDatabaseHelper.readableDatabase
            val cursor = db.query(
                TABLE_PERIOD_RECORDS,
                null,
                "$COLUMN_ID = ?",
                arrayOf(id),
                null,
                null,
                null,
                "1"
            )
            
            cursor.use {
                if (it.moveToFirst()) {
                    cursorToRecord(it)
                } else {
                    null
                }
            }
        }
    }

    suspend fun deleteAllRecords(): Int {
        return withContext(Dispatchers.IO) {
            val db = this@SQLiteDatabaseHelper.writableDatabase
            db.delete(TABLE_PERIOD_RECORDS, null, null)
        }
    }
    
    suspend fun saveAllRecords(records: List<PeriodRecord>): Boolean {
        return withContext(Dispatchers.IO + NonCancellable) {
            val db = this@SQLiteDatabaseHelper.writableDatabase
            try {
                Log.d(TAG, "Starting transaction to save ${records.size} records")
                db.beginTransaction()
                
                // 先删除所有记录
                Log.d(TAG, "Deleting all old records...")
                val deletedRows = db.delete(TABLE_PERIOD_RECORDS, null, null)
                Log.d(TAG, "Delete completed, removed $deletedRows records")
                
                // 插入所有新记录
                Log.d(TAG, "Starting to insert ${records.size} new records...")
                var insertedCount = 0
                for (record in records) {
                    val values = ContentValues().apply {
                        put(COLUMN_TYPE, record.type.name)
                        put(COLUMN_START_DATE, record.startDate)
                        put(COLUMN_END_DATE, record.endDate)
                        put(COLUMN_DATE, record.date)
                        put(COLUMN_MOOD, record.mood)
                        put(COLUMN_SYMPTOMS, record.symptoms.joinToString(","))
                        put(COLUMN_INTIMACY_TYPE, record.intimacyType)
                        put(COLUMN_NOTE, record.note)
                        put(COLUMN_TIMESTAMP, record.timestamp)
                    }
                    val rowId = db.insert(TABLE_PERIOD_RECORDS, null, values)
                    if (rowId != -1L) {
                        insertedCount++
                    } else {
                        Log.w(TAG, "Failed to insert record: $record")
                    }
                }
                Log.d(TAG, "Insert completed, successfully inserted $insertedCount/${records.size} records")
                
                db.setTransactionSuccessful()
                Log.i(TAG, "Transaction committed successfully, saved $insertedCount records")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Transaction failed", e)
                false
            } finally {
                db.endTransaction()
                Log.d(TAG, "Transaction ended")
            }
        }
    }

    private fun cursorToRecord(cursor: android.database.Cursor): PeriodRecord {
        return PeriodRecord(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            type = RecordType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE))),
            startDate = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_START_DATE)),
            endDate = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_END_DATE)),
            date = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
            mood = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_MOOD)),
            symptoms = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_SYMPTOMS))
                ?.takeIf { it.isNotEmpty() }
                ?.split(",")
                ?: emptyList(),
            intimacyType = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_INTIMACY_TYPE)),
            note = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_NOTE)) ?: "",
            timestamp = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
        )
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (isNull(columnIndex)) null else getString(columnIndex)
    }
}
package ai.neargo.fnb.pos.core.cloud

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * 云端映射表与需处理队列（与业务库同一个 SQLite 文件；表按需创建，不改业务库版本）。
 * scope = 「本机门店id@服务端门店号」：换门店或改服务端门店号不会串号。
 */
internal class SqliteCloudRefs(private val database: () -> SQLiteDatabase, private val scope: String) : CloudRefs {
    private val db get() = database().also(::ensure)

    override fun get(kind: String, localId: String): CloudRef? =
        db.rawQuery("SELECT remote_no,version FROM cloud_refs WHERE scope=? AND kind=? AND local_id=?", arrayOf(scope, kind, localId)).use {
            if (it.moveToFirst()) CloudRef(kind, localId, it.getString(0), it.getLong(1)) else null
        }

    override fun all(kind: String): List<CloudRef> =
        db.rawQuery("SELECT local_id,remote_no,version FROM cloud_refs WHERE scope=? AND kind=? ORDER BY rowid", arrayOf(scope, kind)).use {
            buildList { while (it.moveToNext()) add(CloudRef(kind, it.getString(0), it.getString(1), it.getLong(2))) }
        }

    override fun put(ref: CloudRef) {
        db.insertWithOnConflict("cloud_refs", null, ContentValues().apply {
            put("scope", scope); put("kind", ref.kind); put("local_id", ref.localId); put("remote_no", ref.remoteNo); put("version", ref.version)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun issues(): List<CloudIssue> =
        db.rawQuery("SELECT event_id,type,aggregate_id,code,message,at FROM cloud_issues WHERE scope=? ORDER BY at", arrayOf(scope)).use {
            buildList { while (it.moveToNext()) add(CloudIssue(it.getString(0), it.getString(1), it.getString(2), it.getInt(3), it.getString(4), it.getLong(5))) }
        }

    override fun addIssue(issue: CloudIssue) {
        db.insertWithOnConflict("cloud_issues", null, ContentValues().apply {
            put("scope", scope); put("event_id", issue.eventId); put("type", issue.type); put("aggregate_id", issue.aggregateId)
            put("code", issue.code); put("message", issue.message); put("at", issue.at)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun resolve(eventId: String) { db.delete("cloud_issues", "scope=? AND event_id=?", arrayOf(scope, eventId)) }

    companion object {
        @Volatile private var ready = false
        private fun ensure(db: SQLiteDatabase) {
            if (ready) return
            db.execSQL("CREATE TABLE IF NOT EXISTS cloud_refs (scope TEXT NOT NULL,kind TEXT NOT NULL,local_id TEXT NOT NULL,remote_no TEXT NOT NULL,version INTEGER NOT NULL,PRIMARY KEY(scope,kind,local_id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS cloud_issues (scope TEXT NOT NULL,event_id TEXT NOT NULL,type TEXT NOT NULL,aggregate_id TEXT NOT NULL,code INTEGER NOT NULL,message TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(scope,event_id))")
            ready = true
        }
    }
}

/** 云端连接设置：每台设备一份（SharedPreferences） */
internal class PrefsCloudSettings(context: Context) : CloudSettingsStore {
    private val sp = context.applicationContext.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    override fun load() = CloudSettings(sp.getString("baseUrl", "").orEmpty(), sp.getString("storeNo", "").orEmpty(), sp.getString("token", "").orEmpty())
    override fun save(settings: CloudSettings) {
        sp.edit().putString("baseUrl", settings.baseUrl).putString("storeNo", settings.storeNo).putString("token", settings.token).apply()
    }
    override fun lastOnlineAt() = sp.getLong("lastOnlineAt", 0)
    override fun saveLastOnlineAt(at: Long) { sp.edit().putLong("lastOnlineAt", at).apply() }
}


/** 推送：安装号（首次生成）、每个门店的端号与最后事件序号 */
internal class PrefsPushStore(context: Context) : PushStore {
    private val sp = context.applicationContext.getSharedPreferences("cloud_push", Context.MODE_PRIVATE)
    override fun installId(): String = sp.getString("installId", null) ?: java.util.UUID.randomUUID().toString().also { sp.edit().putString("installId", it).apply() }
    override fun terminalNo(storeNo: String) = sp.getString("terminal/$storeNo", null)
    override fun saveTerminalNo(storeNo: String, terminalNo: String?) { sp.edit().apply { if (terminalNo == null) remove("terminal/$storeNo") else putString("terminal/$storeNo", terminalNo) }.apply() }
    override fun lastEventId(storeNo: String) = sp.getString("last/$storeNo", null)
    override fun saveLastEventId(storeNo: String, id: String) { sp.edit().putString("last/$storeNo", id).apply() }
}

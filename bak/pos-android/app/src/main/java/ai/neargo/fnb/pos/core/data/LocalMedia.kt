package ai.neargo.fnb.pos.core.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 本机图片库（v3.4 离线版）：校验 JPG / PNG / WebP、≤ 5MB、可解码，复制到应用私有目录，返回 file:// 地址。
 * 阶段三改为授权上传并换成 mediaId，界面状态（读取中 / 成功 / 失败）不变。
 */
class LocalMedia(private val context: Context) {
    private val allowed = setOf("image/jpeg", "image/png", "image/webp")
    private val maxBytes = 5L * 1024 * 1024

    suspend fun import(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver
            val type = cr.getType(uri)
            require(type in allowed) { "只支持 JPG、PNG、WebP 图片" }
            val size = cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
            require(size in 1..maxBytes) { "图片不能超过 5MB" }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            require(opts.outWidth > 0 && opts.outHeight > 0) { "图片无法读取，请重新选择" }
            val ext = when (type) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val dir = File(context.filesDir, "media").apply { mkdirs() }
            val out = File(dir, "${UUID.randomUUID()}.$ext")
            cr.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            Uri.fromFile(out).toString()
        }
    }
}

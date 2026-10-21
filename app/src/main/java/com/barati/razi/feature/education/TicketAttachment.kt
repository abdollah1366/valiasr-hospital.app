package com.barati.razi.feature.education

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** پیوند تیکت: همان نوع‌ها و سقف حجمی که سرور می‌پذیرد (app/routers/education_engage.py). */
sealed interface AttachmentCheck {
    data class Ok(val filename: String, val contentType: String) : AttachmentCheck
    data class TooLarge(val maxMb: Int) : AttachmentCheck
    data object Unsupported : AttachmentCheck
}

object TicketAttachmentRules {
    private data class Rule(val ext: String, val maxMb: Int)

    private val rules = mapOf(
        "image/jpeg" to Rule("jpg", 10),
        "image/png" to Rule("png", 10),
        "image/webp" to Rule("webp", 10),
        "audio/mpeg" to Rule("mp3", 20),
        "audio/mp4" to Rule("m4a", 20),
        "audio/x-m4a" to Rule("m4a", 20),
        "audio/m4a" to Rule("m4a", 20),
        "audio/aac" to Rule("aac", 20),
        "audio/ogg" to Rule("ogg", 20),
        "video/mp4" to Rule("mp4", 100),
        "video/webm" to Rule("webm", 100),
        "video/3gpp" to Rule("3gp", 100),
    )

    /** [size] اگر مشخص نباشد بررسی حجم به سرور سپرده می‌شود. */
    fun check(mime: String?, size: Long?, nowMillis: Long): AttachmentCheck {
        val rule = rules[mime.orEmpty().lowercase()] ?: return AttachmentCheck.Unsupported
        if (size != null && size > rule.maxMb * 1024L * 1024L) return AttachmentCheck.TooLarge(rule.maxMb)
        return AttachmentCheck.Ok("attachment-$nowMillis.${rule.ext}", mime.orEmpty().lowercase())
    }
}

fun ContentResolver.sizeOf(uri: Uri): Long? =
    query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }

/** فایل بزرگ را تکه‌تکه از روی گوشی می‌فرستد، بدون اینکه همه‌اش در حافظه بماند. */
class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val type: String,
    private val size: Long?,
) : RequestBody() {
    override fun contentType(): MediaType? = type.toMediaTypeOrNull()

    override fun contentLength(): Long = size ?: -1

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: error("cannot open $uri")
        input.source().use { sink.writeAll(it) }
    }
}

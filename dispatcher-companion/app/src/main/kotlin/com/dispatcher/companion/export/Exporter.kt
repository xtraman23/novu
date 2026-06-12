package com.dispatcher.companion.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.dispatcher.companion.model.CallSummary
import java.io.File

/** FR-803 export pipeline: TXT, CSV, PDF, clipboard, email, share intent. */
object Exporter {

    fun toTxt(s: CallSummary): String = buildString {
        appendLine("CALL SUMMARY — ${s.lane}")
        appendLine()
        appendLine(s.summaryDetailed)
        appendLine()
        appendLine("Key details: ${s.keyDetails}")
        appendLine("Follow-up: ${s.followUpActions}")
        appendLine("Next steps: ${s.nextSteps}")
    }

    fun toCsv(s: CallSummary): String {
        fun esc(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        return "lane,summary,key_details,follow_up,next_steps\n" +
            listOf(s.lane, s.summaryShort, s.keyDetails, s.followUpActions, s.nextSteps)
                .joinToString(",", transform = ::esc)
    }

    fun toPdf(context: Context, s: CallSummary): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val paint = Paint().apply { textSize = 11f }
        var y = 40f
        (listOf("CALL SUMMARY — ${s.lane}", "") + toTxt(s).lines()).forEach { line ->
            line.chunked(90).forEach { chunk ->
                page.canvas.drawText(chunk, 40f, y, paint)
                y += 16f
            }
        }
        doc.finishPage(page)
        val file = File(context.cacheDir, "call-summary-${System.currentTimeMillis()}.pdf")
        file.outputStream().use(doc::writeTo)
        doc.close()
        return file
    }

    fun toClipboard(context: Context, s: CallSummary) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Call summary", toTxt(s)))
    }

    fun share(context: Context, s: CallSummary) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Call summary — ${s.lane}")
            putExtra(Intent.EXTRA_TEXT, toTxt(s))
        }
        context.startActivity(
            Intent.createChooser(intent, "Export summary").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

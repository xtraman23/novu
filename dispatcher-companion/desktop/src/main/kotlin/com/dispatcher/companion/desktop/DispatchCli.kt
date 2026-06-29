package com.dispatcher.companion.desktop

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.report.CallReport
import java.io.File

/**
 * The desktop dispatch CLI. Reads a labelled transcript on stdin, one
 * utterance per line:
 *
 *     BROKER: I've got a load out of Dallas going to Atlanta
 *     DISPATCHER: what's it pay
 *     B: nineteen hundred, it's FCFS, 42,000 of dry goods
 *
 * Accepted prefixes (case-insensitive): "BROKER:"/"B:" and "DISPATCHER:"/"D:".
 * Unlabelled lines are treated as the broker (they carry the load info).
 * On EOF (or a line "STOP") it writes transcript_*.txt and info_*.txt.
 *
 * On Windows this is driven by the C# capture tool:
 *     DispatcherCapture.exe | dispatcher-desktop
 * where DispatcherCapture transcribes mic (you) + per-process loopback
 * (RingCentral = broker) and emits the labelled lines.
 *
 * Optional arg: output directory (default current dir).
 */
fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: ".").apply { mkdirs() }
    val session = DesktopDispatchSession()
    val out = System.out

    out.println("Dispatcher Companion — desktop. Paste/stream labelled lines; Ctrl-D or 'STOP' to finish.")
    out.println("-".repeat(60))

    for (raw in generateSequence(::readLine)) {
        val line = raw.trim()
        if (line.equals("STOP", ignoreCase = true)) break
        if (line.isEmpty()) continue
        val (speaker, text) = parseLine(line)
        if (text.isBlank()) continue
        val u = session.onUtterance(speaker, text)
        printLiveStatus(out, session, u)
    }

    val ts = System.currentTimeMillis()
    val transcriptFile = File(outDir, "transcript_$ts.txt")
        .apply { writeText(CallReport.transcriptText(session.transcript)) }
    val infoFile = File(outDir, "info_$ts.txt")
        .apply { writeText(CallReport.infoText(session.fields, session.rateEvents)) }

    out.println("-".repeat(60))
    out.println("Saved full transcript : ${transcriptFile.absolutePath}")
    out.println("Saved load info       : ${infoFile.absolutePath}")
}

/** Split a line into (speaker, text) by its prefix. Unlabelled → broker. */
internal fun parseLine(line: String): Pair<Speaker, String> {
    val m = Regex("""^\s*(broker|dispatcher|b|d)\s*[:\-]\s*(.*)$""", RegexOption.IGNORE_CASE).find(line)
    return if (m != null) {
        val tag = m.groupValues[1].lowercase()
        val speaker = if (tag == "dispatcher" || tag == "d") Speaker.DISPATCHER else Speaker.BROKER
        speaker to m.groupValues[2].trim()
    } else {
        Speaker.BROKER to line
    }
}

private fun printLiveStatus(out: java.io.PrintStream, s: DesktopDispatchSession, u: DesktopDispatchSession.Update) {
    val who = if (u.segment.speaker == Speaker.DISPATCHER) "You" else "Broker"
    out.println("$who: ${u.segment.text}")
    val f = s.fields
    fun show(label: String, key: FieldKey) = f[key]?.let { out.println("    $label = ${it.text}") }
    show("P Pickup", FieldKey.PICKUP); show("D Delivery", FieldKey.DELIVERY)
    show("W Weight", FieldKey.WEIGHT); show("C Commodity", FieldKey.COMMODITY)
    show("R Rate", FieldKey.RATE)
    show("  Equipment", FieldKey.EQUIPMENT); show("  Pickup appt", FieldKey.APPOINTMENT_PICKUP)
    show("  Special", FieldKey.SPECIAL_REQUIREMENTS)
    s.advice?.let {
        out.println("    >> Counter $%,.0f (floor $%,.0f, accept %d%%): \"%s\"".format(
            it.counterUsd, it.likelyFloorUsd, (it.acceptanceProbability * 100).toInt(), it.suggestedReply,
        ))
    }
}

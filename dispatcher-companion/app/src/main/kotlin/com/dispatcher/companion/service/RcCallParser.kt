package com.dispatcher.companion.service

/**
 * Pure RingCentral-notification heuristics (FR-101/102) — kept free of
 * Android types so they are JVM-testable. Multi-signal and deliberately
 * loose: RingCentral can change copy between releases.
 */
object RcCallParser {

    private val rcPackages = setOf("com.glip.mobile", "com.ringcentral.android")

    fun isRingCentral(packageName: String): Boolean =
        packageName in rcPackages ||
            packageName.contains("ringcentral", ignoreCase = true) ||
            packageName.contains("glip", ignoreCase = true)

    private val callTextCues = listOf(
        "ongoing call", "call in progress", "on a call", "active call", "tap to return",
    )
    private val durationPattern = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""")

    /** True when an ongoing RC notification looks like a live call. */
    fun looksLikeActiveCall(title: String?, text: String?, isOngoing: Boolean): Boolean {
        if (!isOngoing) return false
        val haystack = "${title.orEmpty()} ${text.orEmpty()}".lowercase()
        return callTextCues.any { haystack.contains(it) } || durationPattern.containsMatchIn(haystack)
    }

    /** Best-effort caller name: the notification title minus app boilerplate. */
    fun callerName(title: String?): String? =
        title?.replace(Regex("(?i)(ringcentral|ongoing call|call)"), "")
            ?.trim(' ', '-', '–', '—', ':', ',')
            ?.takeIf { it.isNotBlank() }
}

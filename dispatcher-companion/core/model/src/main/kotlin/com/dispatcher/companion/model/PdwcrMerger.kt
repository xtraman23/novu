package com.dispatcher.companion.model

/**
 * Merges newly extracted field values into the live load state.
 *
 * Rules (Phase 2 §5):
 *  1. MANUAL values are sticky — automated sources never overwrite them.
 *  2. A MANUAL incoming value always wins.
 *  3. Otherwise a value is replaced only when the incoming confidence is at
 *     least as high as the current one — fields never regress while the
 *     broker keeps talking.
 */
object PdwcrMerger {

    fun merge(
        current: Map<FieldKey, FieldValue>,
        key: FieldKey,
        incoming: FieldValue,
    ): Map<FieldKey, FieldValue> {
        val existing = current[key] ?: return current + (key to incoming)
        if (incoming.source == FieldSource.MANUAL) return current + (key to incoming)
        if (existing.source == FieldSource.MANUAL) return current
        if (incoming.confidence >= existing.confidence) return current + (key to incoming)
        return current
    }

    fun mergeAll(
        current: Map<FieldKey, FieldValue>,
        incoming: Map<FieldKey, FieldValue>,
    ): Map<FieldKey, FieldValue> =
        incoming.entries.fold(current) { acc, (k, v) -> merge(acc, k, v) }
}

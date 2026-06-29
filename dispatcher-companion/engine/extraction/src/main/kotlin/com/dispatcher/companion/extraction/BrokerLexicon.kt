package com.dispatcher.companion.extraction

/**
 * Freight-broker vocabulary the tier-1 extractor is "trained" on. Each entry
 * maps the many ways a broker says a thing to one canonical label, matched on
 * word boundaries so "van" doesn't fire inside "Sullivan".
 *
 * This is the knowledge base; expanding it here teaches the app new terms with
 * no other code change.
 */
object BrokerLexicon {

    private fun word(vararg variants: String): Regex =
        Regex("\\b(?:${variants.joinToString("|") { Regex.escape(it) }})\\b", RegexOption.IGNORE_CASE)

    /** (matcher, canonical) pairs; most-specific listed first. */
    val equipment: List<Pair<Regex, String>> = listOf(
        word("dry van", "dryvan", "53 van", "53' van", "dv") to "DRY_VAN",
        word("reefer", "refrigerated", "temp control", "temp controlled", "temp-controlled",
            "temperature controlled", "temperature control") to "REEFER",
        word("step deck", "stepdeck", "step-deck") to "STEP_DECK",
        word("flatbed", "flat bed", "flat") to "FLATBED",
        word("rgn", "double drop", "lowboy") to "RGN",
        word("conestoga", "connie") to "CONESTOGA",
        word("power only", "power-only") to "POWER_ONLY",
        word("box truck", "straight truck") to "BOX_TRUCK",
        word("sprinter", "cargo van") to "SPRINTER",
        word("hotshot", "hot shot") to "HOTSHOT",
        word("van") to "DRY_VAN",
    )

    /** (matcher, canonical label). */
    val commodities: List<Pair<Regex, String>> = listOf(
        "dry goods", "general freight", "paper products", "paper rolls", "paper",
        "produce", "fresh produce", "frozen food", "frozen", "meat", "poultry", "dairy",
        "packaged food", "canned goods", "food grade", "grocery", "beverages",
        "bottled water", "water", "steel", "steel coils", "aluminum", "copper",
        "scrap metal", "lumber", "building materials", "appliances", "electronics",
        "furniture", "auto parts", "automotive", "tires", "plastics", "chemicals",
        "machinery", "equipment", "retail goods", "consumer goods", "textiles",
        "pallets", "palletized freight",
    ).map { word(it) to it.split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) } }

    /** Handling / accessorial requirements a broker mentions (FR-402). */
    val specialRequirements: List<Pair<Regex, String>> = listOf(
        word("drop and hook", "drop & hook", "drop trailer", "drop n hook") to "Drop & hook",
        word("live load", "live loading") to "Live load",
        word("live unload", "live unloading", "live offload") to "Live unload",
        word("no touch", "no-touch", "no touch freight") to "No-touch freight",
        word("driver assist", "driver-assist", "hand load", "hand unload", "hand bomb") to "Driver assist",
        word("hazmat", "hazardous", "placards", "placarded") to "Hazmat",
        word("tarp", "tarps", "tarping", "tarped") to "Tarps",
        word("team", "teams", "team load", "team driver") to "Team",
        word("twic", "twic card") to "TWIC card",
        word("lumper", "lumper fee") to "Lumper fee",
        word("liftgate", "lift gate") to "Liftgate",
        word("multi-stop", "multi stop", "multiple stops", "two stops", "three stops", "stop off") to "Multi-stop",
        word("partial", "partial load", "ltl", "less than truckload") to "Partial",
        word("oversize", "oversized", "over dimensional", "over-dimensional", "od load") to "Oversized",
        word("pallet exchange", "pallet swap", "pallet jack") to "Pallet exchange",
        word("blind shipment", "blind load") to "Blind shipment",
        word("escort", "pilot car") to "Escort required",
        word("dock high", "dock-high") to "Dock high",
    )

    /** Appointment-scheduling phrases → canonical type (FCFS / appointment). */
    val fcfs = word("fcfs", "first come first serve", "first come first served", "first come, first served")
    val appointment = word("appointment", "appt", "appt only", "by appointment", "appointment only", "scheduled")

    val deliveryHint = Regex(
        "\\b(deliver|delivery|delivering|drop|dropping|consignee|receiver|destination|unload|unloading)\\b",
        RegexOption.IGNORE_CASE,
    )
    val pickupHint = Regex(
        "\\b(pick|pickup|picking|shipper|origin)\\b", RegexOption.IGNORE_CASE,
    )
}

package com.bobsdevattic.networkanalyzer.network

/**
 * Maps a MAC address to a hardware vendor by its OUI (first 3 octets).
 *
 * This is a small **starter set** of common, high-confidence prefixes so the UI
 * has something useful out of the box. The full IEEE OUI registry (~35k entries)
 * should be bundled or fetched later — see the "OUI database" open decision in
 * the README. Unknown prefixes return null (UI shows the raw MAC).
 *
 * Locally-administered addresses (2nd-least-significant bit of the first octet
 * set) are randomized/virtual and have no real vendor, so they're reported as
 * such rather than looked up.
 */
object OuiLookup {

    fun vendorFor(mac: String?): String? {
        if (mac.isNullOrBlank()) return null
        val hex = mac.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        if (hex.length < 6) return null

        if (isLocallyAdministered(hex)) return "Locally administered (randomized/virtual)"

        return OUI[hex.substring(0, 6)]
    }

    private fun isLocallyAdministered(hexNoSep: String): Boolean {
        val firstOctet = hexNoSep.substring(0, 2).toIntOrNull(16) ?: return false
        return (firstOctet and 0x02) != 0
    }

    // Curated, high-confidence subset. Keyed by uppercase 6-hex-char OUI.
    private val OUI: Map<String, String> = mapOf(
        // Apple
        "000A95" to "Apple",
        "3C0754" to "Apple",
        "A483E7" to "Apple",
        "F01898" to "Apple",
        // Raspberry Pi
        "B827EB" to "Raspberry Pi",
        "DCA632" to "Raspberry Pi",
        "E45F01" to "Raspberry Pi",
        // Espressif (ESP32 / ESP8266 IoT)
        "5CCF7F" to "Espressif",
        "240AC4" to "Espressif",
        "A020A6" to "Espressif",
        "246F28" to "Espressif",
        // Google / Nest
        "001A11" to "Google",
        "3C5AB4" to "Google",
        "F4F5E8" to "Google",
        // Ethernet-adapter silicon
        "00E04C" to "Realtek",
        "000EC6" to "ASIX",
        // Virtualization
        "000C29" to "VMware",
        "005056" to "VMware",
        "00155D" to "Microsoft (Hyper-V)",
        // Networking gear
        "245A4C" to "Ubiquiti",
        "F09FC2" to "Ubiquiti",
        // Media
        "000E58" to "Sonos",
    )
}

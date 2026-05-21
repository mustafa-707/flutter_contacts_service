package com.example.flutter_contacts_service

/**
 * Minimal vCard 3.0 serializer / parser for `Contact`-shaped maps.
 *
 * Android has no public vCard API, so this is a self-contained implementation
 * covering the fields this plugin exposes: name, organization, phones, emails,
 * postal addresses, note and birthday.
 */
internal object VCard {

    /** Serializes contact maps into a single vCard 3.0 document. */
    fun serialize(contacts: List<Map<String, Any?>>): String =
        contacts.joinToString("\r\n") { serializeOne(it) }

    private fun serializeOne(c: Map<String, Any?>): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\r\n")
        sb.append("VERSION:3.0\r\n")

        val given = str(c["givenName"])
        val middle = str(c["middleName"])
        val family = str(c["familyName"])
        val prefix = str(c["prefix"])
        val suffix = str(c["suffix"])
        sb.append("N:${esc(family)};${esc(given)};${esc(middle)};${esc(prefix)};${esc(suffix)}\r\n")

        val fn =
            str(c["displayName"]).ifBlank {
                listOf(prefix, given, middle, family, suffix)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
        if (fn.isNotBlank()) sb.append("FN:${esc(fn)}\r\n")

        str(c["company"]).takeIf { it.isNotBlank() }?.let { sb.append("ORG:${esc(it)}\r\n") }
        str(c["jobTitle"]).takeIf { it.isNotBlank() }?.let { sb.append("TITLE:${esc(it)}\r\n") }

        (c["phones"] as? List<*>)?.forEach { p ->
            val m = p as? Map<*, *> ?: return@forEach
            val value = str(m["value"])
            if (value.isNotBlank()) {
                val label = str(m["label"]).ifBlank { "voice" }
                sb.append("TEL;TYPE=${esc(label)}:${esc(value)}\r\n")
            }
        }
        (c["emails"] as? List<*>)?.forEach { e ->
            val m = e as? Map<*, *> ?: return@forEach
            val value = str(m["value"])
            if (value.isNotBlank()) {
                val label = str(m["label"]).ifBlank { "internet" }
                sb.append("EMAIL;TYPE=${esc(label)}:${esc(value)}\r\n")
            }
        }
        (c["postalAddresses"] as? List<*>)?.forEach { a ->
            val m = a as? Map<*, *> ?: return@forEach
            val label = str(m["label"]).ifBlank { "home" }
            // ADR structure: po-box;extended;street;city;region;postcode;country
            sb.append(
                "ADR;TYPE=${esc(label)}:;;${esc(str(m["street"]))};${esc(str(m["city"]))};" +
                    "${esc(str(m["region"]))};${esc(str(m["postcode"]))};${esc(str(m["country"]))}\r\n"
            )
        }

        str(c["note"]).takeIf { it.isNotBlank() }?.let { sb.append("NOTE:${esc(it)}\r\n") }
        str(c["birthday"]).takeIf { it.isNotBlank() }?.let { sb.append("BDAY:${esc(it)}\r\n") }

        sb.append("END:VCARD")
        return sb.toString()
    }

    /** Parses a vCard document into contact maps (one per `VCARD` block). */
    fun parse(text: String): List<Map<String, Any?>> {
        val cards = mutableListOf<Map<String, Any?>>()
        var current: MutableMap<String, Any?>? = null
        var phones = mutableListOf<Map<String, String>>()
        var emails = mutableListOf<Map<String, String>>()
        var addresses = mutableListOf<Map<String, String>>()

        for (line in unfold(text)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            when {
                trimmed.equals("BEGIN:VCARD", ignoreCase = true) -> {
                    current = mutableMapOf()
                    phones = mutableListOf()
                    emails = mutableListOf()
                    addresses = mutableListOf()
                }
                trimmed.equals("END:VCARD", ignoreCase = true) -> {
                    current?.let {
                        it["phones"] = phones
                        it["emails"] = emails
                        it["postalAddresses"] = addresses
                        cards.add(it)
                    }
                    current = null
                }
                current != null -> parseLine(trimmed, current, phones, emails, addresses)
            }
        }
        return cards
    }

    private fun parseLine(
        line: String,
        c: MutableMap<String, Any?>,
        phones: MutableList<Map<String, String>>,
        emails: MutableList<Map<String, String>>,
        addresses: MutableList<Map<String, String>>
    ) {
        val colon = line.indexOf(':')
        if (colon < 0) return
        val header = line.substring(0, colon).split(';')
        val value = line.substring(colon + 1)
        val name = header[0].substringBefore('.').uppercase() // drop optional group prefix
        val type =
            header.drop(1)
                .firstOrNull { it.uppercase().startsWith("TYPE=") }
                ?.substringAfter('=')
                ?.lowercase()
                .orEmpty()

        when (name) {
            "N" -> {
                val f = splitValue(value)
                c["familyName"] = f.getOrElse(0) { "" }
                c["givenName"] = f.getOrElse(1) { "" }
                c["middleName"] = f.getOrElse(2) { "" }
                c["prefix"] = f.getOrElse(3) { "" }
                c["suffix"] = f.getOrElse(4) { "" }
            }
            "FN" -> c["displayName"] = unesc(value)
            "ORG" -> c["company"] = splitValue(value).firstOrNull().orEmpty()
            "TITLE" -> c["jobTitle"] = unesc(value)
            "NOTE" -> c["note"] = unesc(value)
            "BDAY" -> c["birthday"] = unesc(value)
            "TEL" ->
                phones.add(mapOf("label" to type.ifBlank { "voice" }, "value" to unesc(value)))
            "EMAIL" ->
                emails.add(mapOf("label" to type.ifBlank { "internet" }, "value" to unesc(value)))
            "ADR" -> {
                val f = splitValue(value)
                addresses.add(
                    mapOf(
                        "label" to type.ifBlank { "home" },
                        "street" to f.getOrElse(2) { "" },
                        "city" to f.getOrElse(3) { "" },
                        "region" to f.getOrElse(4) { "" },
                        "postcode" to f.getOrElse(5) { "" },
                        "country" to f.getOrElse(6) { "" }
                    )
                )
            }
        }
    }

    /** Joins folded continuation lines (RFC 2425 line folding). */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (line in text.split("\r\n", "\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out[out.size - 1] + line.substring(1)
            } else {
                out.add(line)
            }
        }
        return out
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    /** Splits a structured value on unescaped `;`, then unescapes each field. */
    private fun splitValue(s: String): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                sb.append(ch).append(s[i + 1])
                i += 2
            } else if (ch == ';') {
                parts.add(unesc(sb.toString()))
                sb.setLength(0)
                i++
            } else {
                sb.append(ch)
                i++
            }
        }
        parts.add(unesc(sb.toString()))
        return parts
    }

    private fun str(v: Any?): String = v?.toString() ?: ""
}

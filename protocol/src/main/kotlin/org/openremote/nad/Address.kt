package org.openremote.nad

/** Accepts "192.168.1.30", "192.168.1.30:30001", "nad://host:port", "http://host/" -> host to port; null if unusable. */
fun parseAddress(input: String, defaultPort: Int = 30001): Pair<String, Int>? {
    var t = input.trim()
    t = t.substringAfter("://", t).substringBefore('/').substringBefore('?').trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    val host = t.substringBeforeLast(':', t).takeIf { ':' in t } ?: t
    val port = if (':' in t) t.substringAfterLast(':').toIntOrNull() ?: return null else defaultPort
    if (host.isEmpty() || port !in 1..65535) return null
    return host to port
}

package org.openremote.nad

/** Latest GitHub release, reduced to what the app needs. */
data class Release(val tag: String, val url: String)

private const val REPO = "benoit-prentout/nad-open-remote"
const val RELEASES_API = "https://api.github.com/repos/$REPO/releases/latest"
const val REPO_URL = "https://github.com/$REPO"

/**
 * Reads tag_name from the GitHub "latest release" JSON. The page URL is rebuilt from the tag (never taken from the
 * response), and a tag with unexpected characters is rejected, so a tampered answer cannot make the app open another site.
 */
fun parseRelease(json: String): Release? {
    val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return null
    if (!Regex("[A-Za-z0-9._-]{1,40}").matches(tag)) return null
    return Release(tag, "$REPO_URL/releases/tag/$tag")
}

/** "v0.1.1" is newer than "0.1.0". Numeric parts only; missing parts count as 0; anything unparseable is not newer. */
fun isNewer(current: String, latest: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.', '-').takeWhile { it.toIntOrNull() != null }.map { it.toInt() }
    val a = parts(current); val b = parts(latest)
    if (b.isEmpty() || a.isEmpty()) return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
        if (y != x) return y > x
    }
    return false
}

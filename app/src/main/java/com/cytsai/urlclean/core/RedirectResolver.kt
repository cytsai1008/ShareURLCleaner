package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.AggressiveMode
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Follows HTTP redirects (302 and friends) to the destination URL.
 *
 * OkHttp follows redirect chains itself, so the final URL is simply the URL of the
 * request that produced the last response — no need to walk `Location` headers.
 *
 * Interstitials that answer 200 and hand off in JavaScript instead
 * (`document.location.replace("…")`) are followed too, as one more hop.
 *
 * The chain is followed to its end rather than to a fixed depth: shorteners routinely point at
 * other shorteners, so the dive continues while each new URL is still one the caller wants
 * followed, and stops once a destination neither redirects nor hands off.
 */
object RedirectResolver {

    /**
     * Pinned deliberately — redirect behaviour is highly User-Agent sensitive.
     *
     * Measured against threads.com, facebook.com and b23.tv:
     *  - Browser UAs (mobile and desktop Chrome) get a JS handoff and never redirect at all;
     *    Facebook answers 400 outright.
     *  - OkHttp's own default UA is singled out by Meta and lands on a login wall,
     *    `m.facebook.com/login/?next=…`, which is worse than the link we started with.
     *  - Any other non-browser UA gets the plain 302 to the real content. This one does.
     *
     * Left as an explicit literal so an OkHttp upgrade — which would otherwise change the
     * default `okhttp/<version>` — cannot silently change where shared links resolve to.
     */
    private const val USER_AGENT = "curl/8.7.1"

    // ponytail: aggressive timeouts on purpose — this runs in the share path, so a slow
    // host must degrade to "share the URL as-is", never stall the chooser.
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().header("User-Agent", USER_AGENT).build(),
                )
            }
            .build()
    }

    /**
     * Safety backstop, not a policy. The chain runs until the URL stops moving; this only bounds
     * a loop that slips past the visited set — a redirector minting a fresh URL on every hop.
     */
    private const val MAX_HOPS = 10

    /** Enough for a handoff stub; a real page's redirect script sits in the first bytes too. */
    private const val MAX_BODY_BYTES = 64L * 1024

    /**
     * A JS handoff: `location.replace("…")`, `location.href = "…"`, `location = "…"`, with or
     * without a `window.`/`document.` prefix. Only absolute http(s) targets — a relative one
     * would land on the same interstitial we are trying to leave.
     */
    private val jsRedirect = Regex(
        """location(?:\.href)?\s*=\s*["'](https?:[^"']+)["']""" +
                """|location\.(?:replace|assign)\(\s*["'](https?:[^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Follows the chain to the end. Blocking — call from [kotlinx.coroutines.Dispatchers.IO].
     *
     * Keeps diving for as long as the URL it lands on is still one worth following: a shortener
     * pointing at a shortener pointing at the real page is one share, not three. It stops when the
     * destination answers without a redirect and without a JS handoff, when [shouldFollow] turns
     * the new host down, or when the chain doubles back on somewhere it has already been.
     *
     * Returns null only when nothing was learned: a hop that fails after the chain already moved
     * keeps the progress, because a half-followed shortener is still better than the shortener.
     *
     * @param shouldFollow gates each URL in turn, not just the first — the domain list applies to
     * the whole chain, so a hop that leaves it ends the dive.
     * @param clean applied to every hop, so tracking params picked up along the way never reach
     * the next request or the shared result.
     * @param hop one step of the chain. Defaults to the real network call; overridden in tests,
     * which is the only way to exercise the loop without a live server.
     */
    fun resolve(
        url: String,
        shouldFollow: (String) -> Boolean = { true },
        clean: (String) -> String = { it },
        hop: (String) -> String? = ::hop,
    ): String? {
        val start = clean(url)
        var current = start
        val visited = mutableSetOf(current)
        var failed = false
        for (i in 0 until MAX_HOPS) {
            if (!shouldFollow(current)) break
            val next = try {
                hop(current)
            } catch (_: Exception) {
                failed = true
                null
            } ?: break
            val cleaned = clean(next)
            // A URL seen before means a cycle; anything further would just go round again.
            if (!visited.add(cleaned)) break
            current = cleaned
        }
        return if (failed && current == start) null else current
    }

    /** One step of the chain, or null once [url] stops pointing anywhere else. */
    private fun hop(url: String): String? {
        // HEAD avoids downloading the body; some hosts only redirect on GET, and a JS handoff
        // is invisible without one, so fall back when HEAD lands us nowhere new.
        val viaHead = finalUrl(Request.Builder().url(url).head().build())
        if (viaHead != url) return viaHead

        return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val viaGet = response.request.url.toString()
            if (viaGet != url) viaGet else jsRedirectIn(response.peekBody(MAX_BODY_BYTES).string())
        }
    }

    /** `%` and friends — Meta escapes the percent signs of a nested URL this way. */
    private val jsUnicodeEscape = Regex("""\\u([0-9a-fA-F]{4})""")

    /** The target of a JavaScript handoff in [body], or null if there isn't one. */
    internal fun jsRedirectIn(body: String): String? = jsRedirect.find(body)
        ?.groupValues
        ?.drop(1)
        ?.firstOrNull { it.isNotEmpty() }
        // Inline scripts escape the slashes: "https:\/\/example.com".
        ?.replace("\\/", "/")
        ?.let { escaped ->
            jsUnicodeEscape.replace(escaped) { it.groupValues[1].toInt(16).toChar().toString() }
        }
        ?.takeIf { it.toHttpUrlOrNull() != null }

    /**
     * The URL the redirect chain ended at, whatever the final status code.
     *
     * Deliberately not gated on [okhttp3.Response.isSuccessful]: b23.tv answers 412 on the
     * destination while still having redirected correctly, and an error page's address is
     * still the address we were sent to.
     */
    private fun finalUrl(request: Request): String =
        client.newCall(request).execute().use { it.request.url.toString() }

    /** Whether [url] would actually be fetched under [mode] — checked before any network call. */
    fun appliesTo(mode: AggressiveMode, domains: Set<String>, url: String): Boolean = when (mode) {
        AggressiveMode.OFF -> false
        AggressiveMode.ALL -> url.toHttpUrlOrNull() != null
        AggressiveMode.SELECTED -> {
            val host = url.toHttpUrlOrNull()?.host?.lowercase()
            host != null && domains.any { host == it || host.endsWith(".$it") }
        }
    }

    /**
     * Returns a resolver for [mode], or null when redirect following is off entirely.
     *
     * [onFailure] fires only when a fetch was actually attempted and came back empty, which is
     * what distinguishes "the network let us down" from the far more common "this host isn't
     * one we follow" — both of which return null to the caller.
     *
     * @param onFetch fires just before a network call, so callers can show progress.
     * @param clean applied to every URL in the chain — pass the filter rules in here.
     */
    fun forMode(
        mode: AggressiveMode,
        domains: Set<String>,
        onFailure: () -> Unit = {},
        onFetch: () -> Unit = {},
        clean: (String) -> String = { it },
    ): ((String) -> String?)? {
        if (mode == AggressiveMode.OFF) return null
        if (mode == AggressiveMode.SELECTED && domains.isEmpty()) return null
        return { url ->
            if (appliesTo(mode, domains, url)) {
                onFetch()
                // The same gate guards every hop: under SELECTED the dive ends the moment it
                // lands off the list, under ALL it runs to the real destination.
                resolve(
                    url = url,
                    shouldFollow = { appliesTo(mode, domains, it) },
                    clean = clean,
                ).also { if (it == null) onFailure() }
            } else {
                null
            }
        }
    }
}

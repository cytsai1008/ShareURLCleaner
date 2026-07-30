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

    /** Blocking — call from [kotlinx.coroutines.Dispatchers.IO]. Returns null on any failure. */
    fun resolve(url: String): String? = try {
        // HEAD avoids downloading the body; some hosts only redirect on GET, so fall back
        // when HEAD lands us nowhere new.
        val viaHead = finalUrl(Request.Builder().url(url).head().build())
        if (viaHead != null && viaHead != url) {
            viaHead
        } else {
            finalUrl(Request.Builder().url(url).build())
        }
    } catch (_: Exception) {
        null
    }

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
     */
    fun forMode(
        mode: AggressiveMode,
        domains: Set<String>,
        onFailure: () -> Unit = {},
        onFetch: () -> Unit = {},
    ): ((String) -> String?)? {
        if (mode == AggressiveMode.OFF) return null
        if (mode == AggressiveMode.SELECTED && domains.isEmpty()) return null
        return { url ->
            if (appliesTo(mode, domains, url)) {
                onFetch()
                resolve(url).also { if (it == null) onFailure() }
            } else {
                null
            }
        }
    }
}

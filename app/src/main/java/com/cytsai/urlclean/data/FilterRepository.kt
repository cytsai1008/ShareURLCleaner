package com.cytsai.urlclean.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class FilterRule(val domain: String?, val param: String)

class FilterRepository(private val context: Context) {

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private const val RULES_FILE = "filter_rules.txt"
        private const val RULES_TMP = "filter_rules.tmp"
    }

    suspend fun downloadAndUpdate(url: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }

            val rules = body.lineSequence()
                .mapNotNull { parseLine(it) }
                .toList()

            val tmpFile = File(context.filesDir, RULES_TMP)
            tmpFile.bufferedWriter().use { writer ->
                rules.forEach { rule ->
                    if (rule.domain != null) {
                        writer.write("${rule.domain}\t${rule.param}\n")
                    } else {
                        writer.write("${rule.param}\n")
                    }
                }
            }
            tmpFile.renameTo(File(context.filesDir, RULES_FILE))

            Result.success(rules.size)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loadRules(): List<FilterRule> {
        val file = File(context.filesDir, RULES_FILE)
        if (!file.exists()) return emptyList()
        return file.bufferedReader().lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                when (parts.size) {
                    1 -> if (parts[0].isNotBlank()) FilterRule(null, parts[0]) else null
                    2 -> FilterRule(parts[0], parts[1])
                    else -> null
                }
            }
            .toList()
    }

    private fun parseLine(line: String): FilterRule? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('!') || trimmed.startsWith("@@")) return null

        val removeParamIndex = trimmed.indexOf("\$removeparam=")
        if (removeParamIndex == -1) return null

        val param = trimmed.substring(removeParamIndex + "\$removeparam=".length)
            .substringBefore(',')
            .trim()
        if (param.isEmpty()) return null

        // Domain-scoped: ||example.com^$removeparam=...
        return if (trimmed.startsWith("||")) {
            val domainPart = trimmed.substring(2).substringBefore('^')
            val domain = domainPart.substringBefore('/').lowercase()
            FilterRule(domain, param)
        } else {
            FilterRule(null, param)
        }
    }
}

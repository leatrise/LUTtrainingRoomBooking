package com.trainingroom.book

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val WEIXINLIB_USERCENTER_URL = "https://weixinlib.lut.edu.cn/usercenter"
private const val WEIXINLIB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"

suspend fun validateWeixinlibCookieLogin(
    rawCookieHeader: String
): UserCenterFetchResult = withContext(Dispatchers.IO) {
    runCatching {
        val normalizedCookieHeader = rawCookieHeader
            .removePrefix("Cookie:")
            .removePrefix("cookie:")
            .trim()
        val connection = (URL(WEIXINLIB_USERCENTER_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", WEIXINLIB_USER_AGENT)
            setRequestProperty("Referer", "https://weixinlib.lut.edu.cn/")
            setRequestProperty("Cookie", normalizedCookieHeader)
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val finalUrl = connection.url.toString()
        val profile = parseWeixinlibUserCenterProfile(body)
        when {
            profile != null -> UserCenterFetchResult(profile = profile)
            "cas/login" in finalUrl || "统一身份认证" in body -> {
                UserCenterFetchResult(message = "这组 Cookie 已失效或不完整，请重新从 usercenter 页面复制")
            }
            else -> UserCenterFetchResult(message = "Cookie 已提交，但未解析到 usercenter 用户名")
        }
    }.getOrElse { error ->
        UserCenterFetchResult(message = error.message ?: "Cookie 校验失败")
    }
}

private fun parseWeixinlibUserCenterProfile(html: String): UserCenterProfile? {
    val rawUsername = listOf(
        Regex("""<p[^>]*>\s*欢迎您:([^<]+)<a\s+href="unlogin">"""),
        Regex("""id="username"[^>]*value="([^"]+)"""")
    ).asSequence()
        .mapNotNull { regex -> regex.find(html)?.groupValues?.getOrNull(1)?.trim() }
        .firstOrNull()
        ?: return null

    val username = rawUsername.removePrefix("欢迎您:").trim()
    if (username.isBlank()) return null

    fun readValue(fieldId: String): String? =
        Regex("""id="$fieldId"[^>]*value="([^"]*)"""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.ifBlank { null }

    return UserCenterProfile(
        username = username,
        userCode = readValue("usercode"),
        userUnit = readValue("userunit"),
        userType = readValue("usertype")
    )
}

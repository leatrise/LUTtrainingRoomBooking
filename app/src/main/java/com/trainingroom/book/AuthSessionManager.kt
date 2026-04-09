package com.trainingroom.book

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

object AuthSessionManager {
    private const val PREFS_NAME = "auth_session"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_LAST_USER_CENTER_URL = "last_user_center_url"
    private const val KEY_WEIXINLIB_COOKIE_HEADER = "weixinlib_cookie_header"
    private const val KEY_LOGIN_SOURCE = "login_source"
    private const val WEIXINLIB_DOMAIN = "weixinlib.lut.edu.cn"

    const val LOGIN_SOURCE_SSO = "sso"
    const val LOGIN_SOURCE_COOKIE = "cookie"

    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private var installed = false

    fun install(context: Context) {
        if (!installed) {
            CookieHandler.setDefault(cookieManager)
            installed = true
            restoreCookies(context)
        }
    }

    fun markLoggedIn(
        context: Context,
        userCenterUrl: String,
        loginSource: String = LOGIN_SOURCE_SSO
    ) {
        persistCookies(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_LAST_USER_CENTER_URL, userCenterUrl)
            .putString(KEY_LOGIN_SOURCE, loginSource)
            .apply()
        if (loginSource != LOGIN_SOURCE_COOKIE) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_WEIXINLIB_COOKIE_HEADER)
                .apply()
        }
    }

    fun loginSource(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOGIN_SOURCE, null)

    fun setLoggedInState(context: Context, isLoggedIn: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_LOGGED_IN, false)

    fun isSsoLogin(context: Context): Boolean =
        loginSource(context) == LOGIN_SOURCE_SSO

    fun isCookieLogin(context: Context): Boolean =
        loginSource(context) == LOGIN_SOURCE_COOKIE

    fun clearWeixinlibCookieHeader(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_WEIXINLIB_COOKIE_HEADER)
            .apply()
    }

    fun lastUserCenterUrl(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_USER_CENTER_URL, null)

    fun clear(context: Context) {
        cookieManager.cookieStore.removeAll()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_COOKIES)
            .remove(KEY_IS_LOGGED_IN)
            .remove(KEY_LAST_USER_CENTER_URL)
            .remove(KEY_WEIXINLIB_COOKIE_HEADER)
            .remove(KEY_LOGIN_SOURCE)
            .apply()
    }

    fun importWeixinlibCookies(context: Context, rawCookieHeader: String): Int {
        install(context)
        val normalizedHeader = rawCookieHeader
            .removePrefix("Cookie:")
            .removePrefix("cookie:")
            .trim()
        if (normalizedHeader.isBlank()) return 0

        clear(context)
        val targetUri = URI("https://$WEIXINLIB_DOMAIN/")
        val importedCookies = normalizedHeader.split(";")
            .mapNotNull { segment ->
                val token = segment.trim()
                if (token.isBlank()) return@mapNotNull null
                val separatorIndex = token.indexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null
                val name = token.substring(0, separatorIndex).trim()
                val value = token.substring(separatorIndex + 1).trim()
                if (name.isBlank() || value.isBlank()) return@mapNotNull null
                HttpCookie(name, value).apply {
                    domain = WEIXINLIB_DOMAIN
                    path = "/"
                    secure = true
                }
            }

        importedCookies.forEach { cookie ->
            cookieManager.cookieStore.add(targetUri, cookie)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEIXINLIB_COOKIE_HEADER, normalizedHeader)
            .putString(KEY_LOGIN_SOURCE, LOGIN_SOURCE_COOKIE)
            .apply()
        persistCookies(context)
        return importedCookies.size
    }

    fun weixinlibCookieHeader(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WEIXINLIB_COOKIE_HEADER, null)
            ?.trim()
            ?.ifBlank { null }

    fun persistCookies(context: Context) {
        val cookiesJson = JSONArray()
        cookieManager.cookieStore.cookies
            .filterNot { it.hasExpired() }
            .forEach { cookie ->
                val obj = JSONObject()
                obj.put("name", cookie.name)
                obj.put("value", cookie.value)
                obj.put("domain", cookie.domain.orEmpty())
                obj.put("path", cookie.path.orEmpty())
                obj.put("maxAge", cookie.maxAge)
                obj.put("secure", cookie.secure)
                obj.put("httpOnly", cookie.isHttpOnly)
                cookiesJson.put(obj)
            }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COOKIES, cookiesJson.toString())
            .apply()
    }

    fun debugCookieSnapshot(): String {
        val cookies = cookieManager.cookieStore.cookies.filterNot { it.hasExpired() }
        if (cookies.isEmpty()) {
            return "<empty>"
        }
        return cookies.joinToString(separator = "\n") { cookie ->
            buildString {
                append(cookie.name)
                append("=")
                append(cookie.value)
                append("; domain=")
                append(cookie.domain.orEmpty())
                append("; path=")
                append(cookie.path.orEmpty().ifBlank { "/" })
                append("; secure=")
                append(cookie.secure)
                append("; httpOnly=")
                append(cookie.isHttpOnly)
            }
        }
    }

    private fun restoreCookies(context: Context) {
        if (cookieManager.cookieStore.cookies.isNotEmpty()) return
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COOKIES, null)
            ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val value = obj.optString("value")
                if (name.isBlank()) continue
                val cookie = HttpCookie(name, value).apply {
                    val restoredDomain = obj.optString("domain")
                    if (restoredDomain.isNotBlank()) {
                        domain = restoredDomain
                    }
                    path = obj.optString("path").ifBlank { "/" }
                    secure = obj.optBoolean("secure", false)
                    setHttpOnly(obj.optBoolean("httpOnly", false))
                }
                val domain = cookie.domain?.trimStart('.') ?: continue
                val uri = URI("https://$domain")
                cookieManager.cookieStore.add(uri, cookie)
            }
        }
    }
}

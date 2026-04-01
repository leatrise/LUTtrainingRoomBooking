package com.trainingroom.book

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher

data class SsoLoginResult(
    val success: Boolean,
    val message: String,
    val userCenterUrl: String? = null
)

private data class HttpResult(
    val url: String,
    val code: Int,
    val body: String,
    val location: String?
)

private data class MfaDetectResult(
    val state: String,
    val canContinue: Boolean,
    val message: String? = null
)

object SsoLoginService {
    private const val TAG = "SsoLoginService"
    const val DEFAULT_SSO_LOGIN_URL =
        "https://cas-paas.lut.edu.cn/cas/login?service=https:%2F%2Flib.lut.edu.cn%2Fcas%2Fcas%2Flogin%3Forgcode%3Dlut%26service%3Dhttps%253A%252F%252Fweixinlib.lut.edu.cn%252Flogin%26oauth_provider%3DthirdProvider"

    private const val PUBLIC_KEY_URL = "https://cas-paas.lut.edu.cn/cas/jwt/publicKey"
    private const val MFA_DETECT_URL = "https://cas-paas.lut.edu.cn/cas/mfa/detect"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"
    private const val PREFS_NAME = "sso_login"
    private const val KEY_FP_VISITOR_ID = "fp_visitor_id"

    suspend fun login(
        context: Context,
        username: String,
        password: String,
        loginUrl: String = DEFAULT_SSO_LOGIN_URL
    ): SsoLoginResult = withContext(Dispatchers.IO) {
        AuthSessionManager.install(context)
        if (username.isBlank() || password.isBlank()) {
            return@withContext SsoLoginResult(false, "账号和密码不能为空")
        }

        runCatching {
            Log.d(TAG, "开始 SSO 登录, username=$username")
            val loginPage = httpGet(loginUrl)
            val execution = extractExecution(loginPage.body)
                ?: return@runCatching SsoLoginResult(false, "未能解析统一认证 execution 参数")

            val publicKey = parsePublicKey(httpGet(PUBLIC_KEY_URL).body)
            val encryptedPassword = "__RSA__" + rsaEncrypt(password, publicKey)
            val fpVisitorId = getOrCreateFpVisitorId(context)
            val mfaDetectResult = detectMfaState(
                username = username,
                encryptedPassword = encryptedPassword,
                fpVisitorId = fpVisitorId,
                referer = loginUrl
            )
            if (!mfaDetectResult.canContinue) {
                return@runCatching SsoLoginResult(
                    success = false,
                    message = mfaDetectResult.message ?: "统一身份认证登录失败"
                )
            }

            val form = linkedMapOf(
                "username" to username,
                "password" to encryptedPassword,
                "captcha" to "",
                "currentMenu" to "1",
                "failN" to "0",
                "mfaState" to mfaDetectResult.state,
                "execution" to execution,
                "_eventId" to "submit",
                "geolocation" to "",
                "fpVisitorId" to fpVisitorId,
                "trustAgent" to "",
                "submit1" to "Login1"
            )

            val submitResult = httpPostForm(
                url = loginUrl,
                form = form,
                referer = loginUrl
            )

            if (submitResult.code !in 300..399) {
                val extractedMessage = extractErrorMessage(submitResult.body)
                Log.w(
                    TAG,
                    "登录提交未返回重定向, code=${submitResult.code}, extractedMessage=${extractedMessage ?: "null"}"
                )
                return@runCatching SsoLoginResult(
                    success = false,
                    message = extractedMessage
                        ?: decodeUnicodeEscapes("统一身份认证登录失败，请检查账号或密码")
                )
            }

            val finalResult = followRedirects(submitResult, maxSteps = 8)
            val finalUrl = finalResult.url
            Log.d(TAG, "登录跳转结束, code=${finalResult.code}, finalUrl=$finalUrl")
            if (finalResult.code == 200 && "/usercenter" in finalUrl) {
                AuthSessionManager.markLoggedIn(context, finalUrl)
                Log.d(TAG, "登录成功后的 Cookie 快照:\n${AuthSessionManager.debugCookieSnapshot()}")
                SsoLoginResult(
                    success = true,
                    message = "统一身份认证登录成功",
                    userCenterUrl = finalUrl
                )
            } else {
                SsoLoginResult(
                    success = false,
                    message = "登录流程未完整到达用户中心，当前停留在: $finalUrl"
                )
            }
        }.getOrElse { error ->
            Log.e(TAG, "SSO 登录异常: ${error.message}", error)
            SsoLoginResult(
                success = false,
                message = decodeUnicodeEscapes(error.message ?: "统一身份认证登录失败")
            )
        }
    }

    suspend fun trySilentRefresh(
        context: Context,
        loginUrl: String = DEFAULT_SSO_LOGIN_URL
    ): SsoLoginResult = withContext(Dispatchers.IO) {
        AuthSessionManager.install(context)
        runCatching {
            Log.d(TAG, "开始静默续登")
            val entryResult = httpGet(
                url = loginUrl,
                referer = "https://weixinlib.lut.edu.cn/"
            )
            val finalResult = if (entryResult.location != null) {
                followRedirects(entryResult, maxSteps = 8)
            } else {
                entryResult
            }
            val finalUrl = finalResult.url
            Log.d(TAG, "静默续登结束, code=${finalResult.code}, finalUrl=$finalUrl")
            when {
                finalResult.code == 200 && "/usercenter" in finalUrl -> {
                    AuthSessionManager.clearWeixinlibCookieHeader(context)
                    AuthSessionManager.markLoggedIn(
                        context = context,
                        userCenterUrl = finalUrl,
                        loginSource = AuthSessionManager.LOGIN_SOURCE_SSO
                    )
                    SsoLoginResult(
                        success = true,
                        message = "已使用统一认证会话恢复图书馆登录态",
                        userCenterUrl = finalUrl
                    )
                }
                "cas/login" in finalUrl || "统一身份认证" in finalResult.body -> {
                    SsoLoginResult(
                        success = false,
                        message = "统一认证主登录态已失效，请重新登录"
                    )
                }
                else -> {
                    SsoLoginResult(
                        success = false,
                        message = "静默续登未到达用户中心，当前停留在: $finalUrl"
                    )
                }
            }
        }.getOrElse { error ->
            Log.e(TAG, "静默续登异常: ${error.message}", error)
            SsoLoginResult(
                success = false,
                message = decodeUnicodeEscapes(error.message ?: "静默续登失败")
            )
        }
    }

    private fun followRedirects(initial: HttpResult, maxSteps: Int): HttpResult {
        var current = initial
        repeat(maxSteps) {
            val nextUrl = current.location ?: return current
            Log.d(TAG, "跟随重定向 step=${it + 1}, from=${current.url}, to=$nextUrl")
            current = httpGet(nextUrl, referer = current.url)
            if (current.location == null) return current
        }
        return current
    }

    private fun httpGet(url: String, referer: String? = null): HttpResult {
        Log.d(TAG, "HTTP GET url=$url referer=${referer ?: ""}")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            referer?.let { setRequestProperty("Referer", it) }
        }
        val code = connection.responseCode
        val result = HttpResult(
            url = url,
            code = code,
            body = readBody(connection),
            location = resolveUrl(url, connection.getHeaderField("Location"))
        )
        logHttpResult(
            method = "GET",
            url = url,
            referer = referer,
            result = result
        )
        return result
    }

    private fun httpPostForm(
        url: String,
        form: Map<String, String>,
        referer: String,
        headers: Map<String, String> = emptyMap()
    ): HttpResult {
        val body = form.entries.joinToString("&") { (key, value) ->
            encode(key) + "=" + encode(value)
        }
        Log.d(TAG, "HTTP POST url=$url referer=$referer")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Origin", "https://cas-paas.lut.edu.cn")
            setRequestProperty("Referer", referer)
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        connection.outputStream.use { output ->
            output.write(body.toByteArray(StandardCharsets.UTF_8))
        }
        val code = connection.responseCode
        val result = HttpResult(
            url = url,
            code = code,
            body = readBody(connection),
            location = resolveUrl(url, connection.getHeaderField("Location"))
        )
        logHttpResult(
            method = "POST",
            url = url,
            referer = referer,
            result = result
        )
        return result
    }

    private fun readBody(connection: HttpURLConnection): String {
        val stream = connection.errorStream ?: runCatching { connection.inputStream }.getOrNull()
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun resolveUrl(baseUrl: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        return if (location.startsWith("http://") || location.startsWith("https://")) {
            location
        } else {
            URL(URL(baseUrl), location).toString()
        }
    }

    private fun extractExecution(html: String): String? {
        val formMatch = Regex(
            """<form method="post" id="fm1".*?name="execution" value="([^"]+)"""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)
        return formMatch?.groupValues?.getOrNull(1)
    }

    private fun extractErrorMessage(html: String): String? {
        extractJsonMessage(html)?.let { return it }

        val candidates = listOf(
            Regex("""<div[^>]*class="[^"]*(?:error-text|alert-danger|el-message__content|login-error)[^"]*"[^>]*>([^<]{2,120})"""),
            Regex("""<span[^>]*class="[^"]*(?:error-text|alert-danger|el-message__content|login-error)[^"]*"[^>]*>([^<]{2,120})"""),
            Regex("""errors\s*:\s*\[\s*["']([^"']{2,120})["']""")
        )

        return candidates.asSequence()
            .mapNotNull { regex -> regex.find(html)?.groupValues?.getOrNull(1) }
            .map { decodeUnicodeEscapes(it).trim() }
            .map { stripHtml(it) }
            .map { it.removePrefix("[").removeSuffix("]").trim() }
            .firstOrNull { isMeaningfulError(it) }
    }

    private fun detectMfaState(
        username: String,
        encryptedPassword: String,
        fpVisitorId: String,
        referer: String
    ): MfaDetectResult {
        val response = runCatching {
            httpPostForm(
                url = MFA_DETECT_URL,
                form = mapOf(
                    "username" to username,
                    "password" to encryptedPassword,
                    "fpVisitorId" to fpVisitorId
                ),
                referer = referer,
                headers = mapOf(
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            )
        }.getOrElse {
            return MfaDetectResult(state = "", canContinue = true)
        }

        if (response.code !in 200..299 || response.body.isBlank()) {
            return MfaDetectResult(state = "", canContinue = true)
        }

        val json = runCatching { JSONObject(response.body) }.getOrNull()
            ?: return MfaDetectResult(state = "", canContinue = true)

        if (json.optInt("code", -1) != 0) {
            val message = extractJsonMessage(json.toString())
            return if (message != null) {
                MfaDetectResult(state = "", canContinue = false, message = message)
            } else {
                MfaDetectResult(state = "", canContinue = true)
            }
        }

        val data = json.optJSONObject("data")
        val mfaState = data?.optString("state").orEmpty()
        val needMfa = data?.optBoolean("need", false) ?: false
        if (needMfa) {
            return MfaDetectResult(
                state = mfaState,
                canContinue = false,
                message = "当前账号启用了二次验证，App 暂未支持该验证方式"
            )
        }
        return MfaDetectResult(state = mfaState, canContinue = true)
    }

    private fun getOrCreateFpVisitorId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_FP_VISITOR_ID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) {
            return existing
        }
        val generated = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(KEY_FP_VISITOR_ID, generated).apply()
        return generated
    }

    private fun extractJsonMessage(raw: String): String? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val candidates = sequenceOf(
            json.optString("msg"),
            json.optString("message"),
            json.optString("error_description"),
            json.optString("errorMessage"),
            json.optJSONObject("data")?.optString("msg").orEmpty(),
            json.optJSONObject("data")?.optString("message").orEmpty()
        )
        return candidates
            .map { decodeUnicodeEscapes(it).trim() }
            .firstOrNull { isMeaningfulError(it) }
    }

    private fun stripHtml(input: String): String =
        input.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun isMeaningfulError(message: String): Boolean {
        if (message.isBlank()) return false
        if (message.length <= 1) return false
        if ("{{" in message || "}}" in message) return false
        if (Regex("""[A-Za-z][A-Za-z0-9]+AlertMessage""").containsMatchIn(message)) return false
        val ignoredMessages = setOf(
            "请输入用户名",
            "请输入教工号/学号/安全手机号/证件号码",
            "请输入登录密码",
            "请输入图片验证码",
            "请输入手机号",
            "请输入动态密码"
        )
        return message !in ignoredMessages
    }

    private fun logHttpResult(
        method: String,
        url: String,
        referer: String?,
        result: HttpResult
    ) {
        Log.d(
            TAG,
            buildString {
                append("HTTP ")
                append(method)
                append(" completed")
                append(", url=")
                append(url)
                append(", code=")
                append(result.code)
                append(", location=")
                append(result.location ?: "")
                append(", referer=")
                append(referer ?: "")
            }
        )
    }

    private fun decodeUnicodeEscapes(input: String): String {
        val output = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val current = input[index]
            if (current == '\\' && index + 5 < input.length && input[index + 1] == 'u') {
                val hex = input.substring(index + 2, index + 6)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    output.append(decoded.toChar())
                    index += 6
                    continue
                }
            }
            output.append(current)
            index += 1
        }
        return output.toString()
    }

    private fun parsePublicKey(pem: String): PublicKey {
        val normalized = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.getDecoder().decode(normalized)
        val keySpec = X509EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    private fun rsaEncrypt(password: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

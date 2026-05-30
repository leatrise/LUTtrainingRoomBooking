package com.trainingroom.book

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.parcelize.Parcelize
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.trainingroom.book.ui.theme.MyApplicationTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.install(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen()
                }
            }
        }
    }
}

// 研讨室数据类
@Parcelize
data class ConferenceRoom(
    val id: String,
    val name: String,
    val minCapacity: Int,
    val maxCapacity: Int,
    val location: String,
    val floor: String,
    val inUse: Boolean,
    val status: String,
    val imageUrl: String? = null
): Parcelable

fun ConferenceRoom.shortLabel(): String {
    val patterns = listOf(
        Regex("""[A-Z]{1,2}\d{3}"""),
        Regex("""\d{3}""")
    )
    val match = patterns.asSequence().mapNotNull { it.find(name)?.value }.firstOrNull()
    return match ?: name.split(" ").lastOrNull() ?: id
}

private enum class CapacityOperator {
    LT,
    GT,
    GTE,
    LTE,
    EQ
}

private sealed interface QueryFilter {
    fun matches(room: ConferenceRoom): Boolean
}

private data class FloorQueryFilter(val floorDigit: Char) : QueryFilter {
    override fun matches(room: ConferenceRoom): Boolean =
        room.roomNumber()?.startsWith(floorDigit) == true
}

private data class RoomNumberQueryFilter(val roomNumber: String) : QueryFilter {
    override fun matches(room: ConferenceRoom): Boolean = room.roomNumber() == roomNumber
}

private data class SearchTextQueryFilter(val keyword: String) : QueryFilter {
    override fun matches(room: ConferenceRoom): Boolean {
        val normalizedKeyword = keyword.uppercase()
        val candidates = listOfNotNull(
            room.shortLabel(),
            room.roomNumber(),
            room.name
        ).map { it.uppercase() }
        return candidates.any { normalizedKeyword in it }
    }
}

private data class CapacityQueryFilter(
    val operator: CapacityOperator,
    val peopleCount: Int
) : QueryFilter {
    override fun matches(room: ConferenceRoom): Boolean =
        room.matchesCapacity(operator = operator, peopleCount = peopleCount)
}

private data class CapacityFilterDraft(
    val operatorSymbol: String,
    val peopleCountText: String
)

private data class AdvancedOperatorOption(
    val label: String,
    val symbol: String
)

private val capacityFilterRegex = Regex("""(<=|>=|≤|≥|=|<|>)\s*(\d+)""")
private val singleDigitFloorRegex = Regex("""\d""")
private val threeDigitRoomRegex = Regex("""\d{3}""")
private val alphaNumericFilterRegex = Regex("""[A-Za-z0-9]+""")
private val advancedOperatorOptions = listOf(
    AdvancedOperatorOption("大于", ">"),
    AdvancedOperatorOption("小于", "<"),
    AdvancedOperatorOption("等于", "="),
    AdvancedOperatorOption("大于等于", "≥"),
    AdvancedOperatorOption("小于等于", "≤")
)

private fun advancedOperatorLabel(symbol: String): String =
    advancedOperatorOptions.firstOrNull { it.symbol == symbol }?.label ?: symbol

private fun splitFilterTokens(input: String): List<String> =
    input.split(",").map { it.trim() }.filter { it.isNotEmpty() }

private fun parseSingleQueryFilter(input: String): QueryFilter? {
    val normalized = input.trim()
    if (normalized.isEmpty()) return null
    if (singleDigitFloorRegex.matches(normalized)) {
        return FloorQueryFilter(normalized.first())
    }
    if (threeDigitRoomRegex.matches(normalized)) {
        return RoomNumberQueryFilter(normalized)
    }
    val match = capacityFilterRegex.matchEntire(normalized) ?: return null
    val operator = when (match.groupValues[1]) {
        "<" -> CapacityOperator.LT
        ">" -> CapacityOperator.GT
        ">=", "≥" -> CapacityOperator.GTE
        "<=", "≤" -> CapacityOperator.LTE
        "=" -> CapacityOperator.EQ
        else -> return null
    }
    return CapacityQueryFilter(
        operator = operator,
        peopleCount = match.groupValues[2].toInt()
    )
}

private fun parseSingleFreeTextQueryFilter(input: String): QueryFilter? {
    val normalized = input.trim()
    if (normalized.isEmpty()) return null
    if (!alphaNumericFilterRegex.matches(normalized)) return null
    return SearchTextQueryFilter(normalized)
}

private fun parseQueryFilters(input: String): List<QueryFilter>? {
    val tokens = splitFilterTokens(input)
    if (tokens.isEmpty()) return emptyList()
    return tokens.map { token ->
        parseSingleQueryFilter(token) ?: parseSingleFreeTextQueryFilter(token) ?: return null
    }
}

private fun readCapacityFilterDraft(input: String): CapacityFilterDraft? {
    val match = splitFilterTokens(input)
        .asSequence()
        .mapNotNull { token -> capacityFilterRegex.matchEntire(token) }
        .firstOrNull() ?: return null
    val operatorSymbol = when (match.groupValues[1]) {
        "<=" , "≤" -> "≤"
        ">=", "≥" -> "≥"
        "=" -> "="
        "<" -> "<"
        ">" -> ">"
        else -> return null
    }
    return CapacityFilterDraft(
        operatorSymbol = operatorSymbol,
        peopleCountText = match.groupValues[2]
    )
}

private fun readTextFilterDraft(input: String): String? =
    splitFilterTokens(input)
        .firstOrNull { token -> !capacityFilterRegex.matches(token) && alphaNumericFilterRegex.matches(token) }

private fun mergeCapacityFilter(input: String, operatorSymbol: String, peopleCountText: String): String {
    val tokens = splitFilterTokens(input).toMutableList()
    val capacityIndex = tokens.indexOfFirst { capacityFilterRegex.matches(it) }
    val normalizedPeopleCountText = peopleCountText.trim()
    if (normalizedPeopleCountText.isEmpty()) {
        if (capacityIndex >= 0) {
            tokens.removeAt(capacityIndex)
        }
        return tokens.joinToString(",")
    }
    val normalizedCapacityFilter = operatorSymbol + normalizedPeopleCountText
    if (capacityIndex >= 0) {
        tokens[capacityIndex] = normalizedCapacityFilter
    } else {
        tokens += normalizedCapacityFilter
    }
    return tokens.joinToString(",")
}

private fun mergeTextFilter(input: String, text: String): String {
    val tokens = splitFilterTokens(input).toMutableList()
    val textIndex = tokens.indexOfFirst { token ->
        !capacityFilterRegex.matches(token) && alphaNumericFilterRegex.matches(token)
    }
    val normalizedText = text.trim()
    if (normalizedText.isEmpty()) {
        if (textIndex >= 0) {
            tokens.removeAt(textIndex)
        }
        return tokens.joinToString(",")
    }
    if (textIndex >= 0) {
        tokens[textIndex] = normalizedText
    } else {
        tokens.add(0, normalizedText)
    }
    return tokens.joinToString(",")
}

private fun ConferenceRoom.roomNumber(): String? {
    val fromShortLabel = Regex("""\d{3}""").find(shortLabel())?.value
    return fromShortLabel ?: Regex("""\d{3}""").find(name)?.value
}

private fun ConferenceRoom.matchesCapacity(
    operator: CapacityOperator,
    peopleCount: Int
): Boolean {
    return when (operator) {
        CapacityOperator.LT -> minCapacity < peopleCount
        CapacityOperator.GT -> maxCapacity > peopleCount
        CapacityOperator.GTE -> maxCapacity >= peopleCount
        CapacityOperator.LTE -> minCapacity <= peopleCount
        CapacityOperator.EQ -> peopleCount in minCapacity..maxCapacity
    }
}

fun ConferenceRoom.campus(): String {
    val lower = location.lowercase()
    return when {
        lower.contains("彭") -> "彭家坪校区"
        lower.contains("兰") -> "兰工坪校区"
        else -> "兰工坪校区"
    }
}

private fun isRoomFree(
    room: ConferenceRoom,
    reservations: List<ReservationItem>,
    start: LocalDateTime,
    end: LocalDateTime,
    targetDate: LocalDate
): Boolean {
    if (end <= start) return false
    return reservations.none { item ->
        val s = item.startDateTime()
        val e = item.endDateTime()
        if (s == null || e == null) return@none false
        if (s.toLocalDate() != targetDate) return@none false
        !(e <= start || s >= end)
    }
}

private fun findTransitPlans(
    rooms: List<ConferenceRoom>,
    reservationResults: Map<String, List<ReservationItem>>,
    userStart: LocalDateTime,
    userEnd: LocalDateTime,
    campus: String,
    directFreeRoomIds: Set<String>
): List<TransitPlan> {
    if (userEnd <= userStart) return emptyList()
    val targetDate = userStart.toLocalDate()
    val sameCampusRooms = rooms.filter { (campus == "全部" || it.campus() == campus) && it.id !in directFreeRoomIds }
    if (sameCampusRooms.isEmpty()) return emptyList()

    val boundaries = mutableSetOf(userStart, userEnd)
    sameCampusRooms.forEach { room ->
        reservationResults[room.id].orEmpty().forEach { item ->
            val s = item.startDateTime()
            val e = item.endDateTime()
            if (s == null || e == null) return@forEach
            if (s.toLocalDate() != targetDate) return@forEach
            if (e <= userStart || s >= userEnd) return@forEach
            if (s in userStart..userEnd) boundaries += s
            if (e in userStart..userEnd) boundaries += e
        }
    }

    val sorted = boundaries.filter { it > userStart && it < userEnd }.sorted()
    val plans = mutableListOf<TransitPlan>()
    for (split in sorted) {
        val first = sameCampusRooms.firstOrNull { room ->
            val res = reservationResults[room.id].orEmpty()
            isRoomFree(room, res, userStart, split, targetDate)
        } ?: continue
        val second = sameCampusRooms.firstOrNull { room ->
            val res = reservationResults[room.id].orEmpty()
            room.id != first.id && isRoomFree(room, res, split, userEnd, targetDate)
        } ?: continue
        plans += TransitPlan(first = first, second = second, split = split)
        if (plans.size >= 3) break
    }
    return plans
}

data class ReservationItem(
    val id: String,
    val title: String,
    val startTime: String,
    val endTime: String
)

data class TransitPlan(
    val first: ConferenceRoom,
    val second: ConferenceRoom,
    val split: LocalDateTime
)

data class RoomFetchResult(
    val rooms: List<ConferenceRoom>,
    val rawJson: String?
)

data class UserCenterProfile(
    val username: String,
    val userCode: String? = null,
    val userUnit: String? = null,
    val userType: String? = null
)

data class UserCenterFetchResult(
    val profile: UserCenterProfile? = null,
    val message: String? = null
)

data class RoomsState(
    val categories: List<String>,
    val rooms: List<ConferenceRoom>,
    val offlineNotice: String?,
    val onRefresh: () -> Unit,
    val isRefreshing: Boolean
)

private const val USER_CENTER_URL = "https://weixinlib.lut.edu.cn/usercenter"
private const val USER_CENTER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"

fun parseConferenceRooms(json: String): List<ConferenceRoom> {
    return try {
        val array = JSONArray(json)
        val rooms = mutableListOf<ConferenceRoom>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val roomName = obj.optString("roomname", "未知房间")
                val roomNo = obj.optInt("roomno", i)
                val minUser = obj.optInt("minuser", 0)
                val maxUser = obj.optInt("maxuser", 0)
                val groupName = obj.optString("groupname", "未分类")
                val isUse = obj.optInt("isuse", 0) == 1
                val finishInt = obj.optInt("finishint", 0) == 1
                val imgUrl = obj.optString("imgUrl", "")
                val completeImageUrl = if (imgUrl.isNotEmpty()) {
                    "https://weixinlib.lut.edu.cn$imgUrl"
                } else null
                val status = when {
                    finishInt -> "暂停使用"
                    isUse -> "使用中"
                    else -> "空闲"
                }
                rooms += ConferenceRoom(
                    id = roomNo.toString(),
                    name = roomName,
                    minCapacity = minUser,
                    maxCapacity = maxUser,
                    location = groupName,
                    floor = groupName,
                    inUse = isUse,
                    status = status,
                    imageUrl = completeImageUrl
                )
            } catch (e: Exception) {
                Log.w("MainActivity", "跳过第 $i 个房间: ${e.message}")
            }
        }
        Log.d("MainActivity", "成功解析 ${rooms.size} 个房间")
        rooms
    } catch (e: Exception) {
        Log.e("MainActivity", "JSON 解析异常: ${e.message}", e)
        emptyList()
    }
}

suspend fun fetchConferenceRoomsFromWeb(): RoomFetchResult {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://weixinlib.lut.edu.cn/trainingroomnote?roomid=100")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val json = extractRoomJsonFromHtml(html)
            if (json != null) {
                val rooms = parseConferenceRooms(json)
                RoomFetchResult(rooms, json)
            } else {
                RoomFetchResult(emptyList(), null)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "网络获取房间列表失败: ${e.message}", e)
            RoomFetchResult(emptyList(), null)
        }
    }
}

suspend fun fetchUserCenterProfile(context: Context): UserCenterFetchResult {
    val firstAttempt = fetchUserCenterProfileOnce(context)
    if (firstAttempt.profile != null) {
        AuthSessionManager.setLoggedInState(context, true)
        AuthSessionManager.syncLoggedInUserInfo(
            userCode = firstAttempt.profile.userCode,
            username = firstAttempt.profile.username
        )
        return firstAttempt
    }

    val shouldTrySilentRefresh =
        firstAttempt.message == "当前登录态已失效，请重新登录" &&
            AuthSessionManager.isSsoLogin(context)
    if (!shouldTrySilentRefresh) {
        if (firstAttempt.message == "当前登录态已失效，请重新登录") {
            AuthSessionManager.setLoggedInState(context, false)
            AuthSessionManager.syncLoggedInUserInfo(userCode = null, username = null)
        }
        return firstAttempt
    }

    val renewResult = SsoLoginService.trySilentRefresh(context)
    if (!renewResult.success) {
        if (renewResult.message == "统一认证主登录态已失效，请重新登录") {
            AuthSessionManager.setLoggedInState(context, false)
        }
        return UserCenterFetchResult(message = renewResult.message)
    }
    val secondAttempt = fetchUserCenterProfileOnce(context)
    if (secondAttempt.profile != null) {
        AuthSessionManager.setLoggedInState(context, true)
        AuthSessionManager.syncLoggedInUserInfo(
            userCode = secondAttempt.profile.userCode,
            username = secondAttempt.profile.username
        )
    } else if (secondAttempt.message == "当前登录态已失效，请重新登录") {
        AuthSessionManager.setLoggedInState(context, false)
        AuthSessionManager.syncLoggedInUserInfo(userCode = null, username = null)
    }
    return secondAttempt
}

private suspend fun fetchUserCenterProfileOnce(context: Context): UserCenterFetchResult {
    return withContext(Dispatchers.IO) {
        AuthSessionManager.install(context)
        runCatching {
            val rawCookieHeader = AuthSessionManager.weixinlibCookieHeader(context)
            val connection = (URL(USER_CENTER_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_CENTER_USER_AGENT)
                rawCookieHeader?.let { setRequestProperty("Cookie", it) }
                setRequestProperty("Referer", "https://weixinlib.lut.edu.cn/")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val finalUrl = connection.url.toString()
            val profile = parseUserCenterProfile(body)
            when {
                profile != null -> UserCenterFetchResult(profile = profile)
                "cas/login" in finalUrl || "统一身份认证" in body -> {
                    UserCenterFetchResult(message = "当前登录态已失效，请重新登录")
                }
                else -> UserCenterFetchResult(message = "已请求 usercenter，但未解析到用户名")
            }
        }.getOrElse { error ->
            Log.e("MainActivity", "获取 usercenter 失败: ${error.message}", error)
            UserCenterFetchResult(message = error.message ?: "获取个人中心失败")
        }
    }
}

private fun parseUserCenterProfile(html: String): UserCenterProfile? {
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

fun extractRoomJsonFromHtml(html: String): String? {
    val marker = "var roomdata = "
    val start = html.indexOf(marker)
    if (start < 0) return null
    val remaining = html.substring(start + marker.length)
    val end = remaining.indexOf("];")
    if (end < 0) return null
    return remaining.substring(0, end + 1).trim()
}

fun loadLocalConferenceRooms(context: Context): List<ConferenceRoom> {
    return try {
        Log.d("MainActivity", "开始加载本地 roomdata.json...")
        val json = context.resources.openRawResource(R.raw.roomdata)
            .bufferedReader()
            .use { it.readText() }
        Log.d("MainActivity", "本地 JSON 加载成功，长度: ${json.length}")
        val rooms = parseConferenceRooms(json)
        Log.d("MainActivity", "本地解析成功，共 ${rooms.size} 个房间")
        rooms
    } catch (e: Exception) {
        Log.e("MainActivity", "本地加载或解析失败: ${e.message}", e)
        emptyList()
    }
}

private suspend fun saveRoomJsonToCache(context: Context, json: String) {
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, "room_cache.json")
            file.writeText(json)
            File(context.filesDir, "room_cache_meta.txt").writeText(System.currentTimeMillis().toString())
            Log.d("MainActivity", "房间数据已写入缓存: ${file.absolutePath}")
        }.onFailure { e ->
            Log.w("MainActivity", "写入缓存失败: ${e.message}")
        }
    }
}

private fun loadRoomJsonFromCache(context: Context): String? {
    return try {
        val file = File(context.filesDir, "room_cache.json")
        if (file.exists()) {
            val txt = file.readText()
            Log.d("MainActivity", "从缓存读取房间数据，长度: ${txt.length}")
            txt
        } else null
    } catch (e: Exception) {
        Log.w("MainActivity", "读取缓存失败: ${e.message}")
        null
    }
}

private fun loadRoomCacheTimestamp(context: Context): String? {
    return try {
        val meta = File(context.filesDir, "room_cache_meta.txt")
        if (meta.exists()) meta.readText() else null
    } catch (e: Exception) {
        null
    }
}

private fun loadReservationsCacheTimestamp(context: Context): String? {
    return try {
        val meta = File(context.filesDir, "reservations_cache_meta.txt")
        if (meta.exists()) meta.readText() else null
    } catch (e: Exception) {
        null
    }
}

private fun formatCacheTimestamp(raw: String?): String? {
    val millis = raw?.trim()?.toLongOrNull() ?: return null
    return runCatching {
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M/d HH:mm", Locale.getDefault()))
    }.getOrNull()
}

private const val CACHE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
private const val CACHE_WARN_AGE_MILLIS = 2 * 60 * 60 * 1000L
private const val CACHE_DANGER_AGE_MILLIS = 12 * 60 * 60 * 1000L

private fun isCacheExpired(raw: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val savedAt = raw?.trim()?.toLongOrNull() ?: return true
    return nowMillis - savedAt > CACHE_MAX_AGE_MILLIS
}

private fun cacheAgeMillis(raw: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
    val savedAt = raw?.trim()?.toLongOrNull() ?: return null
    val age = nowMillis - savedAt
    return if (age >= 0) age else null
}

private fun formatCacheAgeText(ageMillis: Long?): String? {
    val age = ageMillis ?: return null
    return when {
        age < 60_000L -> "刚刚获取"
        age < 60 * 60 * 1000L -> "${age / 60_000L}分钟前"
        else -> "${age / (60 * 60 * 1000L)}小时前"
    }
}

private fun buildReservationsCacheStatus(
    prefix: String,
    count: Int,
    cacheAgeText: String?,
    cacheTimeText: String?
): String {
    return buildString {
        append(prefix)
        append('·')
        append(count)
        append("个")
        cacheAgeText?.let {
            append('·')
            append(it)
        }
        cacheTimeText?.let {
            if (cacheAgeText != null) {
                append(' ')
            } else {
                append('·')
            }
            append('(')
            append(it)
            append(')')
        }
    }
}

private fun refreshReservationsCacheStatus(
    count: Int,
    savedAtMillis: Long?,
    cacheTimeText: String?
): Pair<Long?, String> {
    val ageMillis = savedAtMillis?.let { savedAt ->
        (System.currentTimeMillis() - savedAt).coerceAtLeast(0L)
    }
    return ageMillis to buildReservationsCacheStatus(
        prefix = "已加载缓存",
        count = count,
        cacheAgeText = formatCacheAgeText(ageMillis),
        cacheTimeText = cacheTimeText
    )
}

private suspend fun saveReservationsCache(
    context: Context,
    data: Map<String, List<ReservationItem>>
) {
    withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
            data.forEach { (roomId, list) ->
                val arr = JSONArray()
                list.forEach { item ->
                    val obj = JSONObject()
                    obj.put("id", item.id)
                    obj.put("title", item.title)
                    obj.put("start", item.startTime)
                    obj.put("end", item.endTime)
                    arr.put(obj)
                }
                root.put(roomId, arr)
            }
            File(context.filesDir, "reservations_cache.json").writeText(root.toString())
            File(context.filesDir, "reservations_cache_meta.txt").writeText(System.currentTimeMillis().toString())
            Log.d("MainActivity", "已缓存预约信息: ${data.size} 间房间")
        }.onFailure { e ->
            Log.w("MainActivity", "缓存预约信息失败: ${e.message}")
        }
    }
}

private suspend fun loadReservationsCache(context: Context): Map<String, List<ReservationItem>> {
    return withContext(Dispatchers.IO) {
        try {
            if (isCacheExpired(loadReservationsCacheTimestamp(context))) {
                Log.w("MainActivity", "预约缓存已超过24小时，自动丢弃")
                return@withContext emptyMap()
            }
            val file = File(context.filesDir, "reservations_cache.json")
            if (!file.exists()) return@withContext emptyMap()
            val json = file.readText()
            val root = JSONObject(json)
            val result = mutableMapOf<String, List<ReservationItem>>()
            root.keys().forEach { roomId ->
                val arr = root.optJSONArray(roomId) ?: return@forEach
                val list = mutableListOf<ReservationItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list += ReservationItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        startTime = obj.optString("start"),
                        endTime = obj.optString("end")
                    )
                }
                result[roomId] = list
            }
            Log.d("MainActivity", "从缓存恢复预约信息: ${result.size} 间房间")
            result
        } catch (e: Exception) {
            Log.w("MainActivity", "读取预约缓存失败: ${e.message}")
            emptyMap()
        }
    }
}

suspend fun fetchReservations(roomId: String): List<ReservationItem> {
    return fetchReservationsOrNull(roomId).orEmpty()
}

private suspend fun fetchReservationsOrNull(roomId: String): List<ReservationItem>? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://weixinlib.lut.edu.cn/datafeed?method=list&id=$roomId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            parseReservations(json)
        } catch (e: Exception) {
            Log.e("MainActivity", "获取房间 $roomId 预约信息失败: ${e.message}", e)
            null
        }
    }
}

fun parseReservations(json: String): List<ReservationItem> {
    return try {
        val root = JSONObject(json)
        val events = root.optJSONArray("events") ?: return emptyList()
        buildList {
            for (i in 0 until events.length()) {
                val arr = events.optJSONArray(i) ?: continue
                val id = arr.opt(0)?.toString() ?: continue
                val title = arr.opt(1)?.toString() ?: "预约记录"
                val start = arr.opt(2)?.toString() ?: ""
                val end = arr.opt(3)?.toString() ?: ""
                add(ReservationItem(id = id, title = title, startTime = start, endTime = end))
            }
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "解析预约信息失败: ${e.message}", e)
        emptyList()
    }
}

private val reservationFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")

fun ReservationItem.startDateTime(): LocalDateTime? = try {
    LocalDateTime.parse(startTime, reservationFormatter)
} catch (_: Exception) {
    null
}

fun ReservationItem.endDateTime(): LocalDateTime? = try {
    LocalDateTime.parse(endTime, reservationFormatter)
} catch (_: Exception) {
    null
}

@Composable
fun rememberConferenceRooms(): RoomsState {
    val context = LocalContext.current
    var data by remember { mutableStateOf<List<ConferenceRoom>>(emptyList()) }
    var categories by remember { mutableStateOf(listOf("所有分组")) }
    var offlineNotice by remember { mutableStateOf<String?>(null) }
    var refreshFlag by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshFlag) {
        isRefreshing = true
        try {
            val timeoutMillis = 15_000L
            val rawOnline = withTimeoutOrNull(timeoutMillis) {
                fetchConferenceRoomsFromWeb()
            }
            val timedOut = rawOnline == null
            val online = rawOnline ?: RoomFetchResult(emptyList(), null)

            val cachedJson = loadRoomJsonFromCache(context)
            val cachedTs = loadRoomCacheTimestamp(context)

            val finalData = when {
                online.rooms.isNotEmpty() -> {
                    Log.d("MainActivity", "从网络获取到 ${online.rooms.size} 条房间数据")
                    offlineNotice = null
                    online.rawJson?.let { saveRoomJsonToCache(context, it) }
                    online.rooms
                }
                cachedJson != null -> {
                    val tsText = cachedTs?.toLongOrNull()?.let { millis ->
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(formatter)
                    }
                    offlineNotice = "离线数据，上次更新于 ${tsText ?: "未知时间"}"
                    Log.w("MainActivity", "网络为空，使用本地缓存 room_cache.json")
                    parseConferenceRooms(cachedJson)
                }
                else -> {
                    offlineNotice = "离线数据，上次更新于 内置数据"
                    Log.w("MainActivity", "缓存为空，回退到内置 roomdata.json")
                    loadLocalConferenceRooms(context)
                }
            }
            if (timedOut) {
                offlineNotice = offlineNotice?.let { existing ->
                    if (existing.contains("刷新超时")) existing
                    else "$existing（刷新超时）"
                } ?: "刷新超时，请稍后重试"
            }

            data = finalData
            categories = listOf("所有分组") + finalData.map { it.location }.distinct()
        } finally {
            isRefreshing = false
        }
    }

    val triggerRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            refreshFlag += 1
        }
    }

    return RoomsState(
        categories = categories,
        rooms = data,
        offlineNotice = offlineNotice,
        onRefresh = triggerRefresh,
        isRefreshing = isRefreshing
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var selectedNavItem by remember { mutableIntStateOf(0) }
    var userCenterRefreshKey by remember { mutableIntStateOf(0) }
    var isGridView by remember { mutableStateOf(true) }
    val roomsState = rememberConferenceRooms()
    val categories = roomsState.categories
    val conferenceRooms = roomsState.rooms
    val offlineNotice = roomsState.offlineNotice
    val onRefresh = roomsState.onRefresh
    val isRefreshing = roomsState.isRefreshing
    val searchState = rememberSearchAvailabilityState()

    val filteredRooms = remember(selectedCategoryIndex, conferenceRooms) {
        if (selectedCategoryIndex == 0) conferenceRooms
        else conferenceRooms.filter { it.location == categories[selectedCategoryIndex] }
    }

    val openRoomDetail: (ConferenceRoom) -> Unit = remember(conferenceRooms, offlineNotice) {
        { room ->
            val intent = Intent(context, RoomDetailActivity::class.java).apply {
                putExtra(RoomDetailActivity.EXTRA_ROOM, room)
                offlineNotice?.let { putExtra(RoomDetailActivity.EXTRA_OFFLINE_NOTICE, it) }
            }
            context.startActivity(intent)
        }
    }
    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            userCenterRefreshKey += 1
        }
    }
    LaunchedEffect(Unit) {
        fetchUserCenterProfile(context)
    }
    val topBarTitle = when (selectedNavItem) {
        0 -> "研讨室预约系统"
        1 -> "空闲研讨室搜索"
        2 -> "我的预约"
        else -> "个人中心"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = topBarTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { isGridView = !isGridView }
                    ) {
                        Icon(
                            imageVector = if (isGridView)
                                Icons.Filled.ViewModule
                            else
                                Icons.Filled.ViewList,
                            contentDescription = "切换视图"
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "更多选项"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedItem = selectedNavItem,
                onItemSelected = {
                    selectedNavItem = it
                    if (it == 2 || it == 3) {
                        userCenterRefreshKey += 1
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedNavItem) {
                0 -> {
                    if (offlineNotice != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable(enabled = !isRefreshing) { onRefresh() },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = offlineNotice,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp
                                )
                                if (isRefreshing) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    CategoryTabs(
                        categories = categories,
                        selectedIndex = selectedCategoryIndex,
                        onCategorySelected = { selectedCategoryIndex = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (conferenceRooms.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                LoadingIndicator()
                                Text(
                                    text = if (isRefreshing) "正在加载研讨室列表" else "暂无研讨室数据",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (isGridView) {
                        ConferenceRoomGrid(rooms = filteredRooms, onRoomSelected = openRoomDetail)
                    } else {
                        ConferenceRoomList(rooms = filteredRooms, onRoomSelected = openRoomDetail)
                    }
                }
                1 -> {
                    SearchAvailabilityScreen(
                        rooms = conferenceRooms,
                        state = searchState,
                        onRoomSelected = openRoomDetail,
                        onReserveRoomSelected = { room, selectedDate, startTime, endTime ->
                            context.startActivity(
                                BookingEntryActivity.createIntent(
                                    context = context,
                                    room = room,
                                    selectedDate = selectedDate,
                                    startTime = startTime,
                                    endTime = endTime
                                )
                            )
                        }
                    )
                }
                2 -> {
                    MyReservationsScreen(
                        refreshKey = userCenterRefreshKey,
                        onOpenLogin = { initialTab ->
                            loginLauncher.launch(LoginActivity.createIntent(context, initialTab))
                        }
                    )
                }
                3 -> {
                    PersonalCenterPlaceholderScreen(
                        refreshKey = userCenterRefreshKey,
                        onOpenLogin = { initialTab ->
                            loginLauncher.launch(LoginActivity.createIntent(context, initialTab))
                        },
                        onNavigateToReservations = {
                            selectedNavItem = 2
                            userCenterRefreshKey += 1
                        },
                        onNavigateToCards = {
                            context.startActivity(MyCardsActivity.createIntent(context))
                        }
                    )
                }
                else -> {
                    FeaturePlaceholderScreen(
                        title = "功能开发中",
                        description = "当前页面还未开放。"
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePlaceholderScreen(
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PersonalCenterPlaceholderScreen(
    refreshKey: Int,
    onOpenLogin: (Int) -> Unit,
    onNavigateToReservations: () -> Unit,
    onNavigateToCards: () -> Unit
) {
    val context = LocalContext.current
    val quickActions = listOf(
        "我的预约" to "登录后同步个人预约记录与状态",
        "常用房间" to "后续可保存常看的研讨室"
    )

    val profileServices = listOf(
        "我的卡片" to "这里可以添加与管理您常用的卡片信息",
        "设置" to "后续可管理应用偏好与通用配置"
    )
    var isLoading by remember(refreshKey) { mutableStateOf(true) }
    var profile by remember(refreshKey) { mutableStateOf<UserCenterProfile?>(null) }
    var message by remember(refreshKey) { mutableStateOf<String?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val profileBannerContainerColor = if (profile == null && !isLoading) {
        Color(0xFFEECFA7)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val profileBannerContentColor = if (profile == null && !isLoading) {
        Color(0xFF2F1A00)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    LaunchedEffect(refreshKey) {
        isLoading = true
        val result = fetchUserCenterProfile(context)
        profile = result.profile
        message = result.message
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = profileBannerContainerColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(profileBannerContentColor.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "用户头像占位",
                            modifier = Modifier.size(36.dp),
                            tint = profileBannerContentColor
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when {
                                isLoading -> "正在同步个人中心"
                                profile != null -> profile?.username.orEmpty()
                                else -> "未登录"
                            },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = profileBannerContentColor
                        )
                        Text(
                            text = when {
                                isLoading -> "正在带着当前会话访问 usercenter 并读取用户名。"
                                profile != null -> listOfNotNull(
                                    profile?.userCode?.let { "证号 $it" },
                                    profile?.userUnit,
                                    profile?.userType
                                ).joinToString(" · ").ifBlank { "已成功获取个人中心用户名" }
                                else -> message ?: "登录后可查看个人预约、常用房间、我的卡片和设置。"
                            },
                            fontSize = 13.sp,
                            color = profileBannerContentColor.copy(alpha = 0.82f)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (profile == null) {
                            onOpenLogin(LoginActivity.TAB_LIBRARY)
                        } else {
                            showLogoutConfirm = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            isLoading -> "同步中"
                            profile != null -> "退出登录"
                            else -> "登录"
                        }
                    )
                }
            }
        }

        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text("退出登录") },
                text = { Text("确认退出当前登录状态？") },
                confirmButton = {
                    Button(
                        onClick = {
                            AuthSessionManager.clear(context)
                            showLogoutConfirm = false
                            profile = null
                            message = "已退出登录"
                            isLoading = false
                        }
                    ) {
                        Text("退出登录")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showLogoutConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "个人中心",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                quickActions.forEach { (title, subtitle) ->
                    ProfileMenuCard(
                        title = title,
                        subtitle = subtitle,
                        onClick = {
                            if (title == "我的预约") {
                                onNavigateToReservations()
                            }
                        }
                    )
                }

                profileServices.forEach { (title, subtitle) ->
                    ProfileMenuCard(
                        title = title,
                        subtitle = subtitle,
                        onClick = {
                            if (title == "我的卡片") {
                                onNavigateToCards()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.rotate(180f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryTabs(
    categories: List<String>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEachIndexed { index, category ->
            CategoryTab(
                text = category,
                isSelected = index == selectedIndex,
                onClick = { onCategorySelected(index) }
            )
        }
    }
}

@Composable
fun CategoryTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

class SearchAvailabilityState {
    private val initialClock: LocalDateTime = LocalDateTime.now()
    var selectedDate by mutableStateOf(initialClock.toLocalDate())
    var startTime by mutableStateOf(nextQuarterHour(initialClock.toLocalTime()))
    var endTime by mutableStateOf(nearestHalfHour(initialClock.toLocalTime().plusHours(2)))
    var selectedCampus by mutableStateOf("全部")
    var filterText by mutableStateOf("")
    var enableTransit by mutableStateOf(false)
    var transitPlans by mutableStateOf<List<TransitPlan>>(emptyList())
    var totalRooms by mutableStateOf(0)
    var ongoing by mutableIntStateOf(0)
    var isFetchingRooms by mutableStateOf(false)
    var statusText by mutableStateOf("等待获取所有研讨室结果（0/0）")
    var reservationsCacheTimeText by mutableStateOf<String?>(null)
    var reservationsCacheAgeMillis by mutableStateOf<Long?>(null)
    var reservationsCacheSavedAtMillis by mutableStateOf<Long?>(null)
    val reservationResults = mutableStateMapOf<String, List<ReservationItem>>()
    var hasLoadedCache by mutableStateOf(false)
    var availableRooms by mutableStateOf<List<ConferenceRoom>>(emptyList())
    var resultMessage by mutableStateOf("尚未查询空闲时间")
}

@Composable
fun rememberSearchAvailabilityState(): SearchAvailabilityState = remember { SearchAvailabilityState() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchAvailabilityScreen(
    rooms: List<ConferenceRoom>,
    state: SearchAvailabilityState,
    onRoomSelected: (ConferenceRoom) -> Unit,
    onReserveRoomSelected: (ConferenceRoom, LocalDate, LocalTime, LocalTime) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    state.totalRooms = rooms.size
    var selectedDate by state::selectedDate
    var startTime by state::startTime
    var endTime by state::endTime
    var selectedCampus by state::selectedCampus
    var filterText by state::filterText
    var enableTransit by state::enableTransit
    var ongoing by state::ongoing
    var isFetchingRooms by state::isFetchingRooms
    var statusText by state::statusText
    var reservationsCacheTimeText by state::reservationsCacheTimeText
    var reservationsCacheAgeMillis by state::reservationsCacheAgeMillis
    var reservationsCacheSavedAtMillis by state::reservationsCacheSavedAtMillis
    var showRefreshConfirm by remember { mutableStateOf(false) }
    var showAdvancedOptionsHint by remember { mutableStateOf(false) }
    var advancedOperator by remember { mutableStateOf("≥") }
    var advancedPeopleCountText by remember { mutableStateOf("") }
    var advancedRoomFilterText by remember { mutableStateOf("") }
    var advancedOperatorExpanded by remember { mutableStateOf(false) }
    val reservationResults = state.reservationResults
    var hasLoadedCache by state::hasLoadedCache
    var availableRooms by state::availableRooms
    var resultMessage by state::resultMessage
    var transitPlans by state::transitPlans

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var dateText by remember { mutableStateOf(selectedDate.format(dateFormatter)) }
    var startTimeText by remember { mutableStateOf(startTime.format(timeFormatter)) }
    var endTimeText by remember { mutableStateOf(endTime.format(timeFormatter)) }
    val campusOptions = listOf("全部", "彭家坪校区", "兰工坪校区")
    var campusExpanded by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showStartTimePickerDialog by remember { mutableStateOf(false) }
    var showEndTimePickerDialog by remember { mutableStateOf(false) }
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atOffset(ZoneOffset.UTC)
                                .toLocalDate()
                            dateText = selectedDate.format(dateFormatter)
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDatePickerDialog = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePickerDialog) {
        val startTimePickerState = rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartTimePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        startTime = LocalTime.of(
                            startTimePickerState.hour,
                            startTimePickerState.minute
                        )
                        startTimeText = startTime.format(timeFormatter)
                        showStartTimePickerDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStartTimePickerDialog = false }) {
                    Text("取消")
                }
            },
            text = {
                TimePicker(state = startTimePickerState)
            }
        )
    }

    if (showEndTimePickerDialog) {
        val endTimePickerState = rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showEndTimePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        endTime = LocalTime.of(
                            endTimePickerState.hour,
                            endTimePickerState.minute
                        )
                        endTimeText = endTime.format(timeFormatter)
                        showEndTimePickerDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEndTimePickerDialog = false }) {
                    Text("取消")
                }
            },
            text = {
                TimePicker(state = endTimePickerState)
            }
        )
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(rooms) {
        ongoing = state.ongoing
        isFetchingRooms = state.isFetchingRooms

        if (rooms.isEmpty()) {
            if (!isFetchingRooms && reservationResults.isEmpty()) {
                statusText = "等待研讨室列表加载"
            }
            state.selectedDate = selectedDate
            state.startTime = startTime
            state.endTime = endTime
            state.filterText = filterText
            state.enableTransit = enableTransit
            state.ongoing = ongoing
            state.isFetchingRooms = isFetchingRooms
            state.statusText = statusText
            state.reservationsCacheTimeText = reservationsCacheTimeText
            state.reservationsCacheAgeMillis = reservationsCacheAgeMillis
            state.reservationsCacheSavedAtMillis = reservationsCacheSavedAtMillis
            state.hasLoadedCache = hasLoadedCache
            state.availableRooms = availableRooms
            state.resultMessage = resultMessage
            return@LaunchedEffect
        }

        if (!hasLoadedCache) {
            val cached = loadReservationsCache(context)
            val rawCacheTimestamp = loadReservationsCacheTimestamp(context)
            if (isCacheExpired(rawCacheTimestamp)) {
                reservationsCacheTimeText = null
                reservationsCacheAgeMillis = null
                reservationsCacheSavedAtMillis = null
            } else {
                reservationsCacheTimeText = formatCacheTimestamp(rawCacheTimestamp)
                reservationsCacheSavedAtMillis = rawCacheTimestamp?.trim()?.toLongOrNull()
                reservationsCacheAgeMillis = cacheAgeMillis(rawCacheTimestamp)
            }
            val validIds = rooms.map { it.id }.toSet()
            cached.forEach { (roomId, list) ->
                if (roomId in validIds) {
                    reservationResults[roomId] = list
                }
            }
            ongoing = reservationResults.size
            statusText = if (reservationResults.isNotEmpty()) {
                val (ageMillis, cacheStatusText) = refreshReservationsCacheStatus(
                    count = reservationResults.size,
                    savedAtMillis = reservationsCacheSavedAtMillis,
                    cacheTimeText = reservationsCacheTimeText
                )
                reservationsCacheAgeMillis = ageMillis
                cacheStatusText
            } else {
                "等待获取所有研讨室结果（0/${state.totalRooms}）"
            }
            hasLoadedCache = true
        } else {
            val validIds = rooms.map { it.id }.toSet()
            reservationResults.keys.toList().forEach { roomId ->
                if (roomId !in validIds) {
                    reservationResults.remove(roomId)
                }
            }
            ongoing = reservationResults.size
            if (!isFetchingRooms) {
                statusText = if (reservationResults.isNotEmpty()) {
                    val (ageMillis, cacheStatusText) = refreshReservationsCacheStatus(
                        count = reservationResults.size,
                        savedAtMillis = reservationsCacheSavedAtMillis,
                        cacheTimeText = reservationsCacheTimeText
                    )
                    reservationsCacheAgeMillis = ageMillis
                    cacheStatusText
                } else {
                    "等待获取所有研讨室结果（0/${state.totalRooms}）"
                }
            }
        }
        state.selectedDate = selectedDate
        state.startTime = startTime
        state.endTime = endTime
        state.filterText = filterText
        state.enableTransit = enableTransit
        state.ongoing = ongoing
        state.isFetchingRooms = isFetchingRooms
        state.statusText = statusText
        state.reservationsCacheTimeText = reservationsCacheTimeText
        state.reservationsCacheAgeMillis = reservationsCacheAgeMillis
        state.reservationsCacheSavedAtMillis = reservationsCacheSavedAtMillis
        state.hasLoadedCache = hasLoadedCache
        state.availableRooms = availableRooms
        state.resultMessage = resultMessage
    }

    val runQuery: () -> Unit = runQuery@{
        if (state.totalRooms == 0) {
            resultMessage = "研讨室列表加载中，请稍后重试"
            availableRooms = emptyList()
            return@runQuery
        }
        if (startTime >= endTime) {
            resultMessage = "开始时间需早于结束时间"
            availableRooms = emptyList()
            return@runQuery
        }
        if (reservationResults.isNotEmpty()) {
            val (ageMillis, cacheStatusText) = refreshReservationsCacheStatus(
                count = reservationResults.size,
                savedAtMillis = reservationsCacheSavedAtMillis,
                cacheTimeText = reservationsCacheTimeText
            )
            reservationsCacheAgeMillis = ageMillis
            statusText = cacheStatusText
        }
        val userStart = LocalDateTime.of(selectedDate, startTime)
        val userEnd = LocalDateTime.of(selectedDate, endTime)
        val trimmedFilterText = filterText.trim()
        val queryFilters = parseQueryFilters(trimmedFilterText)

        if (trimmedFilterText.isNotEmpty() && queryFilters == null) {
            resultMessage = "筛选条件格式异常，支持示例：5、507、A5、>=10、A5,>=10"
            availableRooms = emptyList()
            transitPlans = emptyList()
            return@runQuery
        }

        val filteredRooms = rooms.filter { room ->
            selectedCampus == "全部" || room.campus() == selectedCampus
        }.filter { room ->
            queryFilters?.all { filter -> filter.matches(room) } ?: true
        }

        val free = filteredRooms.filter { room ->
            val reservations = reservationResults[room.id].orEmpty()
            val hasOverlap = reservations.any { item ->
                val s = item.startDateTime()
                val e = item.endDateTime()
                if (s == null || e == null) return@any false
                if (s.toLocalDate() != selectedDate) return@any false
                !(e <= userStart || s >= userEnd)
            }
            !hasOverlap
        }.sortedBy { it.name }

        availableRooms = free

        if (enableTransit) {
            transitPlans = findTransitPlans(
                rooms = filteredRooms,
                reservationResults = reservationResults,
                userStart = userStart,
                userEnd = userEnd,
                campus = selectedCampus,
                directFreeRoomIds = free.map { it.id }.toSet()
            )
        } else {
            transitPlans = emptyList()
        }

        val transitSuffix = if (enableTransit && transitPlans.isNotEmpty()) {
            "，可用中转方案 ${transitPlans.size} 条"
        } else ""

        resultMessage = "空闲房间 ${free.size}/${filteredRooms.size}${transitSuffix}"
    }

    val refreshReservations: ((() -> Unit)?) -> Unit = refresh@{ afterFetch ->
        if (state.totalRooms == 0 || isFetchingRooms) {
            return@refresh
        }
        isFetchingRooms = true
        ongoing = 0
        statusText = "获取中（0/${state.totalRooms}）"
        scope.launch {
            var failedCount = 0
            try {
                rooms.forEachIndexed { idx, room ->
                    val res = fetchReservationsOrNull(room.id)
                    if (res != null) {
                        reservationResults[room.id] = res
                    } else {
                        failedCount += 1
                    }
                    ongoing = idx + 1
                    statusText = "获取中（${ongoing}/${state.totalRooms}）"
                }
                if (failedCount == 0) {
                    saveReservationsCache(context, reservationResults.toMap())
                    val rawTs = loadReservationsCacheTimestamp(context)
                    reservationsCacheTimeText = formatCacheTimestamp(rawTs)
                    reservationsCacheSavedAtMillis = rawTs?.trim()?.toLongOrNull()
                    reservationsCacheAgeMillis = cacheAgeMillis(rawTs)
                } else {
                    reservationsCacheAgeMillis = reservationsCacheSavedAtMillis?.let { savedAt ->
                        (System.currentTimeMillis() - savedAt).coerceAtLeast(0L)
                    }
                }
                statusText = if (failedCount > 0 && reservationResults.isNotEmpty()) {
                    buildReservationsCacheStatus(
                        prefix = "网络异常，继续使用缓存",
                        count = reservationResults.size,
                        cacheAgeText = formatCacheAgeText(reservationsCacheAgeMillis),
                        cacheTimeText = reservationsCacheTimeText
                    )
                } else if (failedCount > 0) {
                    "网络异常，且暂无可用预约缓存"
                } else {
                    buildReservationsCacheStatus(
                        prefix = "已加载缓存",
                        count = reservationResults.size,
                        cacheAgeText = formatCacheAgeText(reservationsCacheAgeMillis),
                        cacheTimeText = reservationsCacheTimeText
                    )
                }
                afterFetch?.invoke()
            } catch (e: CancellationException) {
                statusText = "获取已取消（${ongoing}/${state.totalRooms}）"
                throw e
            } finally {
                isFetchingRooms = false
            }
        }
    }

    if (showRefreshConfirm) {
        AlertDialog(
            onDismissRequest = { showRefreshConfirm = false },
            title = { Text("确认刷新缓存") },
            text = { Text("刷新后会重新获取所有研讨室预约数据，是否继续？") },
            confirmButton = {
                Button(
                    onClick = {
                        showRefreshConfirm = false
                        refreshReservations(null)
                    }
                ) {
                    Text("继续刷新")
                }
            },
            dismissButton = {
                Button(onClick = { showRefreshConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAdvancedOptionsHint) {
        AdvancedFilterDialog(
            advancedOperator = advancedOperator,
            advancedPeopleCountText = advancedPeopleCountText,
            advancedRoomFilterText = advancedRoomFilterText,
            advancedOperatorExpanded = advancedOperatorExpanded,
            onDismissRequest = { showAdvancedOptionsHint = false },
            onOperatorExpandedChange = { advancedOperatorExpanded = it },
            onOperatorChange = {
                advancedOperator = it
                advancedOperatorExpanded = false
            },
            onPeopleCountChange = { input ->
                if (input.all { it.isDigit() }) {
                    advancedPeopleCountText = input
                }
            },
            onRoomFilterChange = { input ->
                if (input.all { it.isLetterOrDigit() }) {
                    advancedRoomFilterText = input.uppercase()
                }
            },
            onConfirm = {
                val mergedCapacity = mergeCapacityFilter(
                    input = filterText,
                    operatorSymbol = advancedOperator,
                    peopleCountText = advancedPeopleCountText
                )
                filterText = mergeTextFilter(
                    input = mergedCapacity,
                    text = advancedRoomFilterText
                )
                showAdvancedOptionsHint = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "查询条件",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "请选择日期和时间段，一键查询该时间段的空闲房间",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val dateInteractionSource = remember { MutableInteractionSource() }
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { input ->
                            dateText = input
                            runCatching { LocalDate.parse(input, dateFormatter) }
                                .onSuccess { selectedDate = it }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = dateInteractionSource,
                                indication = null
                            ) { showDatePickerDialog = true },
                        label = { Text("日期") },
                        placeholder = { Text("选择日期") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePickerDialog = true }) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = "选择日期")
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val startInteractionSource = remember { MutableInteractionSource() }
                        val endInteractionSource = remember { MutableInteractionSource() }
                        OutlinedTextField(
                            value = startTimeText,
                            onValueChange = { input ->
                                startTimeText = input
                                runCatching { LocalTime.parse(input, timeFormatter) }
                                    .onSuccess { startTime = it }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = startInteractionSource,
                                    indication = null
                                ) { showStartTimePickerDialog = true },
                            label = { Text("开始时间") },
                            placeholder = { Text("如 09:00") },
                            trailingIcon = {
                                IconButton(onClick = { showStartTimePickerDialog = true }) {
                                    Icon(Icons.Filled.AccessTime, contentDescription = "选择开始时间")
                                }
                            }
                        )
                        OutlinedTextField(
                            value = endTimeText,
                            onValueChange = { input ->
                                endTimeText = input
                                runCatching { LocalTime.parse(input, timeFormatter) }
                                    .onSuccess { endTime = it }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = endInteractionSource,
                                    indication = null
                                ) { showEndTimePickerDialog = true },
                            label = { Text("结束时间") },
                            placeholder = { Text("如 11:00") },
                            trailingIcon = {
                                IconButton(onClick = { showEndTimePickerDialog = true }) {
                                    Icon(Icons.Filled.AccessTime, contentDescription = "选择结束时间")
                                }
                            }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = campusExpanded,
                                onExpandedChange = { campusExpanded = !campusExpanded },
                                modifier = Modifier.width(150.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedCampus,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("校区") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = campusExpanded)
                                    },
                                    modifier = Modifier.menuAnchor()
                                )
                                DropdownMenu(
                                    expanded = campusExpanded,
                                    onDismissRequest = { campusExpanded = false }
                                ) {
                                    campusOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                selectedCampus = option
                                                campusExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = filterText,
                                onValueChange = { filterText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("筛选条件") },
                                placeholder = { Text("如 5 / 507 / >=6") }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { enableTransit = !enableTransit }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = enableTransit, onCheckedChange = { enableTransit = it })
                                Text("搜索中转方案", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val draft = readCapacityFilterDraft(filterText)
                                    advancedOperator = draft?.operatorSymbol ?: "≥"
                                    advancedPeopleCountText = draft?.peopleCountText.orEmpty()
                                    advancedRoomFilterText = readTextFilterDraft(filterText).orEmpty()
                                    advancedOperatorExpanded = false
                                    showAdvancedOptionsHint = true
                                }
                            ) {
                                Text("高级筛选")
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (reservationResults.isEmpty() && !isFetchingRooms && state.totalRooms > 0) {
                            refreshReservations(runQuery)
                        } else {
                            runQuery()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("查询空闲研讨室")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "查询结果",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (availableRooms.isEmpty()) {
                    Text(resultMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(resultMessage, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableRooms.forEach { room ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(onClick = { onRoomSelected(room) }),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(room.name, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onReserveRoomSelected(room, selectedDate, startTime, endTime)
                                            },
                                            modifier = Modifier.heightIn(min = 32.dp),
                                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 4.dp)
                                        ) {
                                            Text("去预约", fontSize = 12.sp)
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.ArrowBack,
                                            contentDescription = "跳转详情",
                                            modifier = Modifier.rotate(180f),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (enableTransit && transitPlans.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("中转方案", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        transitPlans.forEach { plan ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${plan.first.name} -> ${plan.second.name}", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "中转时间：${plan.split.toLocalTime().format(timeFormatter)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "校区：${plan.first.campus()}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                val statusInteractionSource = remember { MutableInteractionSource() }
                if (isFetchingRooms) {
                    LinearWavyProgressIndicator(
                        progress = {
                            if (state.totalRooms > 0) {
                                ongoing.toFloat() / state.totalRooms.toFloat()
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = statusText,
                    color = run {
                        val warnColor = Color(0xFFB35C00)
                        when {
                            isFetchingRooms -> MaterialTheme.colorScheme.primary
                            reservationResults.isNotEmpty() && (reservationsCacheAgeMillis ?: 0) >= CACHE_DANGER_AGE_MILLIS ->
                                MaterialTheme.colorScheme.error
                            reservationResults.isNotEmpty() && (reservationsCacheAgeMillis ?: 0) >= CACHE_WARN_AGE_MILLIS ->
                                warnColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = state.totalRooms > 0,
                            interactionSource = statusInteractionSource,
                            indication = null
                        ) {
                            if (isFetchingRooms) return@clickable
                            if (reservationResults.isNotEmpty()) {
                                if ((reservationsCacheAgeMillis ?: 0L) >= CACHE_DANGER_AGE_MILLIS) {
                                    refreshReservations(null)
                                } else {
                                    showRefreshConfirm = true
                                }
                            } else {
                                refreshReservations(null)
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun ConferenceRoomGrid(
    rooms: List<ConferenceRoom>,
    onRoomSelected: (ConferenceRoom) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(rooms) { room ->
            ConferenceRoomCard(room = room, onClick = { onRoomSelected(room) })
        }
    }
}

@Composable
fun ConferenceRoomCard(room: ConferenceRoom, onClick: () -> Unit) {
    val context = LocalContext.current
    val imageRequest = remember(room.imageUrl) {
        if (room.imageUrl.isNullOrBlank()) null else
            ImageRequest.Builder(context)
                .data(room.imageUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 图片区域 - Coil 自动缓存
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "${room.name}图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无图片",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            // 房间信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = room.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "房间ID: ${room.id}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最少: ${room.minCapacity}人",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "最多: ${room.maxCapacity}人",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RoomStatusBadge(room = room)
            }
        }
    }
}

@Composable
fun ConferenceRoomListItem(room: ConferenceRoom, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = room.shortLabel(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = room.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "最少 ${room.minCapacity}人",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "最多 ${room.maxCapacity}人",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            RoomStatusBadge(room = room)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFilterDialog(
    advancedOperator: String,
    advancedPeopleCountText: String,
    advancedRoomFilterText: String,
    advancedOperatorExpanded: Boolean,
    onDismissRequest: () -> Unit,
    onOperatorExpandedChange: (Boolean) -> Unit,
    onOperatorChange: (String) -> Unit,
    onPeopleCountChange: (String) -> Unit,
    onRoomFilterChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("高级筛选") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设置人数筛选条件")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = advancedOperatorExpanded,
                        onExpandedChange = { onOperatorExpandedChange(!advancedOperatorExpanded) },
                        modifier = Modifier.width(132.dp)
                    ) {
                        OutlinedTextField(
                            value = advancedOperatorLabel(advancedOperator),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("条件") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = advancedOperatorExpanded)
                            },
                            modifier = Modifier.menuAnchor()
                        )
                        DropdownMenu(
                            expanded = advancedOperatorExpanded,
                            onDismissRequest = { onOperatorExpandedChange(false) }
                        ) {
                            advancedOperatorOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { onOperatorChange(option.symbol) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = advancedPeopleCountText,
                        onValueChange = onPeopleCountChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("人数") },
                        placeholder = { Text("输入人数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("设置楼层或房间号")
                OutlinedTextField(
                    value = advancedRoomFilterText,
                    onValueChange = onRoomFilterChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("楼层/房间号") },
                    placeholder = { Text("如 5 / 507 / A5") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("确定")
            }
        },
        dismissButton = {
            Button(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}

@Composable
fun RoomStatusBadge(room: ConferenceRoom) {
    val containerColor = if (room.inUse) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (room.inUse) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = room.status,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ConferenceRoomList(
    rooms: List<ConferenceRoom>,
    onRoomSelected: (ConferenceRoom) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(rooms) { room ->
            ConferenceRoomListItem(room = room, onClick = { onRoomSelected(room) })
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "首页") },
            label = { Text("首页") },
            selected = selectedItem == 0,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { onItemSelected(0) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },

            label = { Text("搜索") },
            selected = selectedItem == 1,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { onItemSelected(1) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Info, contentDescription = "预约") },
            label = { Text("我的预约") },
            selected = selectedItem == 2,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { onItemSelected(2) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Person, contentDescription = "个人中心") },
            label = { Text("个人中心") },
            selected = selectedItem == 3,
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { onItemSelected(3) }
        )
    }
}

private fun nearestHalfHour(time: LocalTime): LocalTime {
    val truncated = time.truncatedTo(ChronoUnit.MINUTES)
    val remainder = truncated.minute % 30
    return if (remainder < 15) {
        truncated.minusMinutes(remainder.toLong())
    } else {
        truncated.plusMinutes((30 - remainder).toLong())
    }
}

private fun nextQuarterHour(time: LocalTime): LocalTime {
    val truncated = time.truncatedTo(ChronoUnit.MINUTES)
    val remainder = truncated.minute % 15
    if (remainder == 0) return truncated
    return truncated.plusMinutes((15 - remainder).toLong())
}

//@Preview(showBackground = true)
//@Composable
//fun HomeScreenPreview() {
//    MyApplicationTheme {
//        HomeScreen()
//    }
//}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
fun SearchAvailabilityScreenPreview() {
    val previewRooms = listOf(
        ConferenceRoom(
            id = "105",
            name = "研讨室 A707",
            minCapacity = 6,
            maxCapacity = 12,
            location = "彭家坪校区 7 楼研讨 A 区",
            floor = "7F",
            inUse = false,
            status = "空闲"
        ),
        ConferenceRoom(
            id = "146",
            name = "研讨室 207",
            minCapacity = 6,
            maxCapacity = 12,
            location = "兰工坪校区 2 楼研讨区",
            floor = "2F",
            inUse = false,
            status = "空闲"
        ),
        ConferenceRoom(
            id = "157",
            name = "研讨室 308",
            minCapacity = 6,
            maxCapacity = 24,
            location = "兰工坪校区 3 楼研讨区",
            floor = "3F",
            inUse = true,
            status = "使用中"
        )
    )
    val previewState = remember { SearchAvailabilityState() }.apply {
        selectedDate = LocalDate.of(2026, 3, 30)
        startTime = LocalTime.of(9, 0)
        endTime = LocalTime.of(11, 0)
        selectedCampus = "全部"
        filterText = "A7"
        enableTransit = true
        totalRooms = previewRooms.size
        ongoing = previewRooms.size
        isFetchingRooms = false
        statusText = "已加载缓存·${previewRooms.size}个·3小时前 (3/30 19:20)"
        reservationsCacheTimeText = "3/30 19:20"
        reservationsCacheAgeMillis = 3 * 60 * 60 * 1000L
        reservationsCacheSavedAtMillis = System.currentTimeMillis() - (reservationsCacheAgeMillis ?: 0L)
        hasLoadedCache = true
        availableRooms = previewRooms.take(2)
        transitPlans = listOf(
            TransitPlan(
                first = previewRooms[0],
                second = previewRooms[1],
                split = LocalDateTime.of(2026, 3, 30, 10, 0)
            )
        )
        resultMessage = "空闲房间 2/${previewRooms.size}，可用中转方案 1 条"
        reservationResults.clear()
        reservationResults[previewRooms[0].id] = listOf(
            ReservationItem("30001", "预约记录", "03/30/2026 13:00", "03/30/2026 15:00")
        )
        reservationResults[previewRooms[1].id] = emptyList()
        reservationResults[previewRooms[2].id] = listOf(
            ReservationItem("30002", "预约记录", "03/30/2026 09:00", "03/30/2026 10:00")
        )
    }

    MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SearchAvailabilityScreen(
                rooms = previewRooms,
                state = previewState,
                onRoomSelected = {},
                onReserveRoomSelected = { _, _, _, _ -> }
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 720)
@Composable
fun AdvancedFilterDialogPreview() {
    var advancedOperator by remember { mutableStateOf("≥") }
    var advancedPeopleCountText by remember { mutableStateOf("8") }
    var advancedRoomFilterText by remember { mutableStateOf("A5") }
    var advancedOperatorExpanded by remember { mutableStateOf(false) }

    MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AdvancedFilterDialog(
                advancedOperator = advancedOperator,
                advancedPeopleCountText = advancedPeopleCountText,
                advancedRoomFilterText = advancedRoomFilterText,
                advancedOperatorExpanded = advancedOperatorExpanded,
                onDismissRequest = {},
                onOperatorExpandedChange = { advancedOperatorExpanded = it },
                onOperatorChange = {
                    advancedOperator = it
                    advancedOperatorExpanded = false
                },
                onPeopleCountChange = { input ->
                    if (input.all { it.isDigit() }) {
                        advancedPeopleCountText = input
                    }
                },
                onRoomFilterChange = { input ->
                    if (input.all { it.isLetterOrDigit() }) {
                        advancedRoomFilterText = input.uppercase()
                    }
                },
                onConfirm = {}
            )
        }
    }
}

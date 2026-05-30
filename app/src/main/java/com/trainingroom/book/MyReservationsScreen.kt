package com.trainingroom.book

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MY_TRAINING_USE_LOG_URL = "https://weixinlib.lut.edu.cn/traininguselog"
private const val MY_TRAINING_CURRENT_URL = "https://weixinlib.lut.edu.cn/trainingroombeskinfor"
private const val MY_TRAINING_DELETE_MORE_URL = "https://weixinlib.lut.edu.cn/trainingroombeskinfor/deletemore"
private const val WEIXINLIB_WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"

enum class MyTrainingReservationCancelType {
    NONE,
    MULTI
}

data class MyTrainingReservationItem(
    val status: String,
    val roomName: String,
    val createdAt: String,
    val useDate: String,
    val startTime: String,
    val endDate: String,
    val endTime: String,
    val id: String = "",
    val cancelType: MyTrainingReservationCancelType = MyTrainingReservationCancelType.NONE
)

data class MyTrainingReservationPage(
    val items: List<MyTrainingReservationItem>,
    val beginDate: String,
    val endDate: String,
    val pageNo: Int,
    val pageSize: Int,
    val totalCount: Int
)

data class MyTrainingReservationFetchResult(
    val page: MyTrainingReservationPage? = null,
    val message: String? = null
)

data class MyTrainingCurrentReservationFetchResult(
    val items: List<MyTrainingReservationItem> = emptyList(),
    val message: String? = null
)

data class MyTrainingReservationCancelResult(
    val success: Boolean,
    val message: String
)

private data class MyTrainingHttpResponse(
    val body: String,
    val finalUrl: String
)

enum class ReservationDatePreset(val label: String, val monthCount: Long?) {
    LAST_MONTH("最近一个月", 1),
    LAST_THREE_MONTHS("最近三个月", 3),
    LAST_HALF_YEAR("最近半年", 6),
    CUSTOM("自定义", null)
}

private val reservationDatePresetOptions = listOf(
    ReservationDatePreset.LAST_MONTH,
    ReservationDatePreset.LAST_THREE_MONTHS,
    ReservationDatePreset.LAST_HALF_YEAR,
    ReservationDatePreset.CUSTOM
)

private fun dateRangeForPreset(
    preset: ReservationDatePreset,
    baseDate: LocalDate = LocalDate.now()
): Pair<LocalDate, LocalDate> {
    val monthCount = preset.monthCount ?: return baseDate to baseDate
    return baseDate.minusMonths(monthCount) to baseDate
}

class MyTrainingReservationsState {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    var beginDate by mutableStateOf(LocalDate.now().minusMonths(6))
    var endDate by mutableStateOf(LocalDate.now())
    var pageNo by mutableIntStateOf(0)
    var pageSize by mutableIntStateOf(10)
    var totalCount by mutableIntStateOf(0)
    var items by mutableStateOf<List<MyTrainingReservationItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isAppending by mutableStateOf(false)
    var canLoadMore by mutableStateOf(true)
    var hasQueried by mutableStateOf(false)
    var currentItems by mutableStateOf<List<MyTrainingReservationItem>>(emptyList())
    var currentIsLoading by mutableStateOf(false)
    var cancellingReservationId by mutableStateOf<String?>(null)
    var currentMessage by mutableStateOf<String?>(null)
    var selectedPreset by mutableStateOf(ReservationDatePreset.LAST_HALF_YEAR)
    var hideCancelled by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun beginDateText(): String = beginDate.format(dateFormatter)

    fun endDateText(): String = endDate.format(dateFormatter)

    fun hasMore(): Boolean = canLoadMore && pageSize > 0 && items.size < totalCount
}

@Composable
fun rememberMyTrainingReservationsState(): MyTrainingReservationsState =
    remember { MyTrainingReservationsState() }

suspend fun fetchMyTrainingReservations(
    context: Context,
    beginDate: String,
    endDate: String,
    pageNo: Int,
    pageSize: Int
): MyTrainingReservationFetchResult {
    val firstAttempt = fetchMyTrainingReservationsOnce(
        context = context,
        beginDate = beginDate,
        endDate = endDate,
        pageNo = pageNo,
        pageSize = pageSize
    )
    if (firstAttempt.page != null) {
        return firstAttempt
    }

    val shouldTrySilentRefresh =
        firstAttempt.message == "当前登录态已失效，请重新登录" &&
            AuthSessionManager.isSsoLogin(context)
    if (!shouldTrySilentRefresh) {
        return firstAttempt
    }

    val renewResult = SsoLoginService.trySilentRefresh(context)
    if (!renewResult.success) {
        return MyTrainingReservationFetchResult(message = renewResult.message)
    }
    return fetchMyTrainingReservationsOnce(
        context = context,
        beginDate = beginDate,
        endDate = endDate,
        pageNo = pageNo,
        pageSize = pageSize
    )
}

private suspend fun fetchMyTrainingReservationsOnce(
    context: Context,
    beginDate: String,
    endDate: String,
    pageNo: Int,
    pageSize: Int
): MyTrainingReservationFetchResult {
    return withContext(Dispatchers.IO) {
        AuthSessionManager.install(context)
        runCatching {
            val cookieHeader = AuthSessionManager.weixinlibCookieHeader(context)
            val pagerOffset = pageNo.coerceAtLeast(0) * pageSize.coerceAtLeast(1)
            val query = "pageSize=$pageSize&pageNumber=1&begintime=$beginDate&endtime=$endDate&pager.offset=$pagerOffset"
            val response = requestMyTrainingPage(
                url = "$MY_TRAINING_USE_LOG_URL?$query",
                referer = "https://weixinlib.lut.edu.cn/usercenter",
                cookieHeader = cookieHeader
            )
            val page = parseMyTrainingUseLogPage(
                html = response.body,
                requestedPageNo = pageNo,
                requestedPageSize = pageSize
            )
            when {
                page != null -> MyTrainingReservationFetchResult(page = page)
                "cas/login" in response.finalUrl || "统一身份认证" in response.body -> {
                    MyTrainingReservationFetchResult(message = "当前登录态已失效，请重新登录")
                }
                else -> MyTrainingReservationFetchResult(message = "已请求使用日志，但未解析到列表数据")
            }
        }.getOrElse { error ->
            Log.e("MyReservationsScreen", "获取我的预约失败: ${error.message}", error)
            MyTrainingReservationFetchResult(message = error.message ?: "获取我的预约失败")
        }
    }
}

private fun requestMyTrainingPage(
    url: String,
    referer: String,
    cookieHeader: String?
): MyTrainingHttpResponse {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", WEIXINLIB_WEB_USER_AGENT)
        setRequestProperty("Referer", referer)
        cookieHeader?.let { setRequestProperty("Cookie", it) }
    }
    val body = connection.inputStream.bufferedReader().use { it.readText() }
    return MyTrainingHttpResponse(
        body = body,
        finalUrl = connection.url.toString()
    )
}

suspend fun fetchMyCurrentTrainingReservations(
    context: Context
): MyTrainingCurrentReservationFetchResult {
    val firstAttempt = fetchMyCurrentTrainingReservationsOnce(context)
    if (firstAttempt.message != "当前登录态已失效，请重新登录") {
        return firstAttempt
    }
    if (!AuthSessionManager.isSsoLogin(context)) {
        return firstAttempt
    }

    val renewResult = SsoLoginService.trySilentRefresh(context)
    if (!renewResult.success) {
        return MyTrainingCurrentReservationFetchResult(message = renewResult.message)
    }
    return fetchMyCurrentTrainingReservationsOnce(context)
}

private suspend fun fetchMyCurrentTrainingReservationsOnce(
    context: Context
): MyTrainingCurrentReservationFetchResult {
    return withContext(Dispatchers.IO) {
        AuthSessionManager.install(context)
        runCatching {
            val cookieHeader = AuthSessionManager.weixinlibCookieHeader(context)
            val connection = (URL(MY_TRAINING_CURRENT_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", WEIXINLIB_WEB_USER_AGENT)
                setRequestProperty("Referer", "https://weixinlib.lut.edu.cn/usercenter")
                cookieHeader?.let { setRequestProperty("Cookie", it) }
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val finalUrl = connection.url.toString()
            val items = parseMyCurrentTrainingReservations(body)
            when {
                items.isNotEmpty() -> MyTrainingCurrentReservationFetchResult(items = items)
                "cas/login" in finalUrl || "统一身份认证" in body -> {
                    MyTrainingCurrentReservationFetchResult(message = "当前登录态已失效，请重新登录")
                }
                isMyCurrentTrainingReservationsPage(body) -> {
                    MyTrainingCurrentReservationFetchResult(message = "当前暂无预约")
                }
                else -> MyTrainingCurrentReservationFetchResult(message = "已请求当前预约，但未解析到列表数据")
            }
        }.getOrElse { error ->
            Log.e("MyReservationsScreen", "获取当前预约失败: ${error.message}", error)
            MyTrainingCurrentReservationFetchResult(message = error.message ?: "获取当前预约失败")
        }
    }
}

fun parseMyCurrentTrainingReservations(html: String): List<MyTrainingReservationItem> {
    if (!isMyCurrentTrainingReservationsPage(html)) return emptyList()

    return readJavascriptArray(html, "moreroombesklist").map { item ->
        MyTrainingReservationItem(
            status = if (item.optInt("isCheck", 0) == 0) "待审核" else "已审核",
            roomName = item.optString("roomname"),
            createdAt = item.optString("committime").take(16),
            useDate = item.optString("useday"),
            startTime = item.optString("begintime"),
            endDate = item.optString("useendday"),
            endTime = item.optString("endtime"),
            id = item.optString("id"),
            cancelType = MyTrainingReservationCancelType.MULTI
        )
    }
}

suspend fun cancelMyTrainingReservation(
    context: Context,
    item: MyTrainingReservationItem
): MyTrainingReservationCancelResult {
    val firstAttempt = cancelMyTrainingReservationOnce(context, item)
    if (firstAttempt.success || firstAttempt.message != "当前登录态已失效，请重新登录") {
        return firstAttempt
    }
    if (!AuthSessionManager.isSsoLogin(context)) {
        return firstAttempt
    }

    val renewResult = SsoLoginService.trySilentRefresh(context)
    if (!renewResult.success) {
        return MyTrainingReservationCancelResult(
            success = false,
            message = renewResult.message ?: "当前登录态已失效，请重新登录"
        )
    }
    return cancelMyTrainingReservationOnce(context, item)
}

private suspend fun cancelMyTrainingReservationOnce(
    context: Context,
    item: MyTrainingReservationItem
): MyTrainingReservationCancelResult {
    if (item.id.isBlank() || item.cancelType == MyTrainingReservationCancelType.NONE) {
        return MyTrainingReservationCancelResult(false, "当前预约缺少取消参数")
    }

    return withContext(Dispatchers.IO) {
        AuthSessionManager.install(context)
        runCatching {
            val (url, parameterName) = when (item.cancelType) {
                MyTrainingReservationCancelType.MULTI -> MY_TRAINING_DELETE_MORE_URL to "deleteid"
                MyTrainingReservationCancelType.NONE -> return@runCatching MyTrainingReservationCancelResult(
                    false,
                    "当前预约不可取消"
                )
            }
            val requestBody = "$parameterName=${URLEncoder.encode(item.id, "UTF-8")}"
            val cookieHeader = AuthSessionManager.weixinlibCookieHeader(context)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", WEIXINLIB_WEB_USER_AGENT)
                setRequestProperty("Referer", MY_TRAINING_CURRENT_URL)
                setRequestProperty("Origin", "https://weixinlib.lut.edu.cn")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                cookieHeader?.let { setRequestProperty("Cookie", it) }
            }
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val finalUrl = connection.url.toString()

            when {
                "cas/login" in finalUrl || "统一身份认证" in body -> {
                    MyTrainingReservationCancelResult(false, "当前登录态已失效，请重新登录")
                }
                else -> {
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val success = json?.optInt("value") == 1
                    MyTrainingReservationCancelResult(
                        success = success,
                        message = json?.optString("msg")?.takeIf { it.isNotBlank() }
                            ?: if (success) "取消成功" else "取消失败"
                    )
                }
            }
        }.getOrElse { error ->
            Log.e("MyReservationsScreen", "取消当前预约失败: ${error.message}", error)
            MyTrainingReservationCancelResult(false, error.message ?: "取消当前预约失败")
        }
    }
}

private fun isMyCurrentTrainingReservationsPage(html: String): Boolean {
    return html.contains("trainingroombeskinfor") ||
        html.contains("moreroombesklist") ||
        html.contains("我的研讨间预约")
}

private fun readJavascriptArray(html: String, variableName: String): List<org.json.JSONObject> {
    val rawArray = Regex(
        pattern = """var\s+$variableName\s*=\s*(\[.*?]);""",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    ).find(html)?.groupValues?.getOrNull(1) ?: return emptyList()

    return runCatching {
        val array = JSONArray(rawArray)
        buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
}

fun parseMyTrainingUseLogPage(
    html: String,
    requestedPageNo: Int = 0,
    requestedPageSize: Int = 10
): MyTrainingReservationPage? {
    val hasUseLogMarker =
        html.contains("traininguselog") || html.contains("操作类型")
    if (!hasUseLogMarker) return null

    val useLogTable = Regex(
        pattern = """<table[^>]*table_type_7[^>]*>.*?</table>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(html)
        .map { it.value }
        .firstOrNull { it.contains("操作类型") }
        ?: return null

    val tbodyContent = Regex(
        pattern = """<tbody[^>]*>(.*?)</tbody>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(useLogTable)?.groupValues?.getOrNull(1).orEmpty()

    val rows = Regex(
        pattern = """<tr[^>]*>(.*?)</tr>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(tbodyContent)
        .mapNotNull { match ->
            val cells = Regex(
                pattern = """<td[^>]*>(.*?)</td>""",
                options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).findAll(match.groupValues[1])
                .map { cell -> htmlToPlainText(cell.groupValues[1]) }
                .toList()
            if (cells.size < 6) return@mapNotNull null
            MyTrainingReservationItem(
                status = normalizeUseLogStatus(cells[5]),
                roomName = cells[0],
                createdAt = cells[1],
                useDate = cells[2],
                startTime = cells[3],
                endDate = cells[2],
                endTime = cells[4]
            )
        }
        .toList()

    val beginDate = readInputValue(html, "begintime").orEmpty()
    val endDate = readInputValue(html, "endtime").orEmpty()
    val currentPage = readInputValue(html, "currentPage")?.toIntOrNull()?.minus(1)
    val pageSize = readInputValue(html, "pageSize")?.toIntOrNull()
    val totalCount = Regex("""共有[:：]\s*(\d+)\s*条记录""")
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

    return MyTrainingReservationPage(
        items = rows,
        beginDate = beginDate,
        endDate = endDate,
        pageNo = currentPage?.coerceAtLeast(0) ?: requestedPageNo,
        pageSize = pageSize?.coerceAtLeast(1) ?: requestedPageSize,
        totalCount = totalCount ?: rows.size
    )
}

private fun normalizeUseLogStatus(raw: String): String {
    return when (raw.trim()) {
        "借出" -> "借出"
        else -> "已取消"
    }
}

fun displayMyTrainingReservationStatus(
    item: MyTrainingReservationItem,
    now: LocalDateTime = LocalDateTime.now()
): String {
    if (item.status != "借出") return item.status
    val endDateTime = parseReservationEndDateTime(item) ?: return item.status
    return if (!endDateTime.isAfter(now)) "已结束" else item.status
}

private fun isCancelledTrainingReservation(item: MyTrainingReservationItem): Boolean {
    return displayMyTrainingReservationStatus(item) == "已取消"
}

private fun parseReservationEndDateTime(item: MyTrainingReservationItem): LocalDateTime? {
    val date = parseFlexibleDate(item.endDate) ?: parseFlexibleDate(item.useDate)
        ?: return null
    val time = parseFlexibleTime(item.endTime) ?: return null
    return LocalDateTime.of(date, time)
}

private fun parseFlexibleTime(raw: String): LocalTime? {
    if (raw.isBlank()) return null
    val value = raw.trim()
    return listOf("H:m:s", "H:m").firstNotNullOfOrNull { pattern ->
        runCatching {
            LocalTime.parse(value, DateTimeFormatter.ofPattern(pattern))
        }.getOrNull()
    }
}

private fun readInputValue(html: String, id: String): String? {
    val regex = Regex(
        pattern = "id=\"$id\"[^>]*value=\"([^\"]*)\"",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    return regex.find(html)?.groupValues?.getOrNull(1)?.trim()
}

private fun htmlToPlainText(raw: String): String {
    return raw
        .replace(Regex("""<[^>]+>"""), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun parseFlexibleDate(raw: String): LocalDate? {
    if (raw.isBlank()) return null
    return runCatching {
        LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy-M-d"))
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyReservationsScreen(
    refreshKey: Int,
    onOpenLogin: (Int) -> Unit,
    state: MyTrainingReservationsState = rememberMyTrainingReservationsState()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zoneId = remember { ZoneId.systemDefault() }

    fun requestPage(targetPageNo: Int, append: Boolean) {
        if (state.beginDate.isAfter(state.endDate)) {
            state.message = "开始日期不能晚于结束日期"
            return
        }
        if (state.isLoading || state.isAppending) return
        if (!append) {
            state.hasQueried = true
        }
        scope.launch {
            if (append) {
                state.isAppending = true
            } else {
                state.isLoading = true
                state.message = null
            }
            val result = fetchMyTrainingReservations(
                context = context,
                beginDate = state.beginDateText(),
                endDate = state.endDateText(),
                pageNo = targetPageNo,
                pageSize = state.pageSize
            )
            result.page?.let { page ->
                state.totalCount = page.totalCount
                state.pageNo = page.pageNo
                state.pageSize = page.pageSize
                parseFlexibleDate(page.beginDate)?.let { state.beginDate = it }
                parseFlexibleDate(page.endDate)?.let { state.endDate = it }
                state.items = if (append) state.items + page.items else page.items
                state.canLoadMore = if (append && page.items.isEmpty() && page.totalCount > state.items.size) {
                    false
                } else {
                    true
                }
                state.message = when {
                    append && page.items.isEmpty() && page.totalCount > state.items.size ->
                        "下一页没有解析出数据，已停止自动加载"
                    state.items.isNotEmpty() -> null
                    page.totalCount > 0 -> "已解析到总记录数，但未解析到当前页列表，页面结构可能变了"
                    else -> "当前时间范围内暂无使用记录"
                }
            } ?: run {
                if (!append) {
                    state.items = emptyList()
                    state.totalCount = 0
                    state.canLoadMore = true
                } else {
                    state.canLoadMore = false
                }
                state.message = if (append) {
                    result.message ?: "下一页加载失败，已停止自动加载"
                } else {
                    result.message ?: "获取我的预约失败"
                }
            }
            if (append) {
                state.isAppending = false
            } else {
                state.isLoading = false
            }
        }
    }

    fun reloadFromFirstPage() {
        state.pageNo = 0
        state.totalCount = 0
        state.items = emptyList()
        state.canLoadMore = true
        requestPage(targetPageNo = 0, append = false)
    }

    fun loadNextPage() {
        if (!state.hasMore()) return
        requestPage(targetPageNo = state.pageNo + 1, append = true)
    }

    fun requestCurrentReservations() {
        if (state.currentIsLoading) return
        scope.launch {
            state.currentIsLoading = true
            state.currentMessage = null
            val result = fetchMyCurrentTrainingReservations(context)
            state.currentItems = result.items
            state.currentMessage = when {
                result.items.isNotEmpty() -> null
                result.message != null -> result.message
                else -> "当前暂无预约"
            }
            state.currentIsLoading = false
        }
    }

    LaunchedEffect(refreshKey) {
        state.currentItems = emptyList()
        state.currentMessage = null
        requestCurrentReservations()
    }

    val showLoginButton = state.message?.contains("登录") == true
    val showCurrentLoginButton = state.currentMessage?.contains("登录") == true
    var presetExpanded by remember { mutableStateOf(false) }
    var showBeginDatePickerDialog by remember { mutableStateOf(false) }
    var showEndDatePickerDialog by remember { mutableStateOf(false) }
    var pendingCancelItem by remember { mutableStateOf<MyTrainingReservationItem?>(null) }
    var cancelResultDialog by remember { mutableStateOf<MyTrainingReservationCancelResult?>(null) }

    fun cancelCurrentReservation(item: MyTrainingReservationItem) {
        if (state.cancellingReservationId != null) return
        scope.launch {
            state.cancellingReservationId = item.id
            state.currentMessage = null
            val result = cancelMyTrainingReservation(context, item)
            if (result.success) {
                state.currentItems = state.currentItems.filterNot {
                    it.id == item.id && it.cancelType == item.cancelType
                }
            }
            cancelResultDialog = result
            state.cancellingReservationId = null
        }
    }

    fun applyPreset(preset: ReservationDatePreset) {
        state.selectedPreset = preset
        if (preset == ReservationDatePreset.CUSTOM) return
        val (beginDate, endDate) = dateRangeForPreset(preset)
        state.beginDate = beginDate
        state.endDate = endDate
    }

    fun openBeginDatePicker() {
        showBeginDatePickerDialog = true
    }

    fun openEndDatePicker() {
        showEndDatePickerDialog = true
    }

    if (showBeginDatePickerDialog) {
        val beginDatePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = state.beginDate
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showBeginDatePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val millis = beginDatePickerState.selectedDateMillis
                        if (millis != null) {
                            state.beginDate = Instant.ofEpochMilli(millis)
                                .atZone(zoneId)
                                .toLocalDate()
                            state.selectedPreset = ReservationDatePreset.CUSTOM
                        }
                        showBeginDatePickerDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBeginDatePickerDialog = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = beginDatePickerState)
        }
    }

    if (showEndDatePickerDialog) {
        val endDatePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = state.endDate
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val millis = endDatePickerState.selectedDateMillis
                        if (millis != null) {
                            state.endDate = Instant.ofEpochMilli(millis)
                                .atZone(zoneId)
                                .toLocalDate()
                            state.selectedPreset = ReservationDatePreset.CUSTOM
                        }
                        showEndDatePickerDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEndDatePickerDialog = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = endDatePickerState)
        }
    }

    pendingCancelItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                if (state.cancellingReservationId == null) {
                    pendingCancelItem = null
                }
            },
            title = { Text("取消当前预约") },
            text = { Text("确认取消 ${item.roomName} 的当前预约？") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingCancelItem = null
                        cancelCurrentReservation(item)
                    },
                    enabled = state.cancellingReservationId == null
                ) {
                    Text("确认取消")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingCancelItem = null },
                    enabled = state.cancellingReservationId == null
                ) {
                    Text("再想想")
                }
            }
        )
    }

    cancelResultDialog?.let { result ->
        AlertDialog(
            onDismissRequest = { cancelResultDialog = null },
            title = { Text(if (result.success) "取消成功" else "取消失败") },
            text = { Text(result.message) },
            confirmButton = {
                Button(onClick = { cancelResultDialog = null }) {
                    Text("确定")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "当前预约",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = ::requestCurrentReservations,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.currentIsLoading
                    ) {
                        Text("刷新当前预约")
                    }
                }
            }
        }

        when {
            state.currentIsLoading && state.currentItems.isEmpty() -> {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            }
            state.currentItems.isNotEmpty() -> {
                itemsIndexed(state.currentItems) { _, item ->
                    ReservationHistoryCard(
                        item = item,
                        onCancel = if (
                            item.id.isNotBlank() &&
                            item.cancelType != MyTrainingReservationCancelType.NONE
                        ) {
                            { pendingCancelItem = item }
                        } else {
                            null
                        },
                        isCancelling = state.cancellingReservationId == item.id
                    )
                }
                state.currentMessage?.let { message ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            else -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = state.currentMessage ?: "当前暂无预约",
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (showCurrentLoginButton) {
                                Button(
                                    onClick = { onOpenLogin(LoginActivity.TAB_LIBRARY) }
                                ) {
                                    Text("重新登录")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "研讨间使用记录",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    ExposedDropdownMenuBox(
                        expanded = presetExpanded,
                        onExpandedChange = { presetExpanded = !presetExpanded }
                    ) {
                        OutlinedTextField(
                            value = state.selectedPreset.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("时间范围") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            enabled = !state.isLoading && !state.isAppending
                        )
                        DropdownMenu(
                            expanded = presetExpanded,
                            onDismissRequest = { presetExpanded = false }
                        ) {
                            reservationDatePresetOptions.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.label) },
                                    onClick = {
                                        presetExpanded = false
                                        applyPreset(preset)
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = ::openBeginDatePicker,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("开始 ${state.beginDateText()}")
                        }
                        OutlinedButton(
                            onClick = ::openEndDatePicker,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("结束 ${state.endDateText()}")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = ::reloadFromFirstPage,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading && !state.isAppending
                        ) {
                            Text("查询")
                        }
                    }
                    if (state.hasQueried) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "该时间段共有 ${state.totalCount} 条数据",
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.clickable(
                                    enabled = !state.isLoading && !state.isAppending,
                                    onClick = { state.hideCancelled = !state.hideCancelled }
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CompositionLocalProvider(
                                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                                ) {
                                    Checkbox(
                                        checked = state.hideCancelled,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(20.dp),
                                        enabled = !state.isLoading && !state.isAppending
                                    )
                                }
                                Text(
                                    text = "隐藏已取消",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        when {
            state.isLoading && state.items.isEmpty() -> {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            }
            state.items.isNotEmpty() -> {
                val hasVisibleItems = state.items.any { item ->
                    !state.hideCancelled || !isCancelledTrainingReservation(item)
                }
                if (!hasVisibleItems) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = "已隐藏全部已取消记录",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                itemsIndexed(state.items) { index, item ->
                    if (!state.hideCancelled || !isCancelledTrainingReservation(item)) {
                        ReservationHistoryCard(item)
                    }
                    if (
                        index == state.items.lastIndex &&
                        state.hasMore() &&
                        !state.isLoading &&
                        !state.isAppending
                    ) {
                        LaunchedEffect(state.items.size, state.pageNo, state.totalCount) {
                            loadNextPage()
                        }
                    }
                }
                if (state.isAppending) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
                if (!state.hasMore()) {
                    item {
                        Text(
                            text = "已加载全部使用记录",
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.message != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = state.message.orEmpty(),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            !state.hasQueried -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "请选择时间范围后点击查询",
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            else -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = state.message ?: "暂无使用记录",
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (showLoginButton) {
                                Button(
                                    onClick = { onOpenLogin(LoginActivity.TAB_LIBRARY) }
                                ) {
                                    Text("重新登录")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationHistoryCard(
    item: MyTrainingReservationItem,
    onCancel: (() -> Unit)? = null,
    isCancelling: Boolean = false
) {
    val displayStatus = displayMyTrainingReservationStatus(item)
    val isCancelled = displayStatus == "已取消"
    val cardAlpha = if (isCancelled) 0.56f else 1f
    val statusColor = if (isCancelled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.roomName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = displayStatus,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
            ReservationHistoryField("创建时间", item.createdAt)
            ReservationHistoryField("使用时间", "${item.useDate} ${item.startTime}")
            ReservationHistoryField("结束时间", "${item.endDate} ${item.endTime}")
            if (onCancel != null) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCancelling
                ) {
                    Text(if (isCancelling) "取消中..." else "取消预约")
                }
            }
        }
    }
}

@Composable
private fun ReservationHistoryField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

package com.trainingroom.book

import android.app.DatePickerDialog
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MY_TRAINING_HISTORY_URL = "https://weixinlib.lut.edu.cn/moretraingroombesklog"
private const val WEIXINLIB_WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"

data class MyTrainingReservationItem(
    val status: String,
    val roomName: String,
    val createdAt: String,
    val useDate: String,
    val startTime: String,
    val endDate: String,
    val endTime: String
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
            val url = URL(
                "$MY_TRAINING_HISTORY_URL?begintime=$beginDate&endtime=$endDate&pageNo=$pageNo&pageSize=$pageSize"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
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
            val page = parseMyTrainingReservationsPage(
                html = body,
                requestedPageNo = pageNo,
                requestedPageSize = pageSize
            )
            when {
                page != null -> MyTrainingReservationFetchResult(page = page)
                "cas/login" in finalUrl || "统一身份认证" in body -> {
                    MyTrainingReservationFetchResult(message = "当前登录态已失效，请重新登录")
                }
                else -> MyTrainingReservationFetchResult(message = "已请求预约历史，但未解析到列表数据")
            }
        }.getOrElse { error ->
            Log.e("MyReservationsScreen", "获取我的预约失败: ${error.message}", error)
            MyTrainingReservationFetchResult(message = error.message ?: "获取我的预约失败")
        }
    }
}

fun parseMyTrainingReservationsPage(
    html: String,
    requestedPageNo: Int = 0,
    requestedPageSize: Int = 10
): MyTrainingReservationPage? {
    val hasHistoryMarker =
        html.contains("moretraingroombesklog") || html.contains("研讨间预约历史")
    if (!hasHistoryMarker) return null

    val tabStart = html.indexOf("<div id=\"tab2\">")
        .takeIf { it >= 0 }
        ?: html.indexOf("id=\"tab2\"")
    val searchRoot = if (tabStart >= 0) html.substring(tabStart) else html

    val tableStart = searchRoot.indexOf("<table class=\"table_type_7")
        .takeIf { it >= 0 }
        ?: searchRoot.indexOf("table_type_7")
    val tableScope = if (tableStart >= 0) searchRoot.substring(tableStart) else searchRoot

    val tbodyContent = Regex(
        pattern = """<tbody[^>]*>(.*?)</tbody>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(tableScope)?.groupValues?.getOrNull(1).orEmpty()

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
            if (cells.size < 7) return@mapNotNull null
            MyTrainingReservationItem(
                status = cells[0],
                roomName = cells[1],
                createdAt = cells[2],
                useDate = cells[3],
                startTime = cells[4],
                endDate = cells[5],
                endTime = cells[6]
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

@Composable
fun MyReservationsScreen(
    refreshKey: Int,
    onOpenLogin: (Int) -> Unit,
    state: MyTrainingReservationsState = rememberMyTrainingReservationsState()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun requestPage(targetPageNo: Int, append: Boolean) {
        if (state.beginDate.isAfter(state.endDate)) {
            state.message = "开始日期不能晚于结束日期"
            return
        }
        if (state.isLoading || state.isAppending) return
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
                    else -> "当前时间范围内暂无预约记录"
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

    LaunchedEffect(refreshKey) {
        reloadFromFirstPage()
    }
    val showLoginButton = state.message?.contains("登录") == true

    fun openBeginDatePicker() {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                state.beginDate = LocalDate.of(year, month + 1, dayOfMonth)
            },
            state.beginDate.year,
            state.beginDate.monthValue - 1,
            state.beginDate.dayOfMonth
        ).show()
    }

    fun openEndDatePicker() {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                state.endDate = LocalDate.of(year, month + 1, dayOfMonth)
            },
            state.endDate.year,
            state.endDate.monthValue - 1,
            state.endDate.dayOfMonth
        ).show()
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
                        text = "研讨间预约历史",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                    Text(
                        text = "该时间段共有 ${state.totalCount} 条数据",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            state.items.isNotEmpty() -> {
                itemsIndexed(state.items) { index, item ->
                    ReservationHistoryCard(item)
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
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                if (!state.hasMore()) {
                    item {
                        Text(
                            text = "已加载全部预约记录",
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
                                text = state.message ?: "暂无预约记录",
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
private fun ReservationHistoryCard(item: MyTrainingReservationItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = item.status,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            ReservationHistoryField("创建时间", item.createdAt)
            ReservationHistoryField("使用时间", "${item.useDate} ${item.startTime}")
            ReservationHistoryField("结束时间", "${item.endDate} ${item.endTime}")
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

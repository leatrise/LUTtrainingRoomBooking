package com.trainingroom.book

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trainingroom.book.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class BookingEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val room = intent.getParcelableExtra<ConferenceRoom>(EXTRA_ROOM)
        if (room == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BookingEntryScreen(
                        room = room,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ROOM = "booking_room"

        fun createIntent(context: Context, room: ConferenceRoom): Intent =
            Intent(context, BookingEntryActivity::class.java).apply {
                putExtra(EXTRA_ROOM, room)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingEntryScreen(
    room: ConferenceRoom,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentLoginUserCode = AuthSessionManager.getLoggedInUserInfo()
        ?.userCode
        ?.trim()
        ?.ifBlank { null }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(LocalTime.of(14, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(16, 0)) }
    var timeValidationState by remember {
        mutableStateOf(BookingTimeValidationState.idle("请选择预约日期和时间"))
    }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showStartTimePickerDialog by remember { mutableStateOf(false) }
    var showEndTimePickerDialog by remember { mutableStateOf(false) }
    var showCardPickerSheet by remember { mutableStateOf(false) }
    var availableCards by remember { mutableStateOf(loadBookingSavedCards(context)) }
    var selectedCardIds by remember { mutableStateOf(setOf<String>()) }
    val selectableCardIds = remember(availableCards) {
        availableCards.filter { it.isSelectable() }.map { it.studentId }.toSet()
    }
    val lockedSelectedCardIds = remember(availableCards, currentLoginUserCode) {
        availableCards
            .filter { it.studentId == currentLoginUserCode && it.isSelectable() }
            .map { it.studentId }
            .toSet()
    }
    val effectiveSelectedCardIds = remember(selectedCardIds, lockedSelectedCardIds, selectableCardIds) {
        (selectedCardIds intersect selectableCardIds) + lockedSelectedCardIds
    }
    val selectedCards = remember(availableCards, effectiveSelectedCardIds) {
        availableCards.filter { it.studentId in effectiveSelectedCardIds }
    }
    val selectedCardCount = selectedCards.size
    val peopleRequirementMessage = remember(selectedCardCount, room.minCapacity, room.maxCapacity) {
        when {
            selectedCardCount < room.minCapacity ->
                "当前 $selectedCardCount 人，还需 ${room.minCapacity - selectedCardCount} 人"
            selectedCardCount > room.maxCapacity ->
                "当前 $selectedCardCount 人，超出 ${selectedCardCount - room.maxCapacity} 人"
            else ->
                "当前 $selectedCardCount 人"
        }
    }
    val peopleRequirementColor = when {
        selectedCardCount < room.minCapacity -> MaterialTheme.colorScheme.error
        selectedCardCount > room.maxCapacity -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val pickerOrderedCards = remember(availableCards) {
        availableCards.sortedWith(
            compareBy<BookingUseCard> { !it.isSelectable() }
                .thenBy { it.sort }
        )
    }
    val cardPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(lockedSelectedCardIds, selectableCardIds) {
        val normalizedSelected = (selectedCardIds intersect selectableCardIds) + lockedSelectedCardIds
        if (normalizedSelected != selectedCardIds) {
            selectedCardIds = normalizedSelected
        }
    }

    LaunchedEffect(room.id, selectedDate, startTime, endTime) {
        val localValidationResult = validateBookingTimeLocally(selectedDate, startTime, endTime)
        if (localValidationResult != null) {
            timeValidationState = localValidationResult
            return@LaunchedEffect
        }

        timeValidationState = BookingTimeValidationState.loading("正在检查该时间段是否可预约...")
        delay(250)
        timeValidationState = validateBookingTimeRemotely(
            context = context,
            roomId = room.id,
            selectedDate = selectedDate,
            startTime = startTime,
            endTime = endTime
        )
    }

    if (showCardPickerSheet) {
        var tempSelectedIds by remember(effectiveSelectedCardIds, selectableCardIds, lockedSelectedCardIds) {
            mutableStateOf(
                (effectiveSelectedCardIds intersect selectableCardIds) + lockedSelectedCardIds
            )
        }
        ModalBottomSheet(
            onDismissRequest = { showCardPickerSheet = false },
            sheetState = cardPickerSheetState
        ) {
            BookingCardPickerSheetContent(
                onOpenMyCards = {
                    context.startActivity(MyCardsActivity.createIntent(context))
                },
                availableCards = pickerOrderedCards,
                selectedCardIds = tempSelectedIds,
                lockedCardIds = lockedSelectedCardIds,
                onToggleCard = { cardId ->
                    if (cardId !in lockedSelectedCardIds && cardId in selectableCardIds) {
                        tempSelectedIds = tempSelectedIds.toggle(cardId)
                    }
                },
                onDismiss = { showCardPickerSheet = false },
                onConfirm = {
                    selectedCardIds = (tempSelectedIds intersect selectableCardIds) + lockedSelectedCardIds
                    showCardPickerSheet = false
                }
            )
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
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
        val startTimePickerState = androidx.compose.material3.rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour = true
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showStartTimePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        startTime = LocalTime.of(
                            startTimePickerState.hour,
                            startTimePickerState.minute
                        )
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
        val endTimePickerState = androidx.compose.material3.rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour = true
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEndTimePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        endTime = LocalTime.of(
                            endTimePickerState.hour,
                            endTimePickerState.minute
                        )
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("预约房间", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(room.name, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "容量: ${room.minCapacity} - ${room.maxCapacity} 人",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("预约时间", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = selectedDate.format(dateFormatter),
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePickerDialog = true },
                        readOnly = true,
                        label = { Text("日期") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePickerDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = "选择日期"
                                )
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = startTime.format(timeFormatter),
                            onValueChange = {},
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showStartTimePickerDialog = true },
                            readOnly = true,
                            label = { Text("开始时间") },
                            trailingIcon = {
                                IconButton(onClick = { showStartTimePickerDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.AccessTime,
                                        contentDescription = "选择开始时间"
                                    )
                                }
                            }
                        )
                        OutlinedTextField(
                            value = endTime.format(timeFormatter),
                            onValueChange = {},
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showEndTimePickerDialog = true },
                            readOnly = true,
                            label = { Text("结束时间") },
                            trailingIcon = {
                                IconButton(onClick = { showEndTimePickerDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.AccessTime,
                                        contentDescription = "选择结束时间"
                                    )
                                }
                            }
                        )
                    }
                    BookingTimeValidationIndicator(
                        state = timeValidationState,
                        dateText = selectedDate.format(dateFormatter),
                        startTimeText = startTime.format(timeFormatter),
                        endTimeText = endTime.format(timeFormatter)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "使用卡片",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = peopleRequirementMessage,
                            fontSize = 12.sp,
                            color = peopleRequirementColor
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            availableCards = loadBookingSavedCards(context)
                            showCardPickerSheet = true
                        },
                        modifier = Modifier.fillMaxWidth()
                        ) {
                        Icon(
                            imageVector = Icons.Filled.CreditCard,
                            contentDescription = null
                        )
                        Text("选择卡片", modifier = Modifier.padding(start = 8.dp))
                    }

                    if (selectedCards.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = when {
                                    availableCards.isEmpty() -> "暂无已保存卡片"
                                    selectableCardIds.isEmpty() -> "暂无联网验证成功的可选卡片"
                                    else -> "暂未选择使用卡片"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedCards.forEachIndexed { index, card ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = card.displayTitle(),
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (index == 0) "主卡" else "成员",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = card.displaySummary(),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "提交前请确认日期、时间段与使用卡片信息。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f)
                    )
                }
            }

            Button(
                onClick = {},
                enabled = timeValidationState.isBookable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (timeValidationState.isLoading) "校验中..." else "提交预约",
                    fontSize = 16.sp
                )
            }
        }
    }
}

private enum class BookingTimeValidationTone {
    Info,
    Success,
    Warning
}

private data class BookingTimeValidationState(
    val tone: BookingTimeValidationTone,
    val message: String,
    val detail: String? = null,
    val isLoading: Boolean = false,
    val isBookable: Boolean = false
) {
    companion object {
        fun idle(message: String) = BookingTimeValidationState(
            tone = BookingTimeValidationTone.Info,
            message = message
        )

        fun loading(message: String) = BookingTimeValidationState(
            tone = BookingTimeValidationTone.Info,
            message = message,
            isLoading = true
        )

        fun success(message: String, detail: String? = null) = BookingTimeValidationState(
            tone = BookingTimeValidationTone.Success,
            message = message,
            detail = detail,
            isBookable = true
        )

        fun warning(message: String, detail: String? = null) = BookingTimeValidationState(
            tone = BookingTimeValidationTone.Warning,
            message = message,
            detail = detail
        )
    }
}

@Composable
private fun BookingTimeValidationIndicator(
    state: BookingTimeValidationState,
    dateText: String,
    startTimeText: String,
    endTimeText: String
) {
    val (containerColor, contentColor, icon) = when (state.tone) {
        BookingTimeValidationTone.Info -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Filled.Info
        )
        BookingTimeValidationTone.Success -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF1B5E20),
            Icons.Filled.CheckCircle
        )
        BookingTimeValidationTone.Warning -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.ErrorOutline
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor
                )
                Text(
                    text = state.message,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            state.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    color = contentColor.copy(alpha = 0.82f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

private data class BookingUseCard(
    val studentId: String,
    val name: String,
    val note: String,
    val userUnit: String = "",
    val userType: String = "",
    val sort: Int = 0,
    val verificationStatus: String = "",
    val verificationMessage: String? = null
)

private const val BOOKING_CARD_PREFS_NAME = "saved_cards"
private const val BOOKING_KEY_CARDS_JSON = "cards_json"
private const val BOOKING_TIME_VALIDATE_URL = "https://weixinlib.lut.edu.cn/getYY"
private const val BOOKING_SOURCE_PAGE_URL = "https://weixinlib.lut.edu.cn/moretrainingroombesk"
private val BOOKING_OPEN_TIME: LocalTime = LocalTime.of(8, 0)
private val BOOKING_CLOSE_TIME: LocalTime = LocalTime.of(22, 0)
private val BOOKING_MIN_END_TIME: LocalTime = LocalTime.of(9, 0)
private val BOOKING_LATEST_START_TIME: LocalTime = LocalTime.of(21, 0)
private const val BOOKING_REQUEST_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"

private fun loadBookingSavedCards(context: Context): List<BookingUseCard> {
    val raw = context.getSharedPreferences(BOOKING_CARD_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(BOOKING_KEY_CARDS_JSON, null)
        .orEmpty()
    if (raw.isBlank()) return emptyList()

    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val studentId = item.optString("studentId").trim()
                if (studentId.isBlank()) continue
                add(
                    BookingUseCard(
                        studentId = studentId,
                        name = item.optString("name").trim(),
                        note = item.optString("note").trim(),
                        userUnit = item.optString("userUnit").trim(),
                        userType = item.optString("userType").trim(),
                        sort = item.optInt("sort", index + 1),
                        verificationStatus = item.optString("verificationStatus").trim(),
                        verificationMessage = item.optString("verificationMessage").trim().ifBlank { null }
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
        .sortedBy { it.sort }
}

private fun Set<String>.toggle(studentId: String): Set<String> {
    return if (studentId in this) this - studentId else this + studentId
}

private fun BookingUseCard.displayTitle(): String {
    return note.ifBlank { name.ifBlank { studentId } }
}

private fun BookingUseCard.displaySummary(): String {
    val parts = mutableListOf(studentId)
    if (note.isNotBlank() && name.isNotBlank()) {
        parts += name
    }
    if (userUnit.isNotBlank()) {
        parts += userUnit
    }
    if (userType.isNotBlank()) {
        parts += userType
    }
    return parts.joinToString(" · ")
}

private fun BookingUseCard.isSelectable(): Boolean = verificationStatus == "Verified"

private fun validateBookingTimeLocally(
    selectedDate: LocalDate,
    startTime: LocalTime,
    endTime: LocalTime
): BookingTimeValidationState? {
    val startAt = LocalDateTime.of(selectedDate, startTime)
    val endAt = LocalDateTime.of(selectedDate, endTime)
    val now = LocalDateTime.now()

    return when {
        startTime.isBefore(BOOKING_OPEN_TIME) || endTime.isAfter(BOOKING_CLOSE_TIME) ->
            BookingTimeValidationState.warning("预约时间需在图书馆开放时间 08:00 - 22:00 内")
        !startTime.isBefore(BOOKING_LATEST_START_TIME) ->
            BookingTimeValidationState.warning("开始时间必须早于 21:00")
        endTime.isBefore(BOOKING_MIN_END_TIME) ->
            BookingTimeValidationState.warning("结束时间必须不早于 09:00")
        !endAt.isAfter(startAt) -> BookingTimeValidationState.warning("结束时间需晚于开始时间")
        !startAt.isAfter(now) -> BookingTimeValidationState.warning("开始日期及时间应大于现在的时间")
        else -> null
    }
}

private suspend fun validateBookingTimeRemotely(
    context: Context,
    roomId: String,
    selectedDate: LocalDate,
    startTime: LocalTime,
    endTime: LocalTime
): BookingTimeValidationState {
    return withContext(Dispatchers.IO) {
        AuthSessionManager.install(context)
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val rawCookieHeader = AuthSessionManager.weixinlibCookieHeader(context)

        val query = buildString {
            append("roomId=")
            append(URLEncoder.encode(roomId, "UTF-8"))
            append("&beginDay=")
            append(URLEncoder.encode(selectedDate.format(dateFormatter), "UTF-8"))
            append("&beginTime=")
            append(URLEncoder.encode(startTime.format(timeFormatter), "UTF-8"))
            append("&endDay=")
            append(URLEncoder.encode(selectedDate.format(dateFormatter), "UTF-8"))
            append("&endTime=")
            append(URLEncoder.encode(endTime.format(timeFormatter), "UTF-8"))
        }

        runCatching {
            val connection = (URL("$BOOKING_TIME_VALIDATE_URL?$query").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
                setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                setRequestProperty("Referer", "$BOOKING_SOURCE_PAGE_URL?id=$roomId")
                setRequestProperty("User-Agent", BOOKING_REQUEST_USER_AGENT)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                rawCookieHeader?.let { setRequestProperty("Cookie", it) }
            }

            val responseText = (if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (isBookingLoginLikeResponse(responseText, connection.url.toString())) {
                return@runCatching BookingTimeValidationState.warning("当前登录已失效，请重新登录后再校验预约时间")
            }

            val payload = JSONObject(responseText)
            val isValid = payload.optString("flagSign").equals("true", ignoreCase = true)
            val serverMessage = payload.optString("strReturn").trim().ifBlank { null }

            if (isValid) {
                BookingTimeValidationState.success(
                    message = "当前时间段可预约",
                    detail = "已通过预约时间合法性检查"
                )
            } else {
                BookingTimeValidationState.warning(
                    message = serverMessage ?: "当前时间段不可预约",
                    detail = "请调整预约日期或时间后重试"
                )
            }
        }.getOrElse { error ->
            BookingTimeValidationState.warning(
                message = "预约时间校验失败",
                detail = error.message ?: "请稍后重试"
            )
        }
    }
}

private fun isBookingLoginLikeResponse(body: String, finalUrl: String): Boolean {
    return "cas/login" in finalUrl ||
        "读者登录" in body ||
        "统一身份认证" in body ||
        "action=\"login\"" in body
}

@Composable
private fun BookingCardPickerSheetContent(
    onOpenMyCards: () -> Unit,
    availableCards: List<BookingUseCard>,
    selectedCardIds: Set<String>,
    lockedCardIds: Set<String>,
    onToggleCard: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "选择卡片",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(onClick = onOpenMyCards) {
                Text("我的卡包")
            }
        }
        Text(
            text = "勾选需要参与本次预约的卡片信息。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (availableCards.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text(
                    text = "“我的卡包”中还没有可用卡片，请先添加",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableCards, key = { it.studentId }) { card ->
                    val isLocked = card.studentId in lockedCardIds
                    val isSelectable = card.isSelectable()
                    val isInteractive = isSelectable && !isLocked
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isInteractive) { onToggleCard(card.studentId) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = card.studentId in selectedCardIds,
                                enabled = isInteractive,
                                onCheckedChange = { onToggleCard(card.studentId) }
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                Text(
                                    text = card.displayTitle(),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = card.displaySummary(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                if (isLocked) {
                                    Text(
                                        text = "当前登录人，不可取消",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (!isSelectable) {
                                    Text(
                                        text = card.verificationMessage ?: "未联网验证成功，暂不可选",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text("确认选择")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 920)
@Composable
private fun BookingEntryScreenPreview() {
    val previewRoom = ConferenceRoom(
        id = "126",
        name = "研讨室 B708",
        minCapacity = 6,
        maxCapacity = 12,
        location = "彭家坪校区 图书馆 7F",
        floor = "7F",
        inUse = false,
        status = "空闲"
    )

    MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BookingEntryScreen(
                room = previewRoom,
                onBack = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 760)
@Composable
private fun BookingCardPickerSheetContentPreview() {
    var selectedIds by remember {
        mutableStateOf(setOf("202300101", "202400101"))
    }
    val previewCards = listOf(
        BookingUseCard(
            studentId = "202300101",
            name = "张三",
            note = "主预约人",
            userUnit = "计算机学院",
            userType = "本科生",
            sort = 1,
            verificationStatus = "Verified",
            verificationMessage = "已验证"
        ),
        BookingUseCard(
            studentId = "202400101",
            name = "李四",
            note = "学弟",
            userUnit = "计算机学院",
            userType = "本科生",
            sort = 2,
            verificationStatus = "Verified",
            verificationMessage = "已验证"
        ),
        BookingUseCard(
            studentId = "202400201",
            name = "王五",
            note = "",
            userUnit = "经济管理学院",
            userType = "本科生",
            sort = 3,
            verificationStatus = "OfflineUnverified",
            verificationMessage = "未验证（离线添加）"
        )
    )

    MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            BookingCardPickerSheetContent(
                onOpenMyCards = {},
                availableCards = previewCards,
                selectedCardIds = selectedIds,
                lockedCardIds = setOf("202300101"),
                onToggleCard = { cardId -> selectedIds = selectedIds.toggle(cardId) },
                onDismiss = {},
                onConfirm = {}
            )
        }
    }
}

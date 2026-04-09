package com.trainingroom.book

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trainingroom.book.ui.theme.MyApplicationTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RoomDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val room = intent.getParcelableExtra<ConferenceRoom>(EXTRA_ROOM)
        val offlineNotice = intent.getStringExtra(EXTRA_OFFLINE_NOTICE)
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
                    RoomDetailScreen(
                        room = room,
                        offlineNotice = offlineNotice,
                        onBack = { finish() },
                        onReserve = {
                            startActivity(BookingEntryActivity.createIntent(this, room))
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROOM = "room"
        const val EXTRA_OFFLINE_NOTICE = "offlineNotice"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoomDetailScreen(
    room: ConferenceRoom,
    offlineNotice: String?,
    onBack: () -> Unit,
    onReserve: () -> Unit
) {
    var reservations by remember { mutableStateOf<List<ReservationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(room.id) {
        loading = true
        error = null
        val result = fetchReservations(room.id)
        reservations = result
        loading = false
        if (result.isEmpty()) error = "暂无预约记录"
    }

    RoomDetailContent(
        room = room,
        offlineNotice = offlineNotice,
        reservations = reservations,
        loading = loading,
        error = error,
        onBack = onBack,
        onReserve = onReserve
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoomDetailContent(
    room: ConferenceRoom,
    offlineNotice: String?,
    reservations: List<ReservationItem>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onReserve: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(room.name, fontSize = 22.sp)
                },
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
                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (offlineNotice != null) {
                        Text(
                            text = offlineNotice,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                    Text(room.name, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("房间ID: ${room.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("位置: ${room.location}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("容量: ${room.minCapacity} - ${room.maxCapacity} 人", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RoomStatusBadge(room = room)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("当前预约请求", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)

                    if (loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    } else if (error != null) {
                        Text(error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                    } else {
                        ReservationTable(reservations)
                        Spacer(modifier = Modifier.height(4.dp))
                        ReservationCalendar3Day(reservations)
                    }
                }
            }

            Button(
                onClick = onReserve,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("去预约")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun RoomDetailScreenPreview() {
    val previewRoom = ConferenceRoom(
        id = "105",
        name = "研讨室 A707",
        minCapacity = 6,
        maxCapacity = 12,
        location = "彭家坪校区 7 楼研讨 A 区",
        floor = "7F",
        inUse = false,
        status = "空闲"
    )
    val previewReservations = listOf(
        ReservationItem(
            id = "284941",
            title = "预约记录",
            startTime = "03/30/2026 08:00",
            endTime = "03/30/2026 10:00"
        ),
        ReservationItem(
            id = "284944",
            title = "预约记录",
            startTime = "03/31/2026 13:00",
            endTime = "03/31/2026 15:30"
        ),
        ReservationItem(
            id = "285064",
            title = "预约记录",
            startTime = "04/01/2026 18:30",
            endTime = "04/01/2026 21:00"
        )
    )

    MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            RoomDetailContent(
                room = previewRoom,
                offlineNotice = "预览数据：当前为本地示例，不代表实时预约状态",
                reservations = previewReservations,
                loading = false,
                error = null,
                onBack = {},
                onReserve = {}
            )
        }
    }
}

@Composable
fun ReservationTable(items: List<ReservationItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("预约ID", modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("标题", modifier = Modifier.weight(1.2f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("开始", modifier = Modifier.weight(1.2f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("结束", modifier = Modifier.weight(1.2f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        if (items.isEmpty()) {
            Text("暂无预约记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.id, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(item.title, modifier = Modifier.weight(1.2f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(item.startTime, modifier = Modifier.weight(1.2f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(item.endTime, modifier = Modifier.weight(1.2f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun ReservationCalendar3Day(items: List<ReservationItem>) {
    val today = LocalDate.now()
    val days = listOf(today, today.plusDays(1), today.plusDays(2))
    val perDay = days.map { day ->
        val events = items.mapNotNull { item ->
            val s = item.startDateTime()
            val e = item.endDateTime()
            if (s != null && e != null && s.toLocalDate() == day) {
                Triple(item, s, e)
            } else null
        }.sortedBy { it.second }
        day to events
    }

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        perDay.forEach { (day, events) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = day.toString(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (events.isEmpty()) {
                    Text("暂无预约", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    events.forEach { (item, start, end) ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(item.title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${start.format(timeFormatter)} - ${end.format(timeFormatter)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

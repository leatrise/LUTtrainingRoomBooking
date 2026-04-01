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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                RoomDetailScreen(room = room, offlineNotice = offlineNotice)
            }
        }
    }

    companion object {
        const val EXTRA_ROOM = "room"
        const val EXTRA_OFFLINE_NOTICE = "offlineNotice"
    }
}

@Composable
fun RoomDetailScreen(room: ConferenceRoom, offlineNotice: String?) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                Text(room.name, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("房间ID: ${room.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("位置: ${room.location}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("容量: ${room.minCapacity} - ${room.maxCapacity} 人", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("状态: ${room.status}", fontSize = 12.sp, color = if (room.inUse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前预约请求", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)

                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    Text(error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                } else {
                    ReservationTable(reservations)
                    Spacer(modifier = Modifier.height(8.dp))
                    ReservationCalendar3Day(reservations)
                }
            }
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

package com.trainingroom.book

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trainingroom.book.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class MyCardsActivity : ComponentActivity() {
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
                    MyCardsScreen(onBack = { finish() })
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, MyCardsActivity::class.java)
    }
}

private data class SavedCard(
    val studentId: String,
    val name: String,
    val note: String,
    val userUnit: String = "",
    val userType: String = "",
    val verificationStatus: CardVerificationStatus,
    val verificationMessage: String? = null
)

private val previewSavedCards = listOf(
    SavedCard(
        studentId = "202300101",
        name = "张三",
        note = "训练室常用卡",
        verificationStatus = CardVerificationStatus.Verified
    ),
    SavedCard(
        studentId = "202600401",
        name = "李四",
        note = "",
        verificationStatus = CardVerificationStatus.OfflineUnverified
    )
)

private enum class CardVerificationStatus {
    Verified,
    OfflineUnverified,
    Failed
}

private data class VerificationBadgeStyle(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color
)

private enum class CardLookupStatus {
    Success,
    NameMismatch,
    NotFound,
    LoginRequired,
    Error
}

private data class CardLookupResult(
    val status: CardLookupStatus,
    val name: String? = null,
    val userUnit: String? = null,
    val userType: String? = null,
    val message: String
)

private data class BulkVerifyResult(
    val cards: List<SavedCard>,
    val successCount: Int,
    val failedCount: Int,
    val nameMismatchCount: Int
)

private enum class NoticeTone {
    Info,
    Success,
    Warning
}

private const val CARD_PREFS_NAME = "saved_cards"
private const val KEY_CARDS_JSON = "cards_json"
private const val CARD_LOOKUP_URL = "https://weixinlib.lut.edu.cn/trainingroominfor/search"
private const val CARD_SOURCE_PAGE_URL = "https://weixinlib.lut.edu.cn/moretrainingroombesk"
private const val CARD_SOURCE_PAGE_ID = "126"
private const val CARD_ROOM_GROUP_ID = "14"
private const val CARD_LOOKUP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyCardsScreen(
    onBack: () -> Unit,
    initialCards: List<SavedCard>? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHint by rememberSaveable { mutableStateOf(true) }
    var savedCards by remember(initialCards) { mutableStateOf(initialCards ?: loadSavedCards(context)) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var editingOriginalStudentId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteCard by remember { mutableStateOf<SavedCard?>(null) }
    var studentId by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var isLookingUp by remember { mutableStateOf(false) }
    var lookupMessage by remember { mutableStateOf(defaultLookupMessage(context)) }
    var lookupTone by remember { mutableStateOf(defaultLookupTone(context)) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveTone by remember { mutableStateOf(NoticeTone.Success) }
    var autoFilledStudentId by remember { mutableStateOf<String?>(null) }
    var userUnit by remember { mutableStateOf("") }
    var userType by remember { mutableStateOf("") }
    var currentVerificationStatus by remember {
        mutableStateOf(defaultDraftVerificationStatus(context))
    }
    var studentIdHasFocus by remember { mutableStateOf(false) }
    var requestedLookupStudentId by remember { mutableStateOf<String?>(null) }
    var lookupRequestVersion by remember { mutableStateOf(0) }
    var isBulkVerifying by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun resetEditorState() {
        studentId = ""
        name = ""
        note = ""
        isLookingUp = false
        lookupMessage = defaultLookupMessage(context)
        lookupTone = defaultLookupTone(context)
        saveMessage = null
        saveTone = NoticeTone.Success
        autoFilledStudentId = null
        userUnit = ""
        userType = ""
        currentVerificationStatus = defaultDraftVerificationStatus(context)
        studentIdHasFocus = false
        requestedLookupStudentId = null
        lookupRequestVersion = 0
        editingOriginalStudentId = null
    }

    LaunchedEffect(studentId) {
        saveMessage = null
        val normalizedStudentId = studentId.trim()
        if (autoFilledStudentId != null && normalizedStudentId != autoFilledStudentId) {
            name = ""
            autoFilledStudentId = null
            userUnit = ""
            userType = ""
            currentVerificationStatus = defaultDraftVerificationStatus(context)
        }
    }

    LaunchedEffect(showAddSheet, lookupRequestVersion) {
        if (!showAddSheet || lookupRequestVersion == 0) {
            return@LaunchedEffect
        }
        val normalizedStudentId = requestedLookupStudentId.orEmpty()

        if (normalizedStudentId.isBlank()) {
            isLookingUp = false
            lookupMessage = defaultLookupMessage(context)
            lookupTone = defaultLookupTone(context)
            return@LaunchedEffect
        }

        if (!AuthSessionManager.isLoggedIn(context)) {
            isLookingUp = false
            lookupMessage = "当前未登录，无法验证真实性，但仍可手动填写姓名后保存。"
            lookupTone = NoticeTone.Warning
            currentVerificationStatus = CardVerificationStatus.OfflineUnverified
            return@LaunchedEffect
        }

        if (!isNetworkAvailable(context)) {
            isLookingUp = false
            lookupMessage = "当前无网络，无法在线验证，但仍可手动填写姓名后保存。"
            lookupTone = NoticeTone.Warning
            currentVerificationStatus = CardVerificationStatus.OfflineUnverified
            return@LaunchedEffect
        }

        delay(450)
        isLookingUp = true
        val lookupResult = lookupCardOwner(context, normalizedStudentId)
        isLookingUp = false
        when (lookupResult.status) {
            CardLookupStatus.Success -> {
                name = lookupResult.name.orEmpty()
                userUnit = lookupResult.userUnit.orEmpty()
                userType = lookupResult.userType.orEmpty()
                autoFilledStudentId = normalizedStudentId
                lookupMessage = lookupResult.message
                lookupTone = NoticeTone.Success
                currentVerificationStatus = CardVerificationStatus.Verified
            }
            CardLookupStatus.NameMismatch -> {
                userUnit = lookupResult.userUnit.orEmpty()
                userType = lookupResult.userType.orEmpty()
                lookupMessage = lookupResult.message
                lookupTone = NoticeTone.Warning
                currentVerificationStatus = CardVerificationStatus.Failed
            }
            CardLookupStatus.NotFound -> {
                lookupMessage = lookupResult.message
                lookupTone = NoticeTone.Warning
                currentVerificationStatus = CardVerificationStatus.Failed
            }
            CardLookupStatus.LoginRequired -> {
                lookupMessage = lookupResult.message
                lookupTone = NoticeTone.Warning
                currentVerificationStatus = CardVerificationStatus.OfflineUnverified
            }
            CardLookupStatus.Error -> {
                if (!isNetworkAvailable(context)) {
                    lookupMessage = "当前无网络，无法在线验证，但仍可手动填写姓名后保存。"
                    lookupTone = NoticeTone.Warning
                    currentVerificationStatus = CardVerificationStatus.OfflineUnverified
                } else {
                    lookupMessage = lookupResult.message
                    lookupTone = NoticeTone.Warning
                    currentVerificationStatus = CardVerificationStatus.Failed
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("我的卡片", fontSize = 22.sp) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showHint) {
                item {
                    CardsHintBar(onDismiss = { showHint = false })
                }
            }

            item {
                AddCardEntryButton(
                    onClick = {
                        resetEditorState()
                        showAddSheet = true
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已保存卡片",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                val hasUnverifiedCards = savedCards.any {
                                    it.verificationStatus == CardVerificationStatus.OfflineUnverified
                                }
                                if (!hasUnverifiedCards) {
                                    Toast.makeText(context, "当前无未验证卡片", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                if (!AuthSessionManager.isLoggedIn(context)) {
                                    Toast.makeText(context, "当前未登录，无法一键验证", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                if (!isNetworkAvailable(context)) {
                                    Toast.makeText(context, "当前无网络，无法一键验证", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                isBulkVerifying = true
                                val result = verifyUnverifiedCards(context, savedCards)
                                savedCards = result.cards
                                isBulkVerifying = false
                                val message = when {
                                    result.successCount == 0 && result.failedCount == 0 ->
                                        "请求异常，请确认登录正常或稍后重试"
                                    else -> buildList {
                                        if (result.successCount > 0) {
                                            add("${result.successCount} 个验证成功")
                                        }
                                        if (result.failedCount > 0) {
                                            add("${result.failedCount} 个验证失败")
                                        }
                                        if (result.nameMismatchCount > 0) {
                                            add("${result.nameMismatchCount} 个姓名不匹配")
                                        }
                                    }.joinToString("，")
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isBulkVerifying && savedCards.any {
                            it.verificationStatus == CardVerificationStatus.OfflineUnverified
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "验证未验证卡片",
                            tint = if (savedCards.any {
                                    it.verificationStatus == CardVerificationStatus.OfflineUnverified
                                } && !isBulkVerifying
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (savedCards.isEmpty()) {
                item {
                    EmptyCardsPlaceholder()
                }
            } else {
                items(savedCards, key = { it.studentId }) { card ->
                    SavedCardItem(
                        card = card,
                        onEdit = {
                            editingOriginalStudentId = card.studentId
                            studentId = card.studentId
                            name = card.name
                            note = card.note
                            userUnit = card.userUnit
                            userType = card.userType
                            saveMessage = null
                            saveTone = NoticeTone.Success
                            lookupMessage = "离开学号输入框后可重新验证姓名。"
                            lookupTone = NoticeTone.Info
                            autoFilledStudentId = card.studentId
                            currentVerificationStatus = card.verificationStatus
                            requestedLookupStudentId = null
                            lookupRequestVersion = 0
                            showAddSheet = true
                        },
                        onDelete = {
                            pendingDeleteCard = card
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAddSheet = false
                resetEditorState()
            },
            sheetState = addSheetState
        ) {
            CardEditorSheet(
                title = if (editingOriginalStudentId == null) "新增卡片" else "修改卡片",
                submitLabel = if (editingOriginalStudentId == null) "保存卡片" else "保存修改",
                studentId = studentId,
                name = name,
                note = note,
                notePlaceholder = buildString {
                    if (userUnit.isNotBlank()) append(userUnit)
                    if (userType.isNotBlank()) {
                        if (isNotEmpty()) append("，")
                        append(userType)
                    }
                },
                isNameLocked = currentVerificationStatus == CardVerificationStatus.Verified,
                isLookingUp = isLookingUp,
                lookupMessage = lookupMessage,
                lookupTone = lookupTone,
                saveMessage = saveMessage,
                saveTone = saveTone,
                onStudentIdChange = { studentId = it.filter(Char::isDigit) },
                onStudentIdFocusChanged = { isFocused ->
                    val wasFocused = studentIdHasFocus
                    studentIdHasFocus = isFocused
                    if (wasFocused && !isFocused) {
                        val normalizedStudentId = studentId.trim()
                        val isEditingVerifiedCard =
                            editingOriginalStudentId != null &&
                                currentVerificationStatus == CardVerificationStatus.Verified
                        if (isEditingVerifiedCard && normalizedStudentId == editingOriginalStudentId) {
                            lookupMessage = "学号未修改，保留已验证状态。"
                            lookupTone = NoticeTone.Success
                            requestedLookupStudentId = null
                        } else {
                            requestedLookupStudentId = normalizedStudentId
                            lookupRequestVersion += 1
                        }
                    }
                },
                onNameChange = {
                    name = it
                    autoFilledStudentId = null
                    userUnit = ""
                    userType = ""
                    currentVerificationStatus = defaultDraftVerificationStatus(context)
                },
                onNoteChange = { note = it },
                onSave = {
                    val normalizedStudentId = studentId.trim()
                    val normalizedName = name.trim()
                    val normalizedNote = note.trim()
                    val shouldPreserveVerifiedStatus =
                        editingOriginalStudentId != null &&
                            normalizedStudentId == editingOriginalStudentId &&
                            currentVerificationStatus == CardVerificationStatus.Verified
                    val effectiveVerificationStatus = when {
                        shouldPreserveVerifiedStatus -> CardVerificationStatus.Verified
                        !AuthSessionManager.isLoggedIn(context) -> CardVerificationStatus.OfflineUnverified
                        !isNetworkAvailable(context) -> CardVerificationStatus.OfflineUnverified
                        else -> currentVerificationStatus
                    }

                    when {
                        normalizedStudentId.isBlank() -> {
                            saveMessage = "请先填写学号。"
                            saveTone = NoticeTone.Warning
                        }
                        normalizedName.isBlank() -> {
                            saveMessage = "请先填写姓名。"
                            saveTone = NoticeTone.Warning
                        }
                        else -> {
                            savedCards = upsertSavedCard(
                                context = context,
                                originalStudentId = editingOriginalStudentId,
                                card = SavedCard(
                                    studentId = normalizedStudentId,
                                    name = normalizedName,
                                    note = normalizedNote,
                                    userUnit = userUnit,
                                    userType = userType,
                                    verificationStatus = effectiveVerificationStatus,
                                    verificationMessage = when (effectiveVerificationStatus) {
                                        CardVerificationStatus.Verified -> "已验证"
                                        CardVerificationStatus.OfflineUnverified -> null
                                        CardVerificationStatus.Failed -> lookupMessage
                                    }
                                )
                            )
                            saveMessage = if (editingOriginalStudentId == null) {
                                "卡片已保存，重复学号会自动覆盖。"
                            } else {
                                "卡片信息已更新。"
                            }
                            saveTone = NoticeTone.Success
                            showAddSheet = false
                            resetEditorState()
                        }
                    }
                }
            )
        }
    }

    pendingDeleteCard?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCard = null },
            title = { Text("删除卡片") },
            text = { Text("确认删除 ${card.name} 的卡片信息？") },
            confirmButton = {
                Button(
                    onClick = {
                        savedCards = deleteSavedCard(context, card.studentId)
                        pendingDeleteCard = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeleteCard = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CardsHintBar(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "这里可以添加与管理您常用的卡片信息",
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭提示",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun AddCardEntryButton(
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("新增卡片")
    }
}

@Composable
private fun CardEditorSheet(
    title: String,
    submitLabel: String,
    studentId: String,
    name: String,
    note: String,
    notePlaceholder: String,
    isNameLocked: Boolean,
    isLookingUp: Boolean,
    lookupMessage: String,
    lookupTone: NoticeTone,
    saveMessage: String?,
    saveTone: NoticeTone,
    onStudentIdChange: (String) -> Unit,
    onStudentIdFocusChanged: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "输入学号后会自动尝试查找姓名；如果未登录或未找到，也可以手动填写。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = studentId,
            onValueChange = onStudentIdChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onStudentIdFocusChanged(it.isFocused) },
            singleLine = true,
            label = { Text("*学号") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isNameLocked,
            singleLine = true,
            label = { Text("*姓名") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("备注") },
            placeholder = {
                if (notePlaceholder.isNotBlank()) {
                    Text(notePlaceholder)
                }
            },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Done
            )
        )

        StatusNotice(
            message = if (isLookingUp) "正在验证学号并查找姓名..." else lookupMessage,
            tone = if (isLookingUp) NoticeTone.Info else lookupTone
        )

        saveMessage?.let {
            StatusNotice(message = it, tone = saveTone)
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(submitLabel)
        }
    }
}

@Composable
private fun StatusNotice(message: String, tone: NoticeTone) {
    val backgroundColor = when (tone) {
        NoticeTone.Info -> MaterialTheme.colorScheme.surfaceContainerHigh
        NoticeTone.Success -> MaterialTheme.colorScheme.secondaryContainer
        NoticeTone.Warning -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (tone) {
        NoticeTone.Info -> MaterialTheme.colorScheme.onSurfaceVariant
        NoticeTone.Success -> MaterialTheme.colorScheme.onSecondaryContainer
        NoticeTone.Warning -> MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = message,
            color = contentColor,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun EmptyCardsPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Text(
            text = "暂时还没有保存的卡片，添加后会显示在这里。",
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SavedCardItem(
    card: SavedCard,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val title = card.note.ifBlank { card.name }
    val showNameSubtitle = card.note.isNotBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        VerificationBadge(
                            status = card.verificationStatus,
                            message = card.verificationMessage
                        )
                    }
                    if (showNameSubtitle) {
                        Text(
                            text = card.name,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = card.studentId,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "修改卡片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除卡片",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationBadge(status: CardVerificationStatus, message: String?) {
    val style = when (status) {
        CardVerificationStatus.Verified -> VerificationBadgeStyle(
            label = "已验证",
            icon = Icons.Filled.CheckCircle,
            containerColor = Color(0xFFE8F5E9),
            contentColor = Color(0xFF1B5E20)
        )
        CardVerificationStatus.OfflineUnverified -> VerificationBadgeStyle(
            label = "未验证",
            icon = Icons.Filled.CloudOff,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardVerificationStatus.Failed -> VerificationBadgeStyle(
            label = if (message == "姓名不匹配") "姓名不匹配" else "验证失败",
            icon = Icons.Filled.ErrorOutline,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Row(
        modifier = Modifier
            .background(style.containerColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = style.contentColor
        )
        Text(
            text = style.label,
            fontSize = 11.sp,
            color = style.contentColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CardField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label：",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun CardVerificationStatus.color() = when (this) {
    CardVerificationStatus.Verified -> MaterialTheme.colorScheme.primary
    CardVerificationStatus.OfflineUnverified -> MaterialTheme.colorScheme.onSurfaceVariant
    CardVerificationStatus.Failed -> MaterialTheme.colorScheme.error
}

private fun CardVerificationStatus.label() = when (this) {
    CardVerificationStatus.Verified -> "已验证"
    CardVerificationStatus.OfflineUnverified -> "未验证（离线添加）"
    CardVerificationStatus.Failed -> "验证失败"
}

private fun defaultLookupMessage(context: Context): String {
    return if (AuthSessionManager.isLoggedIn(context)) {
        "输入学号后会自动查找并填充姓名。"
    } else {
        "当前未登录，无法验证真实性，但仍可手动填写信息。"
    }
}

private fun defaultLookupTone(context: Context): NoticeTone {
    return if (AuthSessionManager.isLoggedIn(context)) NoticeTone.Info else NoticeTone.Warning
}

private fun defaultDraftVerificationStatus(context: Context): CardVerificationStatus {
    return CardVerificationStatus.OfflineUnverified
}

private fun loadSavedCards(context: Context): List<SavedCard> {
    val raw = context.getSharedPreferences(CARD_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CARDS_JSON, null)
        ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val studentId = obj.optString("studentId").trim()
                val name = obj.optString("name").trim()
                val note = obj.optString("note").trim()
                val userUnit = obj.optString("userUnit").trim()
                val userType = obj.optString("userType").trim()
                val verificationStatus = obj.optString("verificationStatus")
                    .toCardVerificationStatus()
                if (studentId.isBlank() || name.isBlank()) continue
                add(
                    SavedCard(
                        studentId = studentId,
                        name = name,
                        note = note,
                        userUnit = userUnit,
                        userType = userType,
                        verificationStatus = verificationStatus,
                        verificationMessage = obj.optString("verificationMessage").trim().ifBlank { null }
                    )
                )
            }
        }
    }.getOrElse { emptyList() }
}

private fun upsertSavedCard(
    context: Context,
    card: SavedCard,
    originalStudentId: String? = null
): List<SavedCard> {
    val updatedCards = buildList {
        add(card)
        loadSavedCards(context)
            .filterNot {
                it.studentId == card.studentId ||
                    (originalStudentId != null && it.studentId == originalStudentId)
            }
            .forEach { add(it) }
    }

    persistSavedCards(context, updatedCards)
    return updatedCards
}

private fun deleteSavedCard(context: Context, studentId: String): List<SavedCard> {
    val updatedCards = loadSavedCards(context).filterNot { it.studentId == studentId }
    persistSavedCards(context, updatedCards)
    return updatedCards
}

private suspend fun verifyUnverifiedCards(
    context: Context,
    cards: List<SavedCard>
): BulkVerifyResult {
    if (!AuthSessionManager.isLoggedIn(context) || !isNetworkAvailable(context)) {
        return BulkVerifyResult(cards = cards, successCount = 0, failedCount = 0, nameMismatchCount = 0)
    }

    var successCount = 0
    var failedCount = 0
    var nameMismatchCount = 0
    val updatedCards = buildList {
        for (card in cards) {
            if (card.verificationStatus != CardVerificationStatus.OfflineUnverified) {
                add(card)
                continue
            }

            val lookupResult = lookupCardOwner(context, studentId = card.studentId, expectedName = card.name)
            when (lookupResult.status) {
                CardLookupStatus.Success -> {
                    successCount += 1
                    add(
                        card.copy(
                            userUnit = lookupResult.userUnit.orEmpty().ifBlank { card.userUnit },
                            userType = lookupResult.userType.orEmpty().ifBlank { card.userType },
                            verificationStatus = CardVerificationStatus.Verified,
                            verificationMessage = "已验证"
                        )
                    )
                }
                CardLookupStatus.NameMismatch -> {
                    failedCount += 1
                    nameMismatchCount += 1
                    add(
                        card.copy(
                            userUnit = lookupResult.userUnit.orEmpty().ifBlank { card.userUnit },
                            userType = lookupResult.userType.orEmpty().ifBlank { card.userType },
                            verificationStatus = CardVerificationStatus.Failed,
                            verificationMessage = "姓名不匹配"
                        )
                    )
                }
                CardLookupStatus.LoginRequired -> {
                    add(
                        card.copy(
                            verificationStatus = CardVerificationStatus.OfflineUnverified,
                            verificationMessage = null
                        )
                    )
                }
                CardLookupStatus.NotFound,
                CardLookupStatus.Error -> {
                    failedCount += 1
                    add(
                        card.copy(
                            userUnit = lookupResult.userUnit.orEmpty().ifBlank { card.userUnit },
                            userType = lookupResult.userType.orEmpty().ifBlank { card.userType },
                            verificationStatus = CardVerificationStatus.Failed,
                            verificationMessage = lookupResult.message
                        )
                    )
                }
            }
        }
    }

    persistSavedCards(context, updatedCards)
    return BulkVerifyResult(
        cards = updatedCards,
        successCount = successCount,
        failedCount = failedCount,
        nameMismatchCount = nameMismatchCount
    )
}

private fun persistSavedCards(context: Context, cards: List<SavedCard>) {
    val array = JSONArray()
    cards.forEach { savedCard ->
        array.put(
            JSONObject().apply {
                put("studentId", savedCard.studentId)
                put("name", savedCard.name)
                put("note", savedCard.note)
                put("userUnit", savedCard.userUnit)
                put("userType", savedCard.userType)
                put("verificationStatus", savedCard.verificationStatus.name)
                put("verificationMessage", savedCard.verificationMessage)
            }
        )
    }
    context.getSharedPreferences(CARD_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CARDS_JSON, array.toString())
        .apply()
}

private fun String?.toCardVerificationStatus(): CardVerificationStatus {
    return when (this) {
        CardVerificationStatus.Verified.name -> CardVerificationStatus.Verified
        CardVerificationStatus.Failed.name -> CardVerificationStatus.Failed
        CardVerificationStatus.OfflineUnverified.name -> CardVerificationStatus.OfflineUnverified
        else -> CardVerificationStatus.OfflineUnverified
    }
}

private suspend fun lookupCardOwner(
    context: Context,
    studentId: String,
    expectedName: String? = null
): CardLookupResult {
    val firstAttempt = lookupCardOwnerOnce(context, studentId, expectedName)
    if (firstAttempt.status != CardLookupStatus.LoginRequired || !AuthSessionManager.isSsoLogin(context)) {
        return firstAttempt
    }

    val renewResult = SsoLoginService.trySilentRefresh(context)
    if (!renewResult.success) {
        return CardLookupResult(
            status = CardLookupStatus.LoginRequired,
            message = renewResult.message ?: "当前登录态已失效，无法验证真实性，可手动填写姓名后保存。"
        )
    }
    return lookupCardOwnerOnce(context, studentId, expectedName)
}

private suspend fun lookupCardOwnerOnce(
    context: Context,
    studentId: String,
    expectedName: String? = null
): CardLookupResult {
    return withContext(Dispatchers.IO) {
        AuthSessionManager.install(context)
        val rawCookieHeader = AuthSessionManager.weixinlibCookieHeader(context)
        if (!AuthSessionManager.isLoggedIn(context)) {
            return@withContext CardLookupResult(
                status = CardLookupStatus.LoginRequired,
                message = "当前未登录，无法验证真实性，但仍可手动填写信息。"
            )
        }

        runCatching {
            val pageConnection = (URL("$CARD_SOURCE_PAGE_URL?id=$CARD_SOURCE_PAGE_ID").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", CARD_LOOKUP_USER_AGENT)
                setRequestProperty("Referer", "https://weixinlib.lut.edu.cn/")
                rawCookieHeader?.let { setRequestProperty("Cookie", it) }
            }
            val pageBody = pageConnection.inputStream.bufferedReader().use { it.readText() }
            val pageFinalUrl = pageConnection.url.toString()
            if (isLoginLikeResponse(pageBody, pageFinalUrl)) {
                return@runCatching CardLookupResult(
                    status = CardLookupStatus.LoginRequired,
                    message = "当前未登录或登录已失效，无法验证真实性，可手动填写姓名后保存。"
                )
            }

            val postBody = buildString {
                append("searchkay=CardNo")
                append("&searchtype=1")
                append("&searchvalue=")
                append(URLEncoder.encode(studentId, "UTF-8"))
                append("&roomGroupid=$CARD_ROOM_GROUP_ID")
            }

            val connection = (URL(CARD_LOOKUP_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Origin", "https://weixinlib.lut.edu.cn")
                setRequestProperty("Referer", "$CARD_SOURCE_PAGE_URL?id=$CARD_SOURCE_PAGE_ID")
                setRequestProperty("User-Agent", CARD_LOOKUP_USER_AGENT)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                rawCookieHeader?.let { setRequestProperty("Cookie", it) }
            }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(postBody)
            }

            val responseText = (connection.inputStream ?: connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (isLoginLikeResponse(responseText, connection.url.toString())) {
                return@runCatching CardLookupResult(
                    status = CardLookupStatus.LoginRequired,
                    message = "当前未登录或登录已失效，无法验证真实性，可手动填写姓名后保存。"
                )
            }

            val array = JSONArray(responseText)
            if (array.length() == 0) {
                return@runCatching CardLookupResult(
                    status = CardLookupStatus.NotFound,
                    message = "未找到对应信息，请核对学号或手动填写姓名。"
                )
            }

            val firstItem = array.optJSONObject(0)
            val foundName = firstItem?.optString("username")?.trim().orEmpty()
            val foundUserUnit = firstItem?.optString("userunit")?.trim().orEmpty()
            val foundUserType = firstItem?.optString("usertype")?.trim().orEmpty()
            if (foundName.isBlank()) {
                return@runCatching CardLookupResult(
                    status = CardLookupStatus.NotFound,
                    message = "未找到对应信息，请核对学号或手动填写姓名。"
                )
            }

            val normalizedExpectedName = expectedName?.trim().orEmpty()
            if (normalizedExpectedName.isNotBlank() && foundName != normalizedExpectedName) {
                return@runCatching CardLookupResult(
                    status = CardLookupStatus.NameMismatch,
                    name = foundName,
                    userUnit = foundUserUnit,
                    userType = foundUserType,
                    message = "姓名不匹配"
                )
            }

            CardLookupResult(
                status = CardLookupStatus.Success,
                name = foundName,
                userUnit = foundUserUnit,
                userType = foundUserType,
                message = "已自动找到并填充姓名。"
            )
        }.getOrElse { error ->
            CardLookupResult(
                status = CardLookupStatus.Error,
                message = error.message ?: "查询失败，可手动填写姓名后保存。"
            )
        }
    }
}

private fun isLoginLikeResponse(body: String, finalUrl: String): Boolean {
    return "cas/login" in finalUrl ||
        "读者登录" in body ||
        "统一身份认证" in body ||
        "action=\"login\"" in body
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun MyCardsScreenPreview() {
    MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    CardsHintBar(onDismiss = {})
                }
                item {
                    AddCardEntryButton(onClick = {})
                }
                item {
                    Text(
                        text = "已保存卡片",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(previewSavedCards, key = { it.studentId }) { card ->
                    SavedCardItem(
                        card = card,
                        onEdit = {},
                        onDelete = {}
                    )
                }
            }
        }
    }
}

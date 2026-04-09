package com.trainingroom.book

import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trainingroom.book.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.install(this)
        enableEdgeToEdge()
        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_LIBRARY)
            .coerceIn(TAB_LIBRARY, TAB_TOKEN)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreen(
                        initialTab = initialTab,
                        onBack = { finish() },
                        onLoginSuccess = {
                            setResult(RESULT_OK)
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_INITIAL_TAB = "initialTab"
        const val TAB_LIBRARY = 0
        const val TAB_SSO = 1
        const val TAB_TOKEN = 2

        fun createIntent(context: Context, initialTab: Int = TAB_LIBRARY): Intent {
            return Intent(context, LoginActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, initialTab.coerceIn(TAB_LIBRARY, TAB_TOKEN))
            }
        }
    }
}

private data class LoginDockItem(
    val title: String,
    val icon: ImageVector
)

private val loginDockItems = listOf(
    LoginDockItem(title = "图书馆号", icon = Icons.Filled.Badge),
    LoginDockItem(title = "统一认证", icon = Icons.Filled.VerifiedUser),
    LoginDockItem(title = "Cookie", icon = Icons.Filled.VpnKey)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    initialTab: Int,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, loginDockItems.lastIndex)) }
    val title = when (selectedTab) {
        LoginActivity.TAB_LIBRARY -> "图书馆号登录"
        LoginActivity.TAB_SSO -> "学校统一身份认证"
        else -> "Cookie 登录"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            LoginTopDockBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            when (selectedTab) {
                LoginActivity.TAB_LIBRARY -> LibraryLoginPanel()
                LoginActivity.TAB_SSO -> SsoLoginPanel(onLoginSuccess = onLoginSuccess)
                else -> CookieLoginPanel(onLoginSuccess = onLoginSuccess)
            }
        }
    }
}

@Composable
private fun LoginTopDockBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            loginDockItems.forEachIndexed { index, item ->
                val selected = selectedTab == index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = item.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SsoLoginPanel(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LoginPanelCard(
        title = "学校统一身份认证平台",
        description = "按 HAR 里的 CAS-PaaS -> lib.lut -> weixinlib 链路发起真实登录请求，界面只保留账号密码输入，成功后会把会话 Cookie 保存在应用内。"
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("统一认证账号") },
            placeholder = { Text("输入学号/工号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            )
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("统一认证密码") },
            placeholder = { Text("输入登录密码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    resultMessage = null
                    scope.launch {
                        isSubmitting = true
                        val result = SsoLoginService.login(
                            context = context,
                            username = username.trim(),
                            password = password,
                            loginUrl = SsoLoginService.DEFAULT_SSO_LOGIN_URL
                        )
                        isSubmitting = false
                        resultMessage = result.message
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        if (result.success) {
                            onLoginSuccess()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                if (isSubmitting) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("统一认证登录")
                }
            }
            OutlinedButton(
                onClick = { showPassword = !showPassword },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (showPassword) "隐藏密码" else "显示密码")
            }
        }

        resultMessage?.let { message ->
            Text(
                text = message,
                fontSize = 12.sp,
                color = if ("成功" in message) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun LibraryLoginPanel() {
    var libraryNo by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LoginPanelCard(
        title = "图书馆号登录",
        description = "适合直接对接图书馆账号密码认证。后续可以在这里补验证码、找回密码和登录异常提示。"
    ) {
        OutlinedTextField(
            value = libraryNo,
            onValueChange = { libraryNo = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("图书馆号") },
            placeholder = { Text("输入图书馆号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            )
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            placeholder = { Text("输入登录密码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {},
                modifier = Modifier.weight(1f)
            ) {
                Text("图书馆号登录")
            }
            OutlinedButton(
                onClick = { showPassword = !showPassword },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (showPassword) "隐藏密码" else "显示密码")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieLoginPanel(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val scope = rememberCoroutineScope()
    var cookieHeader by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LoginPanelCard(
        title = "Cookie 登录",
        description = "仅针对 weixinlib.lut.edu.cn 站内页面。把 F12 Network 里对应请求的 Cookie 请求头整段粘贴进来，应用会直接导入 Cookie 并访问 usercenter 校验用户名。"
    ) {
        OutlinedTextField(
            value = cookieHeader,
            onValueChange = { cookieHeader = it },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            label = { Text("Cookie 请求头") },
            placeholder = { Text("例如：0eJsq4Z7wHaVO=...; JSESSIONID=...") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    resultMessage = null
                    scope.launch {
                        isSubmitting = true
                        val importedCount = AuthSessionManager.importWeixinlibCookies(
                            context = context,
                            rawCookieHeader = cookieHeader
                        )
                        if (importedCount <= 0) {
                            isSubmitting = false
                            resultMessage = "未解析到有效 Cookie"
                            return@launch
                        }

                        val result = validateWeixinlibCookieLogin(cookieHeader)
                        isSubmitting = false
                        if (result.profile != null) {
                            AuthSessionManager.markLoggedIn(
                                context = context,
                                userCenterUrl = "https://weixinlib.lut.edu.cn/usercenter",
                                loginSource = AuthSessionManager.LOGIN_SOURCE_COOKIE
                            )
                            resultMessage = "Cookie 登录成功，当前用户：${result.profile.username}"
                            Toast.makeText(context, resultMessage, Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        } else {
                            resultMessage = result.message ?: "Cookie 校验失败，请检查内容是否有效"
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                if (isSubmitting) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("使用 Cookie 登录")
                }
            }
            OutlinedButton(
                onClick = {
                    cookieHeader = ""
                    val clipboardText = clipboardManager?.primaryClip
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                    if (clipboardText.isNotEmpty()) {
                        cookieHeader = clipboardText
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("清空并粘贴")
            }
        }

        resultMessage?.let { message ->
            Text(
                text = message,
                fontSize = 12.sp,
                color = if ("成功" in message) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun LoginPanelCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun LoginScreenPreview() {
    MyApplicationTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            LoginScreen(
                initialTab = LoginActivity.TAB_LIBRARY,
                onBack = {},
                onLoginSuccess = {}
            )
        }
    }
}

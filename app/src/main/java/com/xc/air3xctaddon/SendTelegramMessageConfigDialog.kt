package com.xc.air3xctaddon.ui

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.xc.air3xctaddon.AppDatabase
import com.xc.air3xctaddon.BuildConfig
import com.xc.air3xctaddon.R
import com.xc.air3xctaddon.SettingsRepository
import com.xc.air3xctaddon.Task
import com.xc.air3xctaddon.TelegramBotHelper
import com.xc.air3xctaddon.TelegramBotInfo
import com.xc.air3xctaddon.TelegramChat
import com.xc.air3xctaddon.ui.components.DropdownMenuSpinner
import com.xc.air3xctaddon.ui.components.SpinnerItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SendTelegramMessageConfigDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var telegramChatId by remember { mutableStateOf("") }
    var telegramChatName by remember { mutableStateOf("") }
    var isLoadingChats by remember { mutableStateOf(true) }
    var isCheckingBot by remember { mutableStateOf(false) }
    var isSendingStart by remember { mutableStateOf(false) }
    var chatError by remember { mutableStateOf<String?>(null) }
    var chats by remember { mutableStateOf<List<TelegramChat>>(emptyList()) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var hasNetworkPermission by remember { mutableStateOf(false) }
    var botInfo by remember { mutableStateOf<TelegramBotInfo?>(null) }
    var selectedChat by remember { mutableStateOf<TelegramChat?>(null) }
    var showChatTypeDialog by remember { mutableStateOf(false) }
    var showGroupSetupDialog by remember { mutableStateOf(false) }
    var showIndividualSetupDialog by remember { mutableStateOf(false) }
    var isAddingNewChat by remember { mutableStateOf(false) }
    var showMessageDialog by remember { mutableStateOf(false) }
    var selectedMessageTitle by remember { mutableStateOf("") }
    var selectedMessageContent by remember { mutableStateOf("") }
    var showNoInternetDialog by remember { mutableStateOf(false) }

    val otherOptionText = stringResource(R.string.other_option)
    val selectChatOptionText = stringResource(R.string.select_chat_option)

    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val settingsRepository = remember { SettingsRepository(context) }
    val telegramBotHelper = remember {
        TelegramBotHelper(
            context,
            BuildConfig.TELEGRAM_BOT_TOKEN,
            fusedLocationClient,
            settingsRepository
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val taskDao = AppDatabase.getDatabase(context).taskDao()

    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetwork != null
    }

    fun checkBotInSelectedChat() {
        selectedChat?.let { chat ->
            isCheckingBot = true
            telegramBotHelper.checkBotAccess(
                chatId = chat.chatId,
                isGroup = chat.isGroup,
                onResult = { isMember, isActive ->
                    isCheckingBot = false
                    selectedChat = chat.copy(isBotMember = isMember, isBotActive = isActive)
                    chats = chats.map {
                        if (it.chatId == chat.chatId) it.copy(isBotMember = isMember, isBotActive = isActive) else it
                    }
                    Log.d("SendTelegramMessageConfigDialog", "Checked chat ${chat.title}: isMember=$isMember, isActive=$isActive")
                    if (!isMember) {
                        if (chat.isGroup) {
                            showGroupSetupDialog = true
                        } else {
                            chatError = context.getString(R.string.bot_no_access_private_chat)
                            showIndividualSetupDialog = true
                        }
                    }
                },
                onError = { error ->
                    isCheckingBot = false
                    chatError = context.getString(R.string.failed_to_check_bot_status, error)
                    selectedChat = chat.copy(isBotMember = false, isBotActive = false)
                    Log.e("SendTelegramMessageConfigDialog", "Error checking bot in chat ${chat.title}: $error")
                }
            )
        }
    }

    suspend fun fetchChats(retryCount: Int = 0, maxRetries: Int = 3) {
        Log.d("SendTelegramMessageConfigDialog", "fetchChats called: retry=$retryCount, maxRetries=$maxRetries")
        if (retryCount > maxRetries) {
            isLoadingChats = false
            chatError = context.getString(R.string.failed_to_fetch_groups_retries, maxRetries)
            Log.e("SendTelegramMessageConfigDialog", "Max retries reached")
            return
        }
        isLoadingChats = true
        chatError = null
        telegramBotHelper.fetchRecentChats(
            onResult = { fetchedChats ->
                Log.d("SendTelegramMessageConfigDialog", "Fetched chats: ${fetchedChats.map { it.title }}")
                chats = fetchedChats
                settingsRepository.saveChats(fetchedChats)
                isLoadingChats = false
                if (fetchedChats.isEmpty() || (telegramChatId.isNotEmpty() && chats.none { it.chatId == telegramChatId })) {
                    telegramChatId = ""
                    telegramChatName = ""
                    selectedChat = null
                }
                if (isAddingNewChat && fetchedChats.isNotEmpty()) {
                    val targetChat = fetchedChats.maxByOrNull { it.chatId.toLongOrNull() ?: Long.MAX_VALUE }
                    targetChat?.let { chat ->
                        telegramChatId = chat.chatId
                        telegramChatName = chat.title
                        selectedChat = chat
                        isAddingNewChat = false
                        Log.d("SendTelegramMessageConfigDialog", "Selected new chat: ${chat.title}")
                        coroutineScope.launch {
                            checkBotInSelectedChat()
                        }
                    }
                }
            },
            onError = { error ->
                Log.w("SendTelegramMessageConfigDialog", "Error (retry $retryCount/$maxRetries): $error")
                if (retryCount < maxRetries) {
                    coroutineScope.launch {
                        delay(1000L * (retryCount + 1))
                        fetchChats(retryCount + 1, maxRetries)
                    }
                } else {
                    isLoadingChats = false
                    chatError = context.getString(R.string.failed_to_fetch_groups_error, error)
                }
            }
        )
    }

    fun sendStartCommand() {
        selectedChat?.let { chat ->
            isSendingStart = true
            telegramBotHelper.sendStartCommand(
                chatId = chat.chatId,
                onResult = {
                    isSendingStart = false
                    selectedChat = chat.copy(isBotActive = true)
                    chats = chats.map {
                        if (it.chatId == chat.chatId) it.copy(isBotActive = true) else it
                    }
                    settingsRepository.saveChats(chats)
                    Log.d("SendTelegramMessageConfigDialog", "Bot activated in chat ${chat.title}")
                },
                onError = { error ->
                    isSendingStart = false
                    chatError = context.getString(R.string.failed_to_activate_bot, error)
                    Log.e("SendTelegramMessageConfigDialog", "Error activating bot in ${chat.title}: $error")
                }
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasNetworkPermission = permissions[android.Manifest.permission.ACCESS_NETWORK_STATE] == true
        if (!hasLocationPermission) {
            chatError = context.getString(R.string.location_permission_denied)
        }
        if (!hasNetworkPermission) {
            Log.w("SendTelegramMessageConfigDialog", context.getString(R.string.log_network_permission_denied))
        }
        if (hasLocationPermission) {
            coroutineScope.launch {
                Log.d("SendTelegramMessageConfigDialog", context.getString(R.string.log_permission_granted))
                fetchChats()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isAddingNewChat) {
                coroutineScope.launch {
                    Log.d("SendTelegramMessageConfigDialog", "App resumed, fetching chats")
                    delay(5000L)
                    fetchChats()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!isOnline()) {
            showNoInternetDialog = true
        } else {
            telegramBotHelper.getBotInfo(
                onResult = { info -> botInfo = info },
                onError = { error ->
                    Log.e("SendTelegramMessageConfigDialog", "Failed to get bot info: $error")
                    chatError = context.getString(R.string.failed_to_get_bot_info, error)
                    isLoadingChats = false
                }
            )
            permissionLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_NETWORK_STATE
            ))
        }
    }

    LaunchedEffect(selectedChat?.chatId) {
        selectedChat?.let { chat ->
            if (chat.chatId.isNotEmpty()) {
                checkBotInSelectedChat()
            }
        }
    }

    if (showNoInternetDialog) {
        AlertDialog(
            onDismissRequest = { showNoInternetDialog = false; onDismiss() },
            title = { Text(stringResource(R.string.no_internet_title)) },
            text = { Text(stringResource(R.string.no_internet_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showNoInternetDialog = false
                        if (isOnline()) {
                            coroutineScope.launch {
                                telegramBotHelper.getBotInfo(
                                    onResult = { info -> botInfo = info },
                                    onError = { error ->
                                        Log.e("SendTelegramMessageConfigDialog", "Failed to get bot info: $error")
                                        chatError = context.getString(R.string.failed_to_get_bot_info, error)
                                        isLoadingChats = false
                                    }
                                )
                                permissionLauncher.launch(arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_NETWORK_STATE
                                ))
                            }
                        } else {
                            showNoInternetDialog = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoInternetDialog = false; onDismiss() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 280.dp)
                    .padding(8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colors.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.configure_telegram_message),
                            style = MaterialTheme.typography.h6
                        )
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    Log.d("SendTelegramMessageConfigDialog", context.getString(R.string.log_manual_refresh))
                                    fetchChats()
                                }
                            },
                            enabled = true
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                tint = MaterialTheme.colors.primary
                            )
                        }
                    }

                    when {
                        isLoadingChats -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.searching_for_available_chats))
                            }
                        }
                        chatError != null -> {
                            Card(
                                backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = chatError ?: stringResource(R.string.error_loading_chats),
                                        color = MaterialTheme.colors.error
                                    )
                                    Button(
                                        onClick = {
                                            chatError = null
                                            coroutineScope.launch {
                                                Log.d("SendTelegramMessageConfigDialog", context.getString(R.string.log_retry_fetch))
                                                fetchChats()
                                            }
                                        },
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                        }
                        chats.isEmpty() -> {
                            Card(
                                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.no_chats_found_with_the_bot),
                                        style = MaterialTheme.typography.subtitle1
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.to_get_started),
                                        style = MaterialTheme.typography.body2
                                    )
                                    Text(stringResource(R.string.create_or_choose_a_telegram_chat), style = MaterialTheme.typography.body2)
                                    Text(stringResource(R.string.add_the_bot_to_that_chat), style = MaterialTheme.typography.body2)
                                    Text(stringResource(R.string.send_start_in_the_chat), style = MaterialTheme.typography.body2)
                                    Text(stringResource(R.string.come_back_here_and_refresh), style = MaterialTheme.typography.body2)
                                    Row(
                                        modifier = Modifier.padding(top = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showChatTypeDialog = true },
                                            enabled = botInfo != null
                                        ) {
                                            Text(stringResource(R.string.add_bot_to_chat))
                                        }
                                        Button(
                                            onClick = {
                                                botInfo?.let { info ->
                                                    telegramBotHelper.openTelegramToAddBot(context, info.username)
                                                }
                                            },
                                            enabled = botInfo != null
                                        ) {
                                            Text(stringResource(R.string.open_bot_in_telegram))
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    Log.d("SendTelegramMessageConfigDialog", context.getString(R.string.log_empty_chats_refresh))
                                                    fetchChats()
                                                }
                                            }
                                        ) {
                                            Text(stringResource(R.string.refresh))
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Text(stringResource(R.string.select_chat_message_prompt))
                            DropdownMenuSpinner(
                                context = context,
                                items = chats.map { SpinnerItem.Item((if (it.isGroup) "Group: " else "User: ") + it.title) } + SpinnerItem.Item(otherOptionText),
                                selectedItem = if (telegramChatName.isEmpty() || chats.none { it.title == telegramChatName }) selectChatOptionText else (if (selectedChat?.isGroup == true) "Group: " else "User: ") + telegramChatName,
                                onItemSelected = { selectedItem ->
                                    if (selectedItem == otherOptionText) {
                                        telegramChatName = ""
                                        telegramChatId = ""
                                        selectedChat = null
                                        showChatTypeDialog = true
                                        isAddingNewChat = true
                                        Log.d("SendTelegramMessageConfigDialog", context.getString(R.string.log_selected_other))
                                    } else {
                                        val title = selectedItem.removePrefix("Group: ").removePrefix("User: ")
                                        chats.find { it.title == title }?.let { chat ->
                                            telegramChatId = chat.chatId
                                            telegramChatName = chat.title
                                            selectedChat = chat
                                            checkBotInSelectedChat()
                                            isAddingNewChat = false
                                            Log.d("SendTelegramMessageConfigDialog", context.getString(R.string.log_selected_chat, chat.title))
                                        }
                                    }
                                },
                                label = stringResource(R.string.telegram_chat_label),
                                modifier = Modifier.fillMaxWidth()
                            )
                            selectedChat?.let { chat ->
                                when {
                                    isCheckingBot -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                            Text(stringResource(R.string.checking_bot_status))
                                        }
                                    }
                                    !chat.isBotMember -> {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = stringResource(R.string.bot_setup_required),
                                                    style = MaterialTheme.typography.subtitle1,
                                                    color = MaterialTheme.colors.error
                                                )
                                                Text(
                                                    if (chat.isGroup)
                                                        stringResource(R.string.the_bot_needs_to_be_added_to_group)
                                                    else
                                                        stringResource(R.string.bot_no_access_to_individual_chat)
                                                )
                                                Row(
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            if (chat.isGroup) {
                                                                showGroupSetupDialog = true
                                                            } else {
                                                                showIndividualSetupDialog = true
                                                            }
                                                        }
                                                    ) {
                                                        Text(stringResource(R.string.add_bot_to_chat))
                                                    }
                                                    OutlinedButton(
                                                        onClick = { checkBotInSelectedChat() }
                                                    ) {
                                                        Text(stringResource(R.string.refresh_status))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    chat.isBotMember && !chat.isBotActive -> {
                                        Card(
                                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = stringResource(R.string.activate_bot),
                                                    style = MaterialTheme.typography.subtitle1,
                                                    color = MaterialTheme.colors.primary
                                                )
                                                Text(stringResource(R.string.the_bot_is_in_the_chat_but_needs_to_be_activated))
                                                if (isSendingStart) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.padding(top = 8.dp)
                                                    ) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                                        Text(stringResource(R.string.activating_bot))
                                                    }
                                                } else {
                                                    Row(
                                                        modifier = Modifier.padding(top = 8.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { sendStartCommand() }
                                                        ) {
                                                            Text(stringResource(R.string.activate_bot))
                                                        }
                                                        OutlinedButton(
                                                            onClick = { checkBotInSelectedChat() }
                                                        ) {
                                                            Text(stringResource(R.string.refresh_status))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    chat.isBotMember && chat.isBotActive -> {
                                        Card(
                                            backgroundColor = Color.Green.copy(alpha = 0.1f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.ready_to_send_updates),
                                                    tint = Color.Green,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.ready_to_send_updates),
                                                    color = Color.Green,
                                                    style = MaterialTheme.typography.body2,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Card(
                                            backgroundColor = MaterialTheme.colors.surface,
                                            elevation = 2.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = stringResource(R.string.selected_chat),
                                                    style = MaterialTheme.typography.caption,
                                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = chat.title,
                                                        style = MaterialTheme.typography.body1,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    TextButton(
                                                        onClick = {
                                                            telegramChatName = ""
                                                            telegramChatId = ""
                                                            selectedChat = null
                                                        }
                                                    ) {
                                                        Text(stringResource(R.string.change))
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { showMessageDialog = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = chat.isBotMember && chat.isBotActive
                                        ) {
                                            Text(stringResource(R.string.add_message_to_send))
                                        }
                                        if (selectedMessageTitle.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Card(
                                                backgroundColor = MaterialTheme.colors.surface,
                                                elevation = 2.dp,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        text = stringResource(R.string.selected_message),
                                                        style = MaterialTheme.typography.caption,
                                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = selectedMessageTitle,
                                                        style = MaterialTheme.typography.body1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showChatTypeDialog) {
                        AlertDialog(
                            onDismissRequest = { showChatTypeDialog = false },
                            title = { Text(stringResource(R.string.select_chat_type_title)) },
                            text = {
                                Column {
                                    Text(stringResource(R.string.select_chat_type_prompt))
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            },
                            buttons = {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            showChatTypeDialog = false
                                            showGroupSetupDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.select_telegram_group))
                                    }
                                    Button(
                                        onClick = {
                                            showChatTypeDialog = false
                                            showIndividualSetupDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.select_individual_chat))
                                    }
                                    TextButton(
                                        onClick = { showChatTypeDialog = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            }
                        )
                    }

                    if (showGroupSetupDialog) {
                        AlertDialog(
                            onDismissRequest = { showGroupSetupDialog = false },
                            title = { Text(stringResource(R.string.add_bot_to_group_title)) },
                            text = {
                                Column {
                                    Text(stringResource(R.string.add_bot_to_group_prompt))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(stringResource(R.string.open_telegram_group_prompt))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(stringResource(R.string.return_and_refresh_instructions), style = MaterialTheme.typography.caption)
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        isAddingNewChat = true
                                        botInfo?.let { info ->
                                            telegramBotHelper.openTelegramToAddBot(context, info.username)
                                        }
                                        showGroupSetupDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.open_telegram_button))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showGroupSetupDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    if (showIndividualSetupDialog) {
                        AlertDialog(
                            onDismissRequest = { showIndividualSetupDialog = false },
                            title = { Text(stringResource(R.string.add_bot_to_individual_title)) },
                            text = {
                                Column {
                                    Text(stringResource(R.string.add_bot_to_individual_instructions))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("1. ${stringResource(R.string.send_bot_link_instruction)}")
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("2. ${stringResource(R.string.ask_contact_start_instruction)}")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(stringResource(R.string.return_and_refresh_instructions_individual), style = MaterialTheme.typography.caption)
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        isAddingNewChat = true
                                        botInfo?.let { info ->
                                            telegramBotHelper.shareBotLink(context, info.username)
                                        }
                                        showIndividualSetupDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.confirm))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showIndividualSetupDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    if (showMessageDialog) {
                        AddMessageDialog(
                            onMessageSelected = { title, content ->
                                selectedMessageTitle = title
                                selectedMessageContent = content
                                showMessageDialog = false
                                Log.d("SendTelegramMessageConfigDialog", "Selected message: $title")
                            },
                            onDismiss = { showMessageDialog = false }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                selectedChat?.let { chat ->
                                    if (chat.isBotMember && chat.isBotActive && selectedMessageContent.isNotEmpty()) {
                                        coroutineScope.launch {
                                            val taskData = "${chat.chatId}|${selectedMessageContent}"
                                            val task = Task(
                                                taskType = "SendTelegramMessage",
                                                taskData = taskData,
                                                taskName = "${selectedMessageTitle.take(20)} - ${chat.title}",
                                                launchInBackground = false
                                            )
                                            taskDao.insert(task)
                                            Log.d("SendTelegramMessageConfigDialog", "Saved task: type=${task.taskType}, taskData=$taskData, name=${task.taskName}")
                                            onConfirm()
                                        }
                                    }
                                }
                            },
                            enabled = selectedChat?.isBotMember == true && selectedChat?.isBotActive == true && selectedMessageContent.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }
    }
}
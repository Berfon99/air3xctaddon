package com.xc.air3xctaddon

import android.content.Context
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
import com.xc.air3xctaddon.ui.components.DropdownMenuSpinner
import com.xc.air3xctaddon.ui.components.SpinnerItem
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Composable
fun SendTelegramPositionConfigDialog(
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
    var showGroupSetupDialog by remember { mutableStateOf(false) }
    var showSelectGroupDialog by remember { mutableStateOf(false) }
    var isAddingNewChat by remember { mutableStateOf(false) }
    var showNoInternetDialog by remember { mutableStateOf(false) }
    var pendingGroupChat by remember { mutableStateOf<TelegramChat?>(null) }
    var isSelectingExistingGroup by remember { mutableStateOf(false) }
    val otherOptionText = stringResource(R.string.other_option)
    val selectChatOptionText = stringResource(R.string.select_chat_option)

    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    LaunchedEffect(Unit) { SettingsRepository.initialize(context) }
    val telegramBotHelper = remember {
        TelegramBotHelper(context, BuildConfig.TELEGRAM_BOT_TOKEN, fusedLocationClient)
    }
    val telegramChatManager = remember { TelegramChatManager(context, telegramBotHelper) }
    val coroutineScope = rememberCoroutineScope()
    val taskDao = AppDatabase.getDatabase(context).taskDao()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasNetworkPermission = permissions[android.Manifest.permission.ACCESS_NETWORK_STATE] == true
        if (!hasLocationPermission) {
            chatError = context.getString(R.string.location_permission_denied)
            Log.e("SendTelegramPositionConfigDialog", context.getString(R.string.location_permission_denied))
        }
        if (!hasNetworkPermission) {
            Log.w("SendTelegramPositionConfigDialog", context.getString(R.string.log_network_permission_denied))
        }
        if (hasLocationPermission) {
            coroutineScope.launch {
                telegramChatManager.fetchChats(
                    onResult = { fetchedChats ->
                        chats = fetchedChats
                        SettingsRepository.saveChats(fetchedChats)
                        isLoadingChats = false
                        telegramChatManager.handleChatSelection(
                            isAddingNewChat || isSelectingExistingGroup,
                            pendingGroupChat,
                            fetchedChats,
                            telegramChatId,
                            { chat ->
                                telegramChatId = chat.chatId
                                telegramChatName = chat.title
                                selectedChat = chat
                                pendingGroupChat = null
                                isAddingNewChat = false
                                isSelectingExistingGroup = false
                                coroutineScope.launch { telegramChatManager.checkBotInSelectedChat(chat) }
                            },
                            { telegramChatId = ""; telegramChatName = ""; selectedChat = null }
                        )
                    },
                    onError = { error ->
                        isLoadingChats = false
                        chatError = error
                    }
                )
            }
        }
    }

    fun initBotAndFetchChats() {
        coroutineScope.launch {
            if (!isOnline(context)) {
                showNoInternetDialog = true
                return@launch
            }
            telegramBotHelper.getBotInfo(
                onResult = { info ->
                    botInfo = info
                    permissionLauncher.launch(arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_NETWORK_STATE
                    ))
                },
                onError = { error ->
                    chatError = context.getString(R.string.failed_to_get_bot_info, error)
                    isLoadingChats = false
                }
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && (isAddingNewChat || isSelectingExistingGroup)) {
                coroutineScope.launch {
                    telegramChatManager.fetchChats(
                        onResult = { fetchedChats ->
                            chats = fetchedChats
                            SettingsRepository.saveChats(fetchedChats)
                            isLoadingChats = false
                            isAddingNewChat = false
                            isSelectingExistingGroup = false
                            pendingGroupChat = null
                            if (fetchedChats.any { it.isGroup && it.isBotMember && it.isBotActive }) {
                                showSelectGroupDialog = true
                            } else {
                                chatError = "No groups with the bot active are available."
                            }
                        },
                        onError = { error ->
                            isLoadingChats = false
                            chatError = error
                        }
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        SettingsRepository.initialize(context)
        SettingsRepository.clearCachedChats()
        initBotAndFetchChats()
    }

    LaunchedEffect(selectedChat?.chatId) {
        selectedChat?.let { chat ->
            if (chat.chatId.isNotEmpty()) {
                telegramChatManager.checkBotInSelectedChat(
                    chat,
                    onResult = { isMember, isActive, isUserMember ->
                        isCheckingBot = false
                        selectedChat = chat.copy(isBotMember = isMember, isBotActive = isActive, isUserMember = isUserMember)
                        chats = chats.map {
                            if (it.chatId == chat.chatId) it.copy(isBotMember = isMember, isBotActive = isActive, isUserMember = isUserMember) else it
                        }
                    },
                    onError = { error ->
                        isCheckingBot = false
                        chatError = error
                        selectedChat = chat.copy(isBotMember = false, isBotActive = false, isUserMember = false)
                    }
                )
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp).padding(8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.configure_telegram_position),
                        style = MaterialTheme.typography.h6
                    )
                    IconButton(
                        onClick = { coroutineScope.launch { initBotAndFetchChats() } },
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
                                        coroutineScope.launch { initBotAndFetchChats() }
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
                                Text(
                                    text = stringResource(R.string.check_bot_privacy_settings),
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.error
                                )
                                Row(
                                    modifier = Modifier.padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showGroupSetupDialog = true },
                                        enabled = botInfo != null
                                    ) {
                                        Text(stringResource(R.string.add_bot_to_group))
                                    }
                                    Button(
                                        onClick = {
                                            botInfo?.let { info ->
                                                telegramBotHelper.openTelegramToAddBot(context, info.username)
                                                isAddingNewChat = true
                                            } ?: run { chatError = context.getString(R.string.bot_info_unavailable) }
                                        },
                                        enabled = true
                                    ) {
                                        Text(stringResource(R.string.open_bot_in_telegram))
                                    }
                                    OutlinedButton(
                                        onClick = { coroutineScope.launch { initBotAndFetchChats() } }
                                    ) {
                                        Text(stringResource(R.string.refresh))
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (telegramChatName.isEmpty() && !isLoadingChats && chatError == null) {
                            Card(
                                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.select_chat_hint),
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.primary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        Text(stringResource(R.string.select_the_chat_where_you_want_to_send_position_updates))
                        DropdownMenuSpinner(
                            context = context,
                            items = chats
                                .filter { it.isGroup && it.isBotMember && it.isBotActive && it.isUserMember }
                                .map { SpinnerItem.Item("Group: " + it.title) } + SpinnerItem.Item(otherOptionText),
                            selectedItem = if (telegramChatName.isEmpty() || chats.none { it.title == telegramChatName }) selectChatOptionText else "Group: " + telegramChatName,
                            onItemSelected = { selectedItem ->
                                if (selectedItem == otherOptionText) {
                                    telegramChatName = ""; telegramChatId = ""; selectedChat = null; pendingGroupChat = null
                                    showGroupSetupDialog = true; isAddingNewChat = false; isSelectingExistingGroup = false
                                } else {
                                    val title = selectedItem.removePrefix("Group: ")
                                    chats.find { it.title == title }?.let { chat ->
                                        telegramChatId = chat.chatId
                                        telegramChatName = chat.title
                                        selectedChat = chat
                                        pendingGroupChat = null
                                        isAddingNewChat = false
                                        isSelectingExistingGroup = false
                                        coroutineScope.launch {
                                            telegramChatManager.checkBotInSelectedChat(chat)
                                        }
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
                                            Text(stringResource(R.string.the_bot_needs_to_be_added_to_group))
                                            Row(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        pendingGroupChat = chat
                                                        showGroupSetupDialog = true
                                                    }
                                                ) {
                                                    Text(stringResource(R.string.add_bot_to_chat))
                                                }
                                                OutlinedButton(
                                                    onClick = { coroutineScope.launch { telegramChatManager.checkBotInSelectedChat(chat) } }
                                                ) {
                                                    Text(stringResource(R.string.refresh_status))
                                                }
                                            }
                                        }
                                    }
                                }
                                !chat.isUserMember -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = stringResource(R.string.user_not_in_group),
                                                style = MaterialTheme.typography.subtitle1,
                                                color = MaterialTheme.colors.error
                                            )
                                            Text(stringResource(R.string.user_must_be_in_group))
                                            Row(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        pendingGroupChat = chat
                                                        showGroupSetupDialog = true
                                                    }
                                                ) {
                                                    Text(stringResource(R.string.join_group))
                                                }
                                                OutlinedButton(
                                                    onClick = { coroutineScope.launch { telegramChatManager.checkBotInSelectedChat(chat) } }
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
                                                        onClick = {
                                                            isSendingStart = true
                                                            telegramBotHelper.sendStartCommand(
                                                                chatId = chat.chatId,
                                                                onResult = {
                                                                    isSendingStart = false
                                                                    selectedChat = chat.copy(isBotActive = true)
                                                                    chats = chats.map {
                                                                        if (it.chatId == chat.chatId) it.copy(isBotActive = true) else it
                                                                    }
                                                                    SettingsRepository.saveChats(chats)
                                                                },
                                                                onError = { error ->
                                                                    isSendingStart = false
                                                                    chatError = context.getString(R.string.failed_to_activate_bot, error)
                                                                }
                                                            )
                                                        }
                                                    ) {
                                                        Text(stringResource(R.string.activate_bot))
                                                    }
                                                    OutlinedButton(
                                                        onClick = { coroutineScope.launch { telegramChatManager.checkBotInSelectedChat(chat) } }
                                                    ) {
                                                        Text(stringResource(R.string.refresh_status))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                chat.isBotMember && chat.isBotActive && chat.isUserMember -> {
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
                                                contentDescription = stringResource(R.string.ready_to_send_position_updates),
                                                tint = Color.Green,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.ready_to_send_position_updates),
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
                                                    onClick = { telegramChatName = ""; telegramChatId = ""; selectedChat = null }
                                                ) {
                                                    Text(stringResource(R.string.change))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showGroupSetupDialog) {
                    AlertDialog(
                        onDismissRequest = { showGroupSetupDialog = false },
                        title = { Text(stringResource(R.string.telegram_group_title)) },
                        text = {
                            Column {
                                Text(
                                    text = stringResource(R.string.group_setup_message),
                                    style = MaterialTheme.typography.body2
                                )
                            }
                        },
                        buttons = {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isAddingNewChat = true
                                        isSelectingExistingGroup = false
                                        botInfo?.let { info ->
                                            telegramBotHelper.openTelegramToAddBot(context, info.username)
                                        } ?: run { chatError = context.getString(R.string.bot_info_unavailable) }
                                        showGroupSetupDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.add_bot_to_group))
                                }
                                OutlinedButton(
                                    onClick = {
                                        isSelectingExistingGroup = true
                                        isAddingNewChat = false
                                        botInfo?.let { info ->
                                            telegramBotHelper.openTelegramToSelectGroup(context)
                                        } ?: run { chatError = context.getString(R.string.bot_info_unavailable) }
                                        showGroupSetupDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.select_group_with_bot))
                                }
                                TextButton(
                                    onClick = { showGroupSetupDialog = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    )
                }

                if (showSelectGroupDialog) {
                    var selectedGroup by remember { mutableStateOf<TelegramChat?>(null) }
                    AlertDialog(
                        onDismissRequest = { showSelectGroupDialog = false },
                        title = { Text(stringResource(R.string.select_your_group_title)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.select_your_group_message))
                                Spacer(modifier = Modifier.height(8.dp))
                                if (chats.any { it.isGroup && it.isBotMember && it.isBotActive }) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                    ) {
                                        items(
                                            items = chats.filter { it.isGroup && it.isBotMember && it.isBotActive },
                                            key = { it.chatId }
                                        ) { chat ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedGroup = chat }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = selectedGroup?.chatId == chat.chatId,
                                                    onClick = { selectedGroup = chat }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = chat.title,
                                                    style = TextStyle(fontSize = 16.sp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(stringResource(R.string.no_groups_available))
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    selectedGroup?.let { chat ->
                                        telegramChatId = chat.chatId
                                        telegramChatName = chat.title
                                        selectedChat = chat
                                        coroutineScope.launch {
                                            telegramChatManager.checkBotInSelectedChat(chat)
                                        }
                                    }
                                    showSelectGroupDialog = false
                                },
                                enabled = selectedGroup != null
                            ) {
                                Text(stringResource(R.string.select_group_button))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSelectGroupDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
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
                                    initBotAndFetchChats()
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
                                if (chat.isBotMember && chat.isBotActive && chat.isUserMember) {
                                    coroutineScope.launch {
                                        val task = Task(
                                            taskType = "SendTelegramPosition",
                                            taskData = chat.chatId,
                                            taskName = chat.title,
                                            launchInBackground = false
                                        )
                                        taskDao.insert(task)
                                        onConfirm()
                                    }
                                }
                            }
                        },
                        enabled = selectedChat?.let { chat ->
                            chat.isBotMember && chat.isBotActive && chat.isUserMember
                        } ?: false
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}
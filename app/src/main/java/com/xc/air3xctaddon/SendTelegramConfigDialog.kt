package com.xc.air3xctaddon.ui

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
import com.xc.air3xctaddon.TelegramGroup
import com.xc.air3xctaddon.ui.components.DropdownMenuSpinner
import com.xc.air3xctaddon.ui.components.SpinnerItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SendTelegramConfigDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var telegramChatId by remember { mutableStateOf("") }
    var telegramGroupName by remember { mutableStateOf("") }
    var isLoadingGroups by remember { mutableStateOf(true) }
    var isCheckingBot by remember { mutableStateOf(false) }
    var isSendingStart by remember { mutableStateOf(false) }
    var groupError by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<TelegramGroup>>(emptyList()) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var hasNetworkPermission by remember { mutableStateOf(false) }
    var botInfo by remember { mutableStateOf<TelegramBotInfo?>(null) }
    var selectedGroup by remember { mutableStateOf<TelegramGroup?>(null) }
    var showBotSetupDialog by remember { mutableStateOf(false) }
    var isAddingNewGroup by remember { mutableStateOf(false) }

    // Extract string resources at the composable level
    val otherOptionText = stringResource(R.string.other_option)
    val selectGroupOptionText = stringResource(R.string.select_group_option)

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

    fun checkBotInSelectedGroup() {
        selectedGroup?.let { group ->
            isCheckingBot = true
            telegramBotHelper.checkBotInGroup(
                chatId = group.chatId,
                onResult = { isMember, isActive ->
                    isCheckingBot = false
                    selectedGroup = group.copy(isBotMember = isMember, isBotActive = isActive)
                    groups = groups.map {
                        if (it.chatId == group.chatId) it.copy(isBotMember = isMember, isBotActive = isActive) else it
                    }
                    Log.d("SendTelegramConfigDialog", "Checked group ${group.title}: isMember=$isMember, isActive=$isActive")
                    if (!isMember) {
                        showBotSetupDialog = true
                    }
                    // Don't auto-confirm when bot is ready
                },
                onError = { error ->
                    isCheckingBot = false
                    groupError = context.getString(R.string.failed_to_check_bot_status, error)
                    selectedGroup = group.copy(isBotMember = false, isBotActive = false)
                    Log.e("SendTelegramConfigDialog", "Error checking bot in group ${group.title}: $error")
                }
            )
        }
    }

    suspend fun fetchGroups(retryCount: Int = 0, maxRetries: Int = 2) {
        Log.d("SendTelegramConfigDialog", "fetchGroups called: retryCount=$retryCount, maxRetries=$maxRetries")
        if (retryCount > maxRetries) {
            isLoadingGroups = false
            groupError = context.getString(R.string.failed_to_fetch_groups_retries, maxRetries)
            Log.e("SendTelegramConfigDialog", "Max retries reached for fetchGroups")
            return
        }
        isLoadingGroups = true
        groupError = null
        telegramBotHelper.fetchGroups(
            onResult = { fetchedGroups ->
                Log.d("SendTelegramConfigDialog", "Fetched groups: ${fetchedGroups.map { it.title }}")
                groups = fetchedGroups
                isLoadingGroups = false
                if (fetchedGroups.isEmpty() || (telegramChatId.isNotEmpty() && fetchedGroups.none { it.chatId == telegramChatId })) {
                    telegramChatId = ""
                    telegramGroupName = ""
                    selectedGroup = null
                }
                if (isAddingNewGroup && fetchedGroups.isNotEmpty()) {
                    // Select the most recently added group (highest chatId)
                    val targetGroup = fetchedGroups.maxByOrNull { it.chatId.toLongOrNull() ?: Long.MIN_VALUE }
                    targetGroup?.let { group ->
                        telegramChatId = group.chatId
                        telegramGroupName = group.title
                        selectedGroup = group
                        isAddingNewGroup = false // Reset flag
                        Log.d("SendTelegramConfigDialog", "Selected new group: ${group.title}")
                        coroutineScope.launch {
                            checkBotInSelectedGroup()
                        }
                    }
                }
            },
            onError = { error ->
                Log.w("SendTelegramConfigDialog", "Fetch groups error (retry $retryCount/$maxRetries): $error")
                if (retryCount < maxRetries) {
                    coroutineScope.launch {
                        delay(1000L * (retryCount + 1))
                        fetchGroups(retryCount + 1, maxRetries)
                    }
                } else {
                    isLoadingGroups = false
                    groupError = context.getString(R.string.failed_to_fetch_groups_error, error)
                }
            }
        )
    }

    fun sendStartCommand() {
        selectedGroup?.let { group ->
            isSendingStart = true
            telegramBotHelper.sendStartCommand(
                chatId = group.chatId,
                onResult = {
                    isSendingStart = false
                    selectedGroup = group.copy(isBotActive = true)
                    groups = groups.map {
                        if (it.chatId == group.chatId) it.copy(isBotActive = true) else it
                    }
                    Log.d("SendTelegramConfigDialog", "Bot activated in group ${group.title}")
                },
                onError = { error ->
                    isSendingStart = false
                    groupError = context.getString(R.string.failed_to_activate_bot, error)
                    Log.e("SendTelegramConfigDialog", "Error activating bot in group ${group.title}: $error")
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
            groupError = context.getString(R.string.location_permission_denied)
        }
        if (!hasNetworkPermission) {
            Log.w("SendTelegramConfigDialog", context.getString(R.string.log_network_permission_denied))
        }
        if (hasLocationPermission) {
            coroutineScope.launch {
                Log.d("SendTelegramConfigDialog", context.getString(R.string.log_permission_granted))
                fetchGroups()
            }
        }
    }

    // Monitor lifecycle to detect app resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isAddingNewGroup) {
                coroutineScope.launch {
                    Log.d("SendTelegramConfigDialog", "App resumed, fetching groups")
                    fetchGroups()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(selectedGroup?.chatId) {
        selectedGroup?.let { group ->
            if (group.chatId.isNotEmpty()) {
                checkBotInSelectedGroup()
            }
        }
    }

    LaunchedEffect(Unit) {
        telegramBotHelper.getBotInfo(
            onResult = { info -> botInfo = info },
            onError = { error ->
                Log.e("SendTelegramConfigDialog", "Failed to get bot info: $error")
                groupError = context.getString(R.string.failed_to_get_bot_info, error)
                isLoadingGroups = false
            }
        )
        permissionLauncher.launch(arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_NETWORK_STATE
        ))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 280.dp)
                .padding(16.dp),
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
                        text = stringResource(R.string.configure_telegram_position),
                        style = MaterialTheme.typography.h6
                    )
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                Log.d("SendTelegramConfigDialog", context.getString(R.string.log_manual_refresh))
                                fetchGroups()
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
                    isLoadingGroups -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.searching_for_available_groups))
                        }
                    }
                    groupError != null -> {
                        Card(
                            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = groupError ?: stringResource(R.string.error_loading_groups),
                                    color = MaterialTheme.colors.error
                                )
                                Button(
                                    onClick = {
                                        groupError = null
                                        coroutineScope.launch {
                                            Log.d("SendTelegramConfigDialog", context.getString(R.string.log_retry_fetch))
                                            fetchGroups()
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                    groups.isEmpty() -> {
                        Card(
                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(R.string.no_groups_found_with_the_bot),
                                    style = MaterialTheme.typography.subtitle1
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.to_get_started),
                                    style = MaterialTheme.typography.body2
                                )
                                Text(stringResource(R.string.create_or_choose_a_telegram_group), style = MaterialTheme.typography.body2)
                                Text(stringResource(R.string.add_the_bot_to_that_group), style = MaterialTheme.typography.body2)
                                Text(stringResource(R.string.send_start_in_the_group), style = MaterialTheme.typography.body2)
                                Text(stringResource(R.string.come_back_here_and_refresh), style = MaterialTheme.typography.body2)
                                Row(
                                    modifier = Modifier.padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showBotSetupDialog = true },
                                        enabled = botInfo != null
                                    ) {
                                        Text(stringResource(R.string.add_bot_to_group))
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
                                                Log.d("SendTelegramConfigDialog", context.getString(R.string.log_empty_groups_refresh))
                                                fetchGroups()
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
                        Text(stringResource(R.string.select_the_group_where_you_want_to_send_position_updates))
                        DropdownMenuSpinner(
                            context = context,
                            items = groups.map { SpinnerItem.Item(it.title) } + SpinnerItem.Item(otherOptionText),
                            selectedItem = if (telegramGroupName.isEmpty() || groups.none { it.title == telegramGroupName }) selectGroupOptionText else telegramGroupName,
                            onItemSelected = { selectedTitle ->
                                if (selectedTitle == otherOptionText) {
                                    telegramGroupName = ""
                                    telegramChatId = ""
                                    selectedGroup = null
                                    showBotSetupDialog = true
                                    isAddingNewGroup = true
                                    Log.d("SendTelegramConfigDialog", context.getString(R.string.log_selected_other))
                                } else {
                                    groups.find { it.title == selectedTitle }?.let { group ->
                                        telegramChatId = group.chatId
                                        telegramGroupName = group.title
                                        selectedGroup = group
                                        checkBotInSelectedGroup()
                                        isAddingNewGroup = false
                                        Log.d("SendTelegramConfigDialog", context.getString(R.string.log_selected_group, group.title))
                                    }
                                }
                            },
                            label = stringResource(R.string.telegram_group_label),
                            modifier = Modifier.fillMaxWidth()
                        )
                        selectedGroup?.let { group ->
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
                                !group.isBotMember -> {
                                    Card(
                                        backgroundColor = MaterialTheme.colors.secondary.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = stringResource(R.string.bot_setup_required),
                                                style = MaterialTheme.typography.subtitle1,
                                                color = MaterialTheme.colors.secondary
                                            )
                                            Text(stringResource(R.string.the_bot_needs_to_be_added_to_this_group))
                                            Row(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { showBotSetupDialog = true }
                                                ) {
                                                    Text(stringResource(R.string.add_bot_to_group))
                                                }
                                                OutlinedButton(
                                                    onClick = { checkBotInSelectedGroup() }
                                                ) {
                                                    Text(stringResource(R.string.refresh_status))
                                                }
                                            }
                                        }
                                    }
                                }
                                group.isBotMember && !group.isBotActive -> {
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
                                            Text(stringResource(R.string.the_bot_is_in_the_group_but_needs_to_be_activated))
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
                                                        onClick = { checkBotInSelectedGroup() }
                                                    ) {
                                                        Text(stringResource(R.string.refresh_status))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                group.isBotMember && group.isBotActive -> {
                                    Card(
                                        backgroundColor = Color.Green.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp), // Reduced padding from 12dp to 8dp
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp) // Changed from SpaceBetween to spacedBy
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = stringResource(R.string.ready_to_send_position_updates),
                                                tint = Color.Green,
                                                modifier = Modifier.size(16.dp) // Added size constraint for icon
                                            )
                                            Text(
                                                text = stringResource(R.string.ready_to_send_position_updates),
                                                color = Color.Green,
                                                style = MaterialTheme.typography.body2, // Changed from subtitle1 to body2
                                                modifier = Modifier.weight(1f) // Take up remaining space
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Add a card around the group selection to make it clear it's selectable
                                    Card(
                                        backgroundColor = MaterialTheme.colors.surface,
                                        elevation = 2.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = stringResource(R.string.selected_group),
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
                                                    text = group.title,
                                                    style = MaterialTheme.typography.body1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(
                                                    onClick = {
                                                        // Reset selection to allow choosing a different group
                                                        telegramGroupName = ""
                                                        telegramChatId = ""
                                                        selectedGroup = null
                                                    }
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

                if (showBotSetupDialog) {
                    AlertDialog(
                        onDismissRequest = { showBotSetupDialog = false },
                        title = { Text(stringResource(R.string.add_bot_to_group_title)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.select_group_to_add_bot))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.open_telegram_prompt))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.return_and_refresh_instructions), style = MaterialTheme.typography.caption)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    isAddingNewGroup = true
                                    botInfo?.let { info ->
                                        telegramBotHelper.openTelegramToAddBot(context, info.username)
                                    }
                                    showBotSetupDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.open_telegram_button))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBotSetupDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
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
                            selectedGroup?.let { group ->
                                if (group.isBotMember && group.isBotActive) {
                                    coroutineScope.launch {
                                        val task = Task(
                                            taskType = "SendTelegramPosition",
                                            taskData = group.chatId,
                                            taskName = group.title,
                                            launchInBackground = false
                                        )
                                        taskDao.insert(task)
                                        Log.d("SendTelegramConfigDialog", "Saved task: type=${task.taskType}, chatId=${task.taskData}, name=${task.taskName}")
                                        onConfirm()
                                    }
                                }
                            }
                        },
                        enabled = selectedGroup?.isBotMember == true && selectedGroup?.isBotActive == true
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}
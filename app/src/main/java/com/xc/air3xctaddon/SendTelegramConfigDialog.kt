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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.location.LocationServices
import com.xc.air3xctaddon.BuildConfig
import com.xc.air3xctaddon.TelegramBotHelper
import com.xc.air3xctaddon.TelegramGroup
import com.xc.air3xctaddon.TelegramBotInfo
import com.xc.air3xctaddon.ui.components.DropdownMenuSpinner
import com.xc.air3xctaddon.ui.components.SpinnerItem
import com.xc.air3xctaddon.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SendTelegramConfigDialog(
    onAdd: (String, String) -> Unit, // Updated to return chatId and groupName
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

    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val telegramBotHelper = remember { TelegramBotHelper(BuildConfig.TELEGRAM_BOT_TOKEN, fusedLocationClient) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun fetchGroups(retryCount: Int = 0, maxRetries: Int = 2) {
        Log.d("ConfigRow", "fetchGroups called: retryCount=$retryCount, maxRetries=$maxRetries")
        if (retryCount > maxRetries) {
            isLoadingGroups = false
            groupError = "Failed to fetch groups after $maxRetries retries"
            Log.e("ConfigRow", "Max retries reached for fetchGroups")
            return
        }
        isLoadingGroups = true
        groupError = null
        telegramBotHelper.fetchGroups(
            onResult = { fetchedGroups ->
                Log.d("ConfigRow", "Fetched groups: ${fetchedGroups.map { it.title }}")
                groups = fetchedGroups
                isLoadingGroups = false
                if (fetchedGroups.isEmpty() || (telegramChatId.isNotEmpty() && fetchedGroups.none { it.chatId == telegramChatId })) {
                    telegramChatId = ""
                    telegramGroupName = ""
                    selectedGroup = null
                }
                if (telegramChatId.isEmpty() && fetchedGroups.isNotEmpty()) {
                    val targetGroup = if (isAddingNewGroup) {
                        fetchedGroups.maxByOrNull { it.chatId.toLongOrNull() ?: Long.MIN_VALUE }
                    } else {
                        fetchedGroups.firstOrNull { it.isBotMember && it.isBotActive } ?: fetchedGroups.first()
                    }
                    targetGroup?.let { group ->
                        telegramChatId = group.chatId
                        telegramGroupName = group.title
                        selectedGroup = group
                        Log.d("ConfigRow", "Auto-selected group: ${group.title}, isAddingNewGroup=$isAddingNewGroup")
                    }
                }
            },
            onError = { error ->
                Log.w("ConfigRow", "Fetch groups error (retry $retryCount/$maxRetries): $error")
                if (retryCount < maxRetries) {
                    coroutineScope.launch {
                        delay(1000L * (retryCount + 1))
                        fetchGroups(retryCount + 1, maxRetries)
                    }
                } else {
                    isLoadingGroups = false
                    groupError = "Failed to fetch groups: $error"
                }
            }
        )
    }

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
                    Log.d("ConfigRow", "Checked group ${group.title}: isMember=$isMember, isActive=$isActive")
                    if (!isMember) {
                        showBotSetupDialog = true
                    }
                },
                onError = { error ->
                    isCheckingBot = false
                    groupError = "Failed to check bot status: $error"
                    selectedGroup = group.copy(isBotMember = false, isBotActive = false)
                    Log.e("ConfigRow", "Error checking bot in group ${group.title}: $error")
                }
            )
        }
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
                    onAdd(group.chatId, group.title) // Updated to pass groupName
                    Log.d("ConfigRow", "Bot activated in group ${group.title}, chatId=${group.chatId}")
                },
                onError = { error ->
                    isSendingStart = false
                    groupError = "Failed to activate bot: $error"
                    Log.e("ConfigRow", "Error activating bot in group ${group.title}: $error")
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
            groupError = "Location permission denied. Please grant permission to continue."
        }
        if (!hasNetworkPermission) {
            Log.w("ConfigRow", "Network state permission denied")
        }
        if (hasLocationPermission) {
            coroutineScope.launch {
                Log.d("ConfigRow", "Permission granted, fetching groups")
                fetchGroups()
            }
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
                Log.e("TelegramDialog", "Failed to get bot info: $error")
                groupError = "Failed to get bot info: $error"
                isLoadingGroups = false
            }
        )
        permissionLauncher.launch(arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_NETWORK_STATE
        ))
    }

    LaunchedEffect(showBotSetupDialog) {
        if (!showBotSetupDialog) {
            isAddingNewGroup = false
            fetchGroups()
        }
    }

    if (showBotSetupDialog) {
        AlertDialog(
            onDismissRequest = { showBotSetupDialog = false },
            title = { Text("Add Bot to Group") },
            text = {
                Column {
                    Text("Select the Group to which you want to add the bot to.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Would you like to open Telegram to add the bot to an existing group?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("After adding the bot, return here and refresh to continue.", style = MaterialTheme.typography.caption)
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
                    Text("Open Telegram")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBotSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 800.dp)
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
                        text = "Configure Telegram Position",
                        style = MaterialTheme.typography.h6
                    )
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                Log.d("ConfigRow", "Manual refresh triggered")
                                fetchGroups()
                            }
                        },
                        enabled = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
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
                            Text("Searching for available groups...")
                        }
                    }
                    groupError != null -> {
                        Card(
                            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = groupError ?: "Error loading groups",
                                    color = MaterialTheme.colors.error
                                )
                                Button(
                                    onClick = {
                                        groupError = null
                                        coroutineScope.launch {
                                            Log.d("ConfigRow", "Retry fetch triggered")
                                            fetchGroups()
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(stringResource(id = R.string.retry))
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
                                    text = "No groups found with the bot",
                                    style = MaterialTheme.typography.subtitle1
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "To get started:",
                                    style = MaterialTheme.typography.body2
                                )
                                Text("1. Create or choose a Telegram group", style = MaterialTheme.typography.body2)
                                Text("2. Add the bot to that group", style = MaterialTheme.typography.body2)
                                Text("3. Send /start in the group", style = MaterialTheme.typography.body2)
                                Text("4. Come back here and refresh", style = MaterialTheme.typography.body2)
                                Row(
                                    modifier = Modifier.padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showBotSetupDialog = true },
                                        enabled = botInfo != null
                                    ) {
                                        Text("Add Bot to Group")
                                    }
                                    Button(
                                        onClick = {
                                            botInfo?.let { info ->
                                                telegramBotHelper.openTelegramToAddBot(context, info.username)
                                            }
                                        },
                                        enabled = botInfo != null
                                    ) {
                                        Text("Open Bot in Telegram")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                Log.d("ConfigRow", "Empty groups refresh triggered")
                                                fetchGroups()
                                            }
                                        }
                                    ) {
                                        Text("Refresh")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text("Select the group where you want to send position updates:")
                        DropdownMenuSpinner(
                            items = groups.map { SpinnerItem.Item(it.title) } + SpinnerItem.Item("Other..."),
                            selectedItem = if (telegramGroupName.isEmpty() || groups.none { it.title == telegramGroupName }) "Select Group" else telegramGroupName,
                            onItemSelected = { selectedTitle ->
                                if (selectedTitle == "Other...") {
                                    telegramGroupName = ""
                                    telegramChatId = ""
                                    selectedGroup = null
                                    showBotSetupDialog = true
                                    isAddingNewGroup = true
                                    Log.d("ConfigRow", "Selected 'Other...', opening bot setup dialog")
                                } else {
                                    groups.find { it.title == selectedTitle }?.let { group ->
                                        telegramChatId = group.chatId
                                        telegramGroupName = group.title
                                        selectedGroup = group
                                        checkBotInSelectedGroup()
                                        isAddingNewGroup = false
                                        Log.d("ConfigRow", "Selected group: ${group.title}")
                                    }
                                }
                            },
                            label = "Telegram Group",
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
                                        Text("Checking bot status...")
                                    }
                                }
                                !group.isBotMember -> {
                                    Card(
                                        backgroundColor = MaterialTheme.colors.secondary.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Bot Setup Required",
                                                style = MaterialTheme.typography.subtitle1,
                                                color = MaterialTheme.colors.secondary
                                            )
                                            Text("The bot needs to be added to this group.")
                                            Row(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { showBotSetupDialog = true }
                                                ) {
                                                    Text("Add Bot to Group")
                                                }
                                                OutlinedButton(
                                                    onClick = { checkBotInSelectedGroup() }
                                                ) {
                                                    Text("Refresh Status")
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
                                                text = "Activate Bot",
                                                style = MaterialTheme.typography.subtitle1,
                                                color = MaterialTheme.colors.primary
                                            )
                                            Text("The bot is in the group but needs to be activated.")
                                            if (isSendingStart) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(top = 8.dp)
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                                    Text("Activating bot...")
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = { sendStartCommand() }
                                                    ) {
                                                        Text("Activate Bot")
                                                    }
                                                    OutlinedButton(
                                                        onClick = { checkBotInSelectedGroup() }
                                                    ) {
                                                        Text("Refresh Status")
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
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Ready",
                                                    tint = Color.Green
                                                )
                                                Text(
                                                    text = "Ready to send position updates!",
                                                    color = Color.Green,
                                                    style = MaterialTheme.typography.subtitle1
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { checkBotInSelectedGroup() }
                                            ) {
                                                Text("Refresh")
                                            }
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
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(telegramChatId, telegramGroupName) }, // Updated to pass groupName
                        enabled = selectedGroup?.isBotMember == true && selectedGroup?.isBotActive == true
                    ) {
                        Text(stringResource(id = R.string.confirm))
                    }
                }
            }
        }
    }
}
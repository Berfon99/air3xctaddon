package com.xc.air3xctaddon

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.xc.air3xctaddon.ui.components.SelectTaskTypeDialog
import com.xc.air3xctaddon.ui.SendTelegramMessageConfigDialog
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.pm.PackageManager

class SettingsActivity : ComponentActivity() {
    private val _tasks = mutableStateListOf<Task>()
    private val tasks: List<Task> get() = _tasks
    private var authenticationPending by mutableStateOf(false)
    private var onAuthenticationSuccess: ((String) -> Unit)? = null

    private val taskResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val taskType = data.getStringExtra("task_type")
                val taskPackage = data.getStringExtra("task_package")
                val taskName = data.getStringExtra("task_name")
                val launchInBackground = data.getBooleanExtra("launch_in_background", true)
                Log.d("SettingsActivity", "Received task: type=$taskType, package=$taskPackage, name=$taskName, launchInBackground=$launchInBackground")
                if (taskType == "LaunchApp" && taskPackage != null && taskName != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val task = Task(
                            id = 0,
                            taskType = taskType,
                            taskData = taskPackage,
                            taskName = taskName,
                            launchInBackground = launchInBackground
                        )
                        Log.d("SettingsActivity", "Inserting Task: id=${task.id}, type=${task.taskType}, data=${task.taskData}, name=${task.taskName}, launchInBackground=${task.launchInBackground}")
                        db.taskDao().insert(task)
                        delay(100)
                        val syncTasks = db.taskDao().getAllTasksSync()
                        Log.d("SettingsActivity", "Sync tasks after insert: ${syncTasks.map { "id=${it.id}, type=${it.taskType}, data=${it.taskData}, launchInBackground=${it.launchInBackground}" }}")
                    }
                }
            }
        }
    }

    private fun isTelegramInstalled(): Boolean {
        val telegramPackages = listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.plus",
            "nekox.messenger",
            "org.thunderdog.challegram",
            "com.telegram.messenger"
        )

        val packageManager = packageManager
        return telegramPackages.any { packageName ->
            try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SettingsActivity", "onCreate called")
        SettingsRepository.initialize(applicationContext) // Initialize singleton
        setContent {
            AIR3XCTAddonTheme {
                var authenticationTrigger by remember { mutableStateOf(0) }
                var showTaskTypeDialog by remember { mutableStateOf(intent.getBooleanExtra("open_task_type_dialog", false)) }
                var showTelegramPositionDialog by remember { mutableStateOf(false) }
                var showTelegramMessageDialog by remember { mutableStateOf(false) }
                var showPilotNameDialog by remember { mutableStateOf(false) }
                var showPilotNameWarningDialog by remember { mutableStateOf(false) }
                var showTelegramNotInstalledDialog by remember { mutableStateOf(false) }
                var showTelegramAuthRequiredDialog by remember { mutableStateOf(false) }
                var showAuthenticationDialog by remember { mutableStateOf(false) }
                var pendingTelegramTaskType by remember { mutableStateOf<String?>(null) }
                var botUsername by remember { mutableStateOf<String?>(null) }
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                val telegramBotHelper = remember {
                    TelegramBotHelper(
                        context = applicationContext,
                        botToken = BuildConfig.TELEGRAM_BOT_TOKEN,
                        fusedLocationClient = fusedLocationClient
                    )
                }

                LaunchedEffect(Unit) {
                    telegramBotHelper.getBotInfo(
                        onResult = { botInfo ->
                            botUsername = botInfo.username
                            Log.d("SettingsActivity", "Bot username fetched: ${botInfo.username}")
                        },
                        onError = { error ->
                            Log.e("SettingsActivity", "Failed to get bot info: $error")
                        }
                    )
                }

                SettingsScreen(
                    onAddTask = { showTaskTypeDialog = true },
                    onClearTasks = {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.taskDao().deleteAll("LaunchApp")
                            db.taskDao().deleteAll("SendTelegramPosition")
                            db.taskDao().deleteAll("SendTelegramMessage")
                            Log.d("SettingsActivity", "Cleared all tasks: LaunchApp, SendTelegramPosition, SendTelegramMessage")
                        }
                    },
                    telegramBotHelper = telegramBotHelper,
                    onAuthenticationStarted = { callback ->
                        authenticationPending = true
                        onAuthenticationSuccess = callback
                    },
                    onAuthenticationCancelled = {
                        authenticationPending = false
                        onAuthenticationSuccess = null
                    },
                    isTelegramInstalled = { isTelegramInstalled() },
                    showAuthenticationDialog = showAuthenticationDialog,
                    setShowAuthenticationDialog = { showAuthenticationDialog = it },
                    botUsername = botUsername,
                    authenticationTrigger = authenticationTrigger,
                    onAuthenticationCleared = { authenticationTrigger++ }
                )

                if (showTaskTypeDialog) {
                    SelectTaskTypeDialog(
                        onLaunchAppSelected = {
                            taskResultLauncher.launch(Intent(this, AddTaskActivity::class.java))
                            showTaskTypeDialog = false
                        },
                        onTelegramPositionSelected = {
                            showTaskTypeDialog = false
                            if (SettingsRepository.getPilotName().isNullOrBlank()) {
                                showPilotNameWarningDialog = true
                                pendingTelegramTaskType = "SendTelegramPosition"
                            } else if (!SettingsRepository.isTelegramValidated()) {
                                showTelegramAuthRequiredDialog = true
                                pendingTelegramTaskType = "SendTelegramPosition"
                            } else if (!isTelegramInstalled()) {
                                showTelegramNotInstalledDialog = true
                            } else {
                                showTelegramPositionDialog = true
                            }
                        },
                        onTelegramMessageSelected = {
                            showTaskTypeDialog = false
                            if (SettingsRepository.getPilotName().isNullOrBlank()) {
                                showPilotNameWarningDialog = true
                                pendingTelegramTaskType = "SendTelegramMessage"
                            } else if (!SettingsRepository.isTelegramValidated()) {
                                showTelegramAuthRequiredDialog = true
                                pendingTelegramTaskType = "SendTelegramMessage"
                            } else if (!isTelegramInstalled()) {
                                showTelegramNotInstalledDialog = true
                            } else {
                                showTelegramMessageDialog = true
                            }
                        },
                        onDismiss = { showTaskTypeDialog = false }
                    )
                }

                if (showTelegramPositionDialog) {
                    SendTelegramConfigDialog(
                        onConfirm = { showTelegramPositionDialog = false },
                        onDismiss = { showTelegramPositionDialog = false }
                    )
                }

                if (showTelegramMessageDialog) {
                    SendTelegramMessageConfigDialog(
                        onConfirm = { showTelegramMessageDialog = false },
                        onDismiss = { showTelegramMessageDialog = false }
                    )
                }

                if (showPilotNameDialog) {
                    TextInputDialog(
                        title = stringResource(R.string.add_pilot_name),
                        label = stringResource(R.string.pilot_name_label),
                        initialValue = SettingsRepository.getPilotName() ?: "",
                        onConfirm = { newPilotName ->
                            if (newPilotName.isNotBlank()) {
                                SettingsRepository.savePilotName(newPilotName)
                                if (SettingsRepository.isTelegramValidated() && isTelegramInstalled()) {
                                    when (pendingTelegramTaskType) {
                                        "SendTelegramPosition" -> showTelegramPositionDialog = true
                                        "SendTelegramMessage" -> showTelegramMessageDialog = true
                                    }
                                    pendingTelegramTaskType = null
                                } else if (!isTelegramInstalled()) {
                                    showTelegramNotInstalledDialog = true
                                } else {
                                    showTelegramAuthRequiredDialog = true
                                }
                            }
                            showPilotNameDialog = false
                        },
                        onDismiss = { showPilotNameDialog = false }
                    )
                }

                if (showPilotNameWarningDialog) {
                    AlertDialog(
                        onDismissRequest = { showPilotNameWarningDialog = false },
                        title = { Text(stringResource(R.string.pilot_name_required_title)) },
                        text = { Text(stringResource(R.string.pilot_name_required_message)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showPilotNameWarningDialog = false
                                    showPilotNameDialog = true
                                }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = {
                                    showPilotNameWarningDialog = false
                                    pendingTelegramTaskType = null
                                }
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }

                if (showTelegramNotInstalledDialog) {
                    AlertDialog(
                        onDismissRequest = { showTelegramNotInstalledDialog = false },
                        title = { Text(stringResource(R.string.telegram_not_installed_title)) },
                        text = { Text(stringResource(R.string.telegram_not_installed_message)) },
                        confirmButton = {
                            Button(
                                onClick = { showTelegramNotInstalledDialog = false }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    )
                }

                if (showTelegramAuthRequiredDialog) {
                    AlertDialog(
                        onDismissRequest = { showTelegramAuthRequiredDialog = false },
                        title = { Text(stringResource(R.string.telegram_authentication_required_title)) },
                        text = { Text(stringResource(R.string.telegram_authentication_required_message)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showTelegramAuthRequiredDialog = false
                                    if (isTelegramInstalled()) {
                                        showAuthenticationDialog = true
                                    } else {
                                        showTelegramNotInstalledDialog = true
                                    }
                                }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = {
                                    showTelegramAuthRequiredDialog = false
                                    pendingTelegramTaskType = null
                                }
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }

                if (showAuthenticationDialog && botUsername != null) {
                    TelegramValidationDialog(
                        botUsername = botUsername!!,
                        onDismiss = {
                            showAuthenticationDialog = false
                            authenticationPending = false
                            onAuthenticationSuccess = null
                        },
                        onValidationSuccess = { userId ->
                            Log.d("SettingsActivity", "TelegramValidationDialog - Authentication succeeded with user ID: $userId")

                            // Save the authentication data
                            SettingsRepository.saveUserId(userId)
                            SettingsRepository.setTelegramValidated(true)

                            // Verify the data was saved correctly
                            val savedUserId = SettingsRepository.getUserId()
                            val isValidated = SettingsRepository.isTelegramValidated()
                            Log.d("SettingsActivity", "After saving - UserId: $savedUserId, Validated: $isValidated")

                            authenticationTrigger++ // Increment trigger to force recomposition
                            showAuthenticationDialog = false

                            // Clear the authentication pending state since we're handling it here
                            authenticationPending = false
                            onAuthenticationSuccess = null

                            when (pendingTelegramTaskType) {
                                "SendTelegramPosition" -> {
                                    if (SettingsRepository.getPilotName().isNullOrBlank()) {
                                        showPilotNameWarningDialog = true
                                        pendingTelegramTaskType = "SendTelegramPosition"
                                    } else {
                                        showTelegramPositionDialog = true
                                        pendingTelegramTaskType = null
                                    }
                                }
                                "SendTelegramMessage" -> {
                                    if (SettingsRepository.getPilotName().isNullOrBlank()) {
                                        showPilotNameWarningDialog = true
                                        pendingTelegramTaskType = "SendTelegramMessage"
                                    } else {
                                        showTelegramMessageDialog = true
                                        pendingTelegramTaskType = null
                                    }
                                }
                                else -> pendingTelegramTaskType = null
                            }
                        },
                        telegramValidation = TelegramValidation(
                            context = applicationContext,
                            botToken = BuildConfig.TELEGRAM_BOT_TOKEN,
                            settingsRepository = SettingsRepository
                        ),
                        settingsRepository = SettingsRepository, // Add this line to fix the error
                        onValidationStarted = { callback ->
                            authenticationPending = true
                            onAuthenticationSuccess = callback
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("SettingsActivity", "onResume called, authenticationPending: $authenticationPending")
        if (authenticationPending) {
            Log.d("SettingsActivity", "Checking authentication on resume")
            val telegramAuthentication = TelegramValidation(
                context = applicationContext,
                botToken = BuildConfig.TELEGRAM_BOT_TOKEN,
                settingsRepository = SettingsRepository
            )
            CoroutineScope(Dispatchers.IO).launch {
                telegramAuthentication.fetchUserId(
                    onResult = { userId ->
                        Log.d("SettingsActivity", "OnResume - Authentication succeeded with user ID: $userId")
                        // Only save if we don't already have this user ID saved
                        val currentUserId = SettingsRepository.getUserId()
                        if (currentUserId != userId) {
                            SettingsRepository.saveUserId(userId)
                            SettingsRepository.setTelegramValidated(true)
                            Log.d("SettingsActivity", "OnResume - Saved new user ID: $userId")
                        } else {
                            Log.d("SettingsActivity", "OnResume - User ID already saved: $currentUserId")
                        }

                        // Always call the callback if it exists
                        onAuthenticationSuccess?.invoke(userId)
                        authenticationPending = false
                        onAuthenticationSuccess = null
                    },
                    onError = { error ->
                        Log.e("SettingsActivity", "Authentication failed on resume: $error")
                        authenticationPending = false
                        onAuthenticationSuccess = null
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SettingsActivity", "onDestroy called")
    }
}

@Composable
fun SettingsScreen(
    onAddTask: () -> Unit = {},
    onClearTasks: () -> Unit = {},
    telegramBotHelper: TelegramBotHelper,
    onAuthenticationStarted: ((String) -> Unit) -> Unit,
    onAuthenticationCancelled: () -> Unit,
    isTelegramInstalled: () -> Boolean,
    showAuthenticationDialog: Boolean,
    setShowAuthenticationDialog: (Boolean) -> Unit,
    botUsername: String?,
    authenticationTrigger: Int = 0,
    onAuthenticationCleared: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(context.applicationContext as android.app.Application)
    )
    val events by viewModel.events.collectAsState()
    var showPilotNameDialog by remember { mutableStateOf(false) }
    var showTelegramNotInstalledDialog by remember { mutableStateOf(false) }
    var showClearAuthConfirmation by remember { mutableStateOf(false) }

    // Make pilot name reactive to changes
    var pilotNameTrigger by remember { mutableStateOf(0) }
    val pilotName = remember(pilotNameTrigger) { SettingsRepository.getPilotName() }

    val launchAppTasks by AppDatabase.getDatabase(context).taskDao()
        .getAllTasks()
        .collectAsState(initial = emptyList())
    val isTelegramAuthenticated = remember(authenticationTrigger) { SettingsRepository.isTelegramValidated() }
    val authenticatedUserId = remember(authenticationTrigger) { SettingsRepository.getUserId() }

    LaunchedEffect(launchAppTasks) {
        Log.d("SettingsScreen", "launchAppTasks updated: ${launchAppTasks.map { "id=${it.id}, taskType=${it.taskType}, taskData=${it.taskData}, launchInBackground=${it.launchInBackground}" }}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Button(
                    onClick = { showPilotNameDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_pilot_name))
                }
            }

            pilotName?.let { name ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.current_pilot_name, name),
                            style = MaterialTheme.typography.body1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isTelegramAuthenticated && authenticatedUserId != null) {
                                Text(
                                    text = stringResource(R.string.telegram_authenticated_with_id, authenticatedUserId),
                                    style = MaterialTheme.typography.body1
                                )
                                IconButton(onClick = { showClearAuthConfirmation = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.clear_telegram_authentication),
                                        tint = MaterialTheme.colors.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (isTelegramInstalled()) {
                                            if (!isTelegramAuthenticated) {
                                                SettingsRepository.clearUserId()
                                                SettingsRepository.clearTelegramValidated()
                                                setShowAuthenticationDialog(true)
                                                Log.d("SettingsScreen", "Initiating Telegram authentication")
                                            }
                                        } else {
                                            showTelegramNotInstalledDialog = true
                                            Log.d("SettingsScreen", "Telegram not installed, showing dialog")
                                        }
                                    },
                                    modifier = Modifier.wrapContentWidth(),
                                    enabled = botUsername != null
                                ) {
                                    Text(stringResource(R.string.telegram_authentication))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, AddEventActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_new_event))
                }
            }

            item {
                val customEventCount = events.count { it is MainViewModel.EventItem.Event }
                Text(stringResource(R.string.event_count, customEventCount))
            }

            item {
                Button(
                    onClick = onAddTask,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_new_task))
                }
            }

            item {
                Button(
                    onClick = onClearTasks,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                ) {
                    Text(stringResource(R.string.clear_all_tasks))
                }
            }

            if (launchAppTasks.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.configured_tasks),
                        style = MaterialTheme.typography.h6
                    )
                }
                items(launchAppTasks, key = { task -> task.id }) { task ->
                    TaskRow(
                        task = task,
                        onDelete = {
                            CoroutineScope(Dispatchers.IO).launch {
                                val db = AppDatabase.getDatabase(context)
                                db.taskDao().delete(task)
                                Log.d("SettingsScreen", "Deleted task: id=${task.id}")
                            }
                        }
                    )
                }
            }
        }

        if (showPilotNameDialog) {
            TextInputDialog(
                title = stringResource(R.string.add_pilot_name),
                label = stringResource(R.string.pilot_name_label),
                initialValue = pilotName ?: "",
                onConfirm = { newPilotName ->
                    if (newPilotName.isNotBlank()) {
                        SettingsRepository.savePilotName(newPilotName)
                        pilotNameTrigger++ // Trigger recomposition
                        Log.d("SettingsScreen", "Saved pilot name: $newPilotName")
                    }
                    showPilotNameDialog = false
                },
                onDismiss = { showPilotNameDialog = false }
            )
        }

        if (showTelegramNotInstalledDialog) {
            AlertDialog(
                onDismissRequest = { showTelegramNotInstalledDialog = false },
                title = { Text(stringResource(R.string.telegram_not_installed_title)) },
                text = { Text(stringResource(R.string.telegram_not_installed_message)) },
                confirmButton = {
                    Button(
                        onClick = { showTelegramNotInstalledDialog = false }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }

        if (showClearAuthConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearAuthConfirmation = false },
                title = { Text("Clear Telegram Authentication") },
                text = { Text("Are you sure you want to clear Telegram authentication? This will require re-authentication to use Telegram tasks.") },
                confirmButton = {
                    Button(
                        onClick = {
                            SettingsRepository.clearTelegramValidated()
                            SettingsRepository.clearUserId()
                            onAuthenticationCleared()
                            Log.d("SettingsScreen", "Cleared Telegram authentication")
                            showClearAuthConfirmation = false
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    Button(onClick = { showClearAuthConfirmation = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

fun getAppName(context: android.content.Context, packageName: String): String {
    return try {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}

@Composable
fun TaskRow(task: Task, onDelete: () -> Unit) {
    val context = LocalContext.current
    val displayText = when (task.taskType) {
        "SendTelegramPosition" -> stringResource(R.string.task_send_telegram_position, task.taskName)
        "SendTelegramMessage" -> stringResource(R.string.task_send_telegram_message, task.taskName)
        "LaunchApp" -> {
            val appName = getAppName(context, task.taskData)
            if (task.launchInBackground) {
                stringResource(R.string.launch_app_background, appName)
            } else {
                stringResource(R.string.launch_app_foreground, appName)
            }
        }
        else -> task.taskName
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.body1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_task),
                tint = MaterialTheme.colors.error
            )
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = text.trim().isNotEmpty()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
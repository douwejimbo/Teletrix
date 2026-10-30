package com.nextgenmatrix.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nextgenmatrix.core.model.SessionVerificationPhase
import com.nextgenmatrix.core.model.SessionVerificationPrompt
import com.nextgenmatrix.core.ui.theme.AppThemeMode
import com.nextgenmatrix.core.ui.theme.TeletrixTheme
import com.nextgenmatrix.data.media.MiniPlayerBar
import com.nextgenmatrix.feature.auth.AuthRoute
import com.nextgenmatrix.feature.chat.DirectoryRoute
import com.nextgenmatrix.feature.chat.ChatListRoute
import com.nextgenmatrix.feature.chat.ChatListViewModel
import com.nextgenmatrix.feature.chat.ConversationRoute
import com.nextgenmatrix.feature.chat.SpaceDetailRoute
import com.nextgenmatrix.feature.chat.SpacesRoute
import com.nextgenmatrix.data.push.PushCoordinator
import com.nextgenmatrix.feature.settings.ProfileRoute
import com.nextgenmatrix.feature.settings.SettingsRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var pushCoordinator: PushCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        offerRoomFromIntent(intent)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerRoomFromIntent(intent)
    }

    private fun offerRoomFromIntent(intent: Intent?) {
        val target = roomTargetFromIntent(intent) ?: return
        pushCoordinator.offerOpenRoom(target.roomId, target.eventId)
    }
}

internal data class RoomOpenTarget(val roomId: String, val eventId: String? = null)

internal fun roomTargetFromIntent(intent: Intent?): RoomOpenTarget? {
    if (intent == null) return null
    intent.getStringExtra("room_id")?.takeIf { it.isNotBlank() }?.let { roomId ->
        val event = intent.getStringExtra("event_id")?.takeIf { it.isNotBlank() }
        return RoomOpenTarget(roomId, event)
    }
    val data = intent.data ?: return null
    if (data.scheme != "teletrix" || data.host != "room") return null
    val encoded = data.pathSegments.firstOrNull() ?: return null
    val event = data.getQueryParameter("event")?.takeIf { it.isNotBlank() }
    return RoomOpenTarget(Uri.decode(encoded), event)
}

internal fun roomIdFromIntent(intent: Intent?): String? = roomTargetFromIntent(intent)?.roomId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val appearanceVm: AppearanceViewModel = hiltViewModel()
    val themeMode by appearanceVm.themeMode.collectAsStateWithLifecycle()
    val contrast by appearanceVm.contrastMode.collectAsStateWithLifecycle()
    val dark = when (themeMode) {
        AppThemeMode.Dark -> true
        AppThemeMode.Light -> false
        AppThemeMode.System -> isSystemInDarkTheme()
    }
    val activity = LocalContext.current as MainActivity
    DisposableEffect(activity, dark) {
        val bars = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        onDispose { }
    }
    val sessionVerifyVm: SessionVerificationViewModel = hiltViewModel()
    val sessionPrompt by sessionVerifyVm.prompt.collectAsStateWithLifecycle()
    val chatListVm: ChatListViewModel = hiltViewModel()
    val rooms by chatListVm.rooms.collectAsStateWithLifecycle()
    val openRoomId by activity.pushCoordinator.openRoomId.collectAsStateWithLifecycle()
    val openJump by activity.pushCoordinator.openJump.collectAsStateWithLifecycle()
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val prefs = activity.getSharedPreferences("teletrix_push", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("asked_battery", false) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        ) {
            prefs.edit().putBoolean("asked_battery", true).apply()
            runCatching {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        "package:${activity.packageName}".toUri(),
                    ),
                )
            }
        }
    }
    val musicVm: MusicPlayerViewModel = hiltViewModel()
    val nowPlaying by musicVm.nowPlaying.collectAsStateWithLifecycle()

    TeletrixTheme(darkTheme = dark, contrastMode = contrast) {
        Box(Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            val scheme = MaterialTheme.colorScheme
            val backStackEntry by navController.currentBackStackEntryAsState()
            val route = backStackEntry?.destination?.route.orEmpty()
            LaunchedEffect(openRoomId, route) {
                val dest = openRoomId ?: return@LaunchedEffect
                if (route == "auth" || route.isEmpty()) return@LaunchedEffect
                navController.navigate("room/$dest") {
                    launchSingleTop = true
                }
                activity.pushCoordinator.consumeOpenRoom()
            }
            val showTopBar = route != "auth"
            val inRoom = route.startsWith("room/") || route.startsWith("room/{")
            val inSpace = route.startsWith("space/")
            val roomId = backStackEntry?.arguments?.getString("roomId")
            val roomTitle = rooms.firstOrNull { it.roomId.value == roomId }?.title
            var spaceTitle by rememberSaveable { mutableStateOf("Space") }
            var selectedTab by rememberSaveable { mutableStateOf("chats") }
            if (route == "chats" || route == "spaces" || route == "settings" || route == "profile") {
                selectedTab = route
            } else if (inSpace || route == "directory") {
                selectedTab = "spaces"
            }
            val title = when {
                inRoom -> roomTitle ?: "Chat"
                inSpace -> spaceTitle
                route == "directory" -> "Directory"
                route == "spaces" -> "Spaces"
                route == "settings" -> "Settings"
                route == "profile" -> "Profile"
                else -> "Teletrix"
            }
            val showTabs = route == "chats" || route == "spaces" || route == "directory" || inSpace ||
                route == "settings" || route == "profile"
            val showMiniPlayer = nowPlaying != null && route != "auth"
            val unreadChats = rooms.filter { !it.isSpace }.sumOf { it.unreadCount }
            val canGoBack = inRoom || inSpace || route == "directory"
            val openTab: (String) -> Unit = { dest ->
                navController.navigate(dest) {
                    popUpTo("chats") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            Scaffold(
                containerColor = Color.Transparent,
                contentColor = scheme.onSurface,
                topBar = {
                    if (!showTopBar) return@Scaffold
                    Surface(color = Color.Transparent) {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                if (canGoBack) {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = scheme.onSurface,
                            ),
                        )
                    }
                },
                bottomBar = {
                    Column {
                        if (showMiniPlayer) {
                            MiniPlayerBar(
                                title = nowPlaying?.title.orEmpty(),
                                isPlaying = nowPlaying?.isPlaying == true,
                                onToggle = musicVm::toggle,
                                onPrevious = musicVm::previous,
                                onNext = musicVm::next,
                            )
                        }
                        if (showTabs) {
                            AppTabBar(
                                selected = selectedTab,
                                unreadChats = unreadChats,
                                onSelect = openTab,
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appBackdropBrush())
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "auth",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable("auth") {
                            AuthRoute(
                                onLoggedIn = {
                                    val dest = openRoomId?.let { "room/$it" } ?: "chats"
                                    navController.navigate(dest) {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                    if (openRoomId != null) activity.pushCoordinator.consumeOpenRoom()
                                },
                            )
                        }
                        composable("chats") {
                            ChatListRoute(onOpenRoom = { navController.navigate("room/$it") })
                        }
                        composable("spaces") {
                            SpacesRoute(
                                onOpenSpace = { navController.navigate("space/$it") },
                                onBrowseDirectory = { navController.navigate("directory") },
                            )
                        }
                        composable("directory") {
                            DirectoryRoute(
                                onOpenRoom = { navController.navigate("room/$it") },
                                onOpenSpace = { navController.navigate("space/$it") },
                            )
                        }
                        composable(
                            route = "space/{spaceId}",
                            arguments = listOf(navArgument("spaceId") { type = NavType.StringType }),
                        ) { entry ->
                            val id = entry.arguments?.getString("spaceId")!!
                            SpaceDetailRoute(
                                spaceId = id,
                                onOpenRoom = { navController.navigate("room/$it") },
                                onOpenSpace = { navController.navigate("space/$it") },
                                onTitle = { spaceTitle = it },
                            )
                        }
                        composable(
                            route = "room/{roomId}",
                            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
                        ) { entry ->
                            val id = entry.arguments?.getString("roomId")!!
                            ConversationRoute(
                                roomId = id,
                                initialJumpEventId = openJump?.takeIf { it.roomId == id }?.eventId,
                                onJumpFromNotification = activity.pushCoordinator::consumeOpenEvent,
                            )
                        }
                        composable("settings") {
                            val notifyVm: NotificationSettingsViewModel = hiltViewModel()
                            val notifyPrefs by notifyVm.prefs.collectAsStateWithLifecycle()
                            SettingsRoute(
                                themeMode = themeMode,
                                onThemeMode = appearanceVm::setThemeMode,
                                contrastMode = contrast,
                                onContrastMode = appearanceVm::setContrastMode,
                                notifyPrefs = notifyPrefs,
                                onNotifyPrefs = notifyVm::update,
                                onVerifySession = { sessionVerifyVm.requestVerification() },
                            )
                        }
                        composable("profile") {
                            val profileVm: ProfileViewModel = hiltViewModel()
                            val account by profileVm.account.collectAsStateWithLifecycle()
                            ProfileRoute(account = account, loadAvatar = profileVm::loadAvatar)
                        }
                    }
                }
            }

            sessionPrompt?.let { prompt ->
                when (prompt.phase) {
                    SessionVerificationPhase.SasCompare ->
                        EmojiVerifyDialog(
                            prompt = prompt,
                            onMatch = sessionVerifyVm::approve,
                            onMismatch = sessionVerifyVm::decline,
                        )

                    SessionVerificationPhase.Pending ->
                        AlertDialog(
                            onDismissRequest = { sessionVerifyVm.dismiss() },
                            title = { Text("Session verification") },
                            text = { prompt.message?.let { Text(it) } },
                            confirmButton = {
                                TextButton(onClick = { sessionVerifyVm.continueFromPending() }) {
                                    Text("Continue verification")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { sessionVerifyVm.dismiss() }) { Text("Hide") }
                            },
                        )

                    SessionVerificationPhase.Finished, SessionVerificationPhase.Failed ->
                        AlertDialog(
                            onDismissRequest = { sessionVerifyVm.dismiss() },
                            title = {
                                Text(
                                    if (prompt.phase == SessionVerificationPhase.Failed) {
                                        "Verification"
                                    } else {
                                        "Done"
                                    },
                                )
                            },
                            text = { prompt.message?.let { Text(it) } },
                            confirmButton = {
                                TextButton(onClick = { sessionVerifyVm.dismiss() }) {
                                    Text(
                                        if (prompt.phase == SessionVerificationPhase.Finished) {
                                            "Done"
                                        } else {
                                            "Close"
                                        },
                                    )
                                }
                            },
                        )

                    SessionVerificationPhase.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun EmojiVerifyDialog(
    prompt: SessionVerificationPrompt,
    onMatch: () -> Unit,
    onMismatch: () -> Unit,
) {
    // Removing this dialog (success, failure, or rotation) dismisses the Android
    // window and would invoke onDismissRequest. That must not send a SAS mismatch,
    // or the other client cancels and the emoji check times out.
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            // The default dialog window is only as wide as its smallest child.
            // fillMaxWidth() then draws Match past that window, so the tap misses.
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Verify this device", style = MaterialTheme.typography.titleLarge)
                prompt.message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (prompt.emojis.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        prompt.emojis.forEach { emoji ->
                            Column(
                                modifier = Modifier.widthIn(min = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(emoji.symbol, style = MaterialTheme.typography.headlineMedium)
                                if (emoji.description.isNotBlank()) {
                                    Text(
                                        emoji.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
                val waitingOnOtherDevice = prompt.message?.startsWith("Confirming") == true
                Button(
                    onClick = onMatch,
                    enabled = !waitingOnOtherDevice,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Match") }
                TextButton(
                    onClick = onMismatch,
                    enabled = !waitingOnOtherDevice,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("They don't match") }
            }
        }
    }
}

@Composable
private fun AppTabBar(
    selected: String,
    unreadChats: Int,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = scheme.primary,
        selectedTextColor = scheme.primary,
        indicatorColor = scheme.primary.copy(alpha = 0.16f),
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant,
    )
    NavigationBar(containerColor = scheme.surface.copy(alpha = 0.96f)) {
        NavigationBarItem(
            selected = selected == "chats",
            onClick = { onSelect("chats") },
            colors = colors,
            label = { Text("Chats") },
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadChats > 0) {
                            Badge { Text(if (unreadChats > 99) "99+" else unreadChats.toString()) }
                        }
                    },
                ) {
                    Icon(
                        if (selected == "chats") Icons.Filled.Chat else Icons.Outlined.Chat,
                        contentDescription = "Chats",
                    )
                }
            },
        )
        NavigationBarItem(
            selected = selected == "spaces",
            onClick = { onSelect("spaces") },
            colors = colors,
            label = { Text("Spaces") },
            icon = {
                Icon(
                    if (selected == "spaces") Icons.Filled.GridView else Icons.Outlined.GridView,
                    contentDescription = "Spaces",
                )
            },
        )
        NavigationBarItem(
            selected = selected == "settings",
            onClick = { onSelect("settings") },
            colors = colors,
            label = { Text("Settings") },
            icon = {
                Icon(
                    if (selected == "settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = "Settings",
                )
            },
        )
        NavigationBarItem(
            selected = selected == "profile",
            onClick = { onSelect("profile") },
            colors = colors,
            label = { Text("Profile") },
            icon = {
                Icon(
                    if (selected == "profile") Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile",
                )
            },
        )
    }
}

@Composable
private fun appBackdropBrush(): Brush {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.35f
    return if (dark) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF18264E),
                Color(0xFF0E1733),
                Color(0xFF060913),
            ),
            radius = 1500f,
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFF2F6FF),
                Color(0xFFE8EEFF),
                Color(0xFFDAE6FF),
            ),
            radius = 1700f,
        )
    }
}

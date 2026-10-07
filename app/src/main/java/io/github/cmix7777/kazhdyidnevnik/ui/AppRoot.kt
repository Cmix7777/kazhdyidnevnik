package io.github.cmix7777.kazhdyidnevnik.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cmix7777.kazhdyidnevnik.R

private enum class Tab(val title: String, @param:DrawableRes val icon: Int) {
    Today("Сегодня", R.drawable.ic_nav_today),
    Week("Неделя", R.drawable.ic_nav_week),
    Progress("Прогресс", R.drawable.ic_nav_progress),
    Deadlines("Дедлайны", R.drawable.ic_nav_deadlines),
}

@Composable
fun AppRoot(vm: ScheduleViewModel = viewModel()) {
    var current by rememberSaveable { mutableStateOf(Tab.Today) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    // При первом запуске один раз спрашиваем разрешение на уведомления (Android 13+).
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk && vm.shouldAskNotifications()) {
            vm.markNotificationsAsked()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab && !settingsOpen,
                        onClick = {
                            current = tab
                            settingsOpen = false
                        },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        val screenModifier = Modifier.padding(padding)
        if (settingsOpen) {
            BackHandler { settingsOpen = false }
            SettingsScreen(vm, onBack = { settingsOpen = false }, modifier = screenModifier)
        } else {
            when (current) {
                Tab.Today -> TodayScreen(vm, onOpenSettings = { settingsOpen = true }, modifier = screenModifier)
                Tab.Week -> WeekScreen(vm, screenModifier)
                Tab.Progress -> ProgressScreen(vm, screenModifier)
                Tab.Deadlines -> DeadlinesScreen(screenModifier)
            }
        }
    }
}

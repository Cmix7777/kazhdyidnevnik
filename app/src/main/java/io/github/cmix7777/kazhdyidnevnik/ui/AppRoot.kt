package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab,
                        onClick = { current = tab },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        val screenModifier = Modifier.padding(padding)
        when (current) {
            Tab.Today -> TodayScreen(vm, screenModifier)
            Tab.Week -> WeekScreen(vm, screenModifier)
            Tab.Progress -> ProgressScreen(vm, screenModifier)
            Tab.Deadlines -> DeadlinesScreen(screenModifier)
        }
    }
}

package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
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
            Tab.Progress -> ProgressPlaceholder(screenModifier)
            Tab.Deadlines -> DeadlinesScreen(screenModifier)
        }
    }
}

@Composable
private fun ProgressPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Прогресс", style = MaterialTheme.typography.headlineSmall)
        InfoNote(
            "Следующий этап: план учёбы на каждый день, темы QA и Laravel с галочками, " +
                "серия дней с билетами ПДД и часы учёбы за неделю.",
        )
    }
}

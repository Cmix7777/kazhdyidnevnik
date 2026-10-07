package io.github.cmix7777.kazhdyidnevnik.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import io.github.cmix7777.kazhdyidnevnik.BuildConfig
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.formatDayTitle
import java.time.LocalDate

private enum class Tab(val title: String, @param:DrawableRes val icon: Int) {
    Today("Сегодня", R.drawable.ic_nav_today),
    Week("Неделя", R.drawable.ic_nav_week),
    Progress("Прогресс", R.drawable.ic_nav_progress),
    Deadlines("Дедлайны", R.drawable.ic_nav_deadlines),
}

@Composable
fun AppRoot() {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (current) {
                Tab.Today -> Placeholder(
                    title = formatDayTitle(LocalDate.now()),
                    text = "Здесь будут пары, смена и план учёбы на сегодня.",
                )
                Tab.Week -> Placeholder(
                    title = "Неделя",
                    text = "Здесь будет расписание на всю неделю.",
                )
                Tab.Progress -> Placeholder(
                    title = "Прогресс",
                    text = "Здесь будут темы QA и Laravel, серия дней с билетами ПДД и часы учёбы.",
                )
                Tab.Deadlines -> Placeholder(
                    title = "Дедлайны",
                    text = "Здесь будет обратный отсчёт до важных дат.",
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Версия ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Placeholder(title: String, text: String) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

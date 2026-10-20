package com.barati.razi.feature.announcements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.core.network.Result
import com.barati.razi.feature.publicinfo.AnnouncementCard
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.rememberLoader
import kotlinx.coroutines.launch

@Composable
fun AnnouncementsScreen(container: AppContainer, onBack: () -> Unit) {
    val repo = container.contentRepository
    val loader = rememberLoader { repo.staffAnnouncements() }
    val acked = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    RaziScaffold(title = "اخبار و بخشنامه‌ها", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { list ->
            // ثبت «دیده‌شد» برای همه موارد نمایش‌داده‌شده
            LaunchedEffect(list) { list.filter { it.read == false }.forEach { repo.markRead(it.id) } }
            if (list.isEmpty()) {
                MessageBox("اطلاعیه‌ای وجود ندارد", Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { a ->
                        val isAcked = acked[a.id] ?: (a.acknowledged == true)
                        AnnouncementCard(a, expandedInitially = a.requiresAck && !isAcked) {
                            if (a.requiresAck) {
                                if (isAcked) {
                                    Text("مطالعه را تأیید کرده‌اید", color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Button(onClick = {
                                        scope.launch {
                                            if (repo.acknowledge(a.id) is Result.Ok) acked[a.id] = true
                                        }
                                    }) { Text("مطالعه کردم") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

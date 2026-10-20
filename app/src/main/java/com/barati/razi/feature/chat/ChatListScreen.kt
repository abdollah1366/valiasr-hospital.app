package com.barati.razi.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.barati.razi.AppContainer
import com.barati.razi.core.chat.ChatLogic
import com.barati.razi.core.network.ConversationDto
import com.barati.razi.core.network.ConversationListDto
import com.barati.razi.core.network.Result
import com.barati.razi.feature.training.EmptyState
import com.barati.razi.feature.training.TrainingColors
import com.barati.razi.ui.components.Load
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.Loader
import com.barati.razi.ui.components.RaziScaffold
import kotlinx.coroutines.delay

private const val LIST_POLL_MILLIS = 15_000L

/** فهرست گفتگوها: کانال واحد، گروه‌ها و گفتگوهای دونفره؛ تا وقتی صفحه باز است هر ۱۵ ثانیه تازه می‌شود. */
@Composable
fun ChatListScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val repo = container.chatRepository
    var state by remember { mutableStateOf<Load<ConversationListDto>>(Load.Loading) }
    var version by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(version) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val r = repo.conversations()
                val current = state
                state = when (r) {
                    is Result.Ok -> Load.Data(r.value)
                    is Result.Err -> if (current is Load.Data) current else Load.Error(r.error.message)
                }
                delay(LIST_POLL_MILLIS)
            }
        }
    }
    val loader = Loader(state) {
        state = Load.Loading
        version++
    }
    RaziScaffold(
        title = "گفتگو",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("گفتگوی تازه") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(loader) { data ->
                if (data.items.isEmpty()) {
                    EmptyState(
                        Icons.Filled.Forum,
                        "هنوز گفتگویی ندارید",
                        "با «گفتگوی تازه» همکار خود را با نام یا کد پرسنلی پیدا کنید.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { SafetyNote() }
                        items(data.items, key = { it.id }) { c -> ConversationRow(c) { onOpen(c.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyNote() {
    Text(
        "این گفتگو برای هماهنگی روزمره است و جای تلفن یا پیجر در موارد فوری را نمی‌گیرد. اطلاعات هویتی بیمار را نفرستید.",
        Modifier
            .fillMaxWidth()
            .background(TrainingColors.amberBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = TrainingColors.amber,
    )
}

@Composable
private fun ConversationRow(c: ConversationDto, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConversationAvatar(c)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    c.lastMessage?.let {
                        Text(ChatLogic.listTime(it.at), style = MaterialTheme.typography.labelSmall, color = TrainingColors.gray)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        ChatLogic.preview(c),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (c.unread > 0) MaterialTheme.colorScheme.onSurface else TrainingColors.gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (c.muted) Icon(Icons.Filled.NotificationsOff, contentDescription = "بی‌صدا", Modifier.size(16.dp), tint = TrainingColors.gray)
                    if (c.unread > 0) UnreadBadge(c.unread, muted = c.muted)
                }
            }
        }
    }
}

@Composable
fun UnreadBadge(count: Int, muted: Boolean = false) {
    Box(
        Modifier
            .background(if (muted) TrainingColors.gray else MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(ChatLogic.badge(count), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** دایره رنگی با حرف اول نام، یا نماد گروه و کانال واحد. */
@Composable
fun ConversationAvatar(c: ConversationDto, size: Int = 46) {
    when (c.kind) {
        "unit" -> IconAvatar(Icons.Filled.Campaign, TrainingColors.amber, TrainingColors.amberBg, size)
        "group" -> IconAvatar(Icons.Filled.Groups, TrainingColors.green, TrainingColors.greenBg, size)
        else -> NameAvatar(c.title, size)
    }
}

@Composable
private fun IconAvatar(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, bg: Color, size: Int) {
    Box(Modifier.size(size.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

private val avatarTones = listOf(
    TrainingColors.blue to TrainingColors.blueBg,
    TrainingColors.green to TrainingColors.greenBg,
    TrainingColors.amber to TrainingColors.amberBg,
    TrainingColors.red to TrainingColors.redBg,
    Color(0xFF6A1B9A) to Color(0xFFF3E5F5),
)

@Composable
fun NameAvatar(name: String, size: Int = 46) {
    val (fg, bg) = avatarTones[Math.floorMod(name.hashCode(), avatarTones.size)]
    Box(Modifier.size(size.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1),
            color = fg,
            style = if (size >= 40) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

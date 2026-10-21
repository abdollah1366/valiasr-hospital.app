package com.barati.razi.feature.education

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.TicketAttachmentDto
import com.barati.razi.core.network.TicketDetailDto
import com.barati.razi.core.network.TicketDto
import com.barati.razi.core.network.TicketMessageDto
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.theme.ValiasrColors
import kotlinx.coroutines.launch

/** تیکت مراجع با مسئول آموزش به بیمار؛ متن، عکس، صوت و فیلم. */
@Composable
fun EducationTickets(container: AppContainer, padding: PaddingValues, signedIn: Boolean, onSignIn: () -> Unit) {
    if (!signedIn) {
        SignInPrompt(padding, R.string.edu_tickets_signin, onSignIn)
        return
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = openId != null || creating) {
        openId = null
        creating = false
        refresh++
    }
    val id = openId
    when {
        id != null -> TicketThread(container, id, padding)
        creating -> NewTicketForm(container, padding) { created ->
            creating = false
            openId = created
        }
        else -> TicketList(container, padding, refresh, onNew = { creating = true }, onOpen = { openId = it })
    }
}

@Composable
private fun TicketList(container: AppContainer, padding: PaddingValues, refresh: Int, onNew: () -> Unit, onOpen: (String) -> Unit) {
    val loader = rememberLoader(refresh) { container.educationRepository.tickets() }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Button(onClick = onNew, Modifier.fillMaxWidth().padding(16.dp).height(48.dp)) { Text(stringResource(R.string.edu_ticket_new)) }
        LoadContent(loader, Modifier) { items ->
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (items.isEmpty()) {
                    item { Text(stringResource(R.string.edu_ticket_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(items, key = { it.id }) { t -> TicketRow(t) { onOpen(t.id) } }
            }
        }
    }
}

@Composable
private fun TicketRow(t: TicketDto, onClick: () -> Unit) {
    val language = LocalAppLanguage.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.subject, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (t.unread) {
                    Text(stringResource(R.string.edu_new), style = MaterialTheme.typography.labelSmall, color = ValiasrColors.Brand)
                }
                if (t.status == "closed") {
                    Text(stringResource(R.string.edu_ticket_closed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            t.lastMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
            Text(
                runCatching { AppointmentLogic.dateTimeText(t.updatedAt, language) }.getOrDefault(t.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewTicketForm(container: AppContainer, padding: PaddingValues, onCreated: (String) -> Unit) {
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(subject, { subject = it.take(200) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.edu_ticket_subject)) })
        OutlinedTextField(body, { body = it.take(4000) }, Modifier.fillMaxWidth().height(180.dp), label = { Text(stringResource(R.string.edu_ticket_message)) })
        if (failed) Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                busy = true
                failed = false
                scope.launch {
                    when (val r = container.educationRepository.createTicket(subject, body)) {
                        is Result.Ok -> onCreated(r.value.id)
                        is Result.Err -> failed = true
                    }
                    busy = false
                }
            },
            enabled = !busy && subject.isNotBlank() && body.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(stringResource(R.string.edu_ticket_send)) }
    }
}

@Composable
private fun TicketThread(container: AppContainer, id: String, padding: PaddingValues) {
    val loader = rememberLoader(id) { container.educationRepository.ticket(id) }
    LoadContent(loader, Modifier.padding(padding)) { initial ->
        var current by remember(initial) { mutableStateOf(initial) }
        ThreadBody(container, current, onUpdated = { current = it })
    }
}

@Composable
private fun ThreadBody(container: AppContainer, ticket: TicketDetailDto, onUpdated: (TicketDetailDto) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(ticket.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var maxMb by remember { mutableIntStateOf(0) }

    fun handle(r: Result<TicketDetailDto>) {
        when (r) {
            is Result.Ok -> {
                onUpdated(r.value)
                error = null
            }
            is Result.Err -> error = R.string.error_generic
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val size = resolver.sizeOf(uri)
        when (val check = TicketAttachmentRules.check(resolver.getType(uri), size, System.currentTimeMillis())) {
            AttachmentCheck.Unsupported -> error = R.string.edu_attach_unsupported
            is AttachmentCheck.TooLarge -> {
                maxMb = check.maxMb
                error = R.string.edu_attach_toolarge
            }
            is AttachmentCheck.Ok -> {
                busy = true
                scope.launch {
                    handle(container.educationRepository.sendAttachment(ticket.id, check.filename, UriRequestBody(resolver, uri, check.contentType, size)))
                    busy = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(ticket.subject, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ticket.messages, key = { it.id }) { m -> MessageBubble(container, m) }
        }
        error?.let {
            Text(
                stringResource(it, maxMb),
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (ticket.status == "closed") {
            Text(stringResource(R.string.edu_ticket_closed_hint), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = { pick.launch(arrayOf("image/*", "audio/*", "video/*")) }, enabled = !busy) {
                Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.edu_ticket_attach))
            }
            OutlinedTextField(
                text,
                { text = it.take(4000) },
                Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.edu_ticket_reply_hint)) },
                maxLines = 4,
            )
            if (busy) {
                CircularProgressIndicator(Modifier.padding(12.dp).height(24.dp))
            } else {
                IconButton(
                    onClick = {
                        val body = text
                        busy = true
                        scope.launch {
                            val r = container.educationRepository.sendMessage(ticket.id, body)
                            if (r is Result.Ok) text = ""
                            handle(r)
                            busy = false
                        }
                    },
                    enabled = text.isNotBlank(),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.edu_ticket_send)) }
            }
        }
    }
}

@Composable
private fun MessageBubble(container: AppContainer, m: TicketMessageDto) {
    val language = LocalAppLanguage.current
    val mine = !m.fromStaff
    Card(
        Modifier.fillMaxWidth().padding(start = if (mine) 40.dp else 0.dp, end = if (mine) 0.dp else 40.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (mine) ValiasrColors.BrandSoft else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (mine) stringResource(R.string.edu_you) else m.staffName ?: stringResource(R.string.edu_staff)) + " · " +
                    runCatching { AppointmentLogic.dateTimeText(m.createdAt, language) }.getOrDefault(m.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            m.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            m.attachment?.let { Attachment(container, it) }
        }
    }
}

@Composable
private fun Attachment(container: AppContainer, a: TicketAttachmentDto) {
    val url = container.trainingRepository.absoluteUrl(a.url)
    when {
        a.contentType.startsWith("image/") -> AsyncImage(
            model = url,
            contentDescription = a.originalName,
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
        a.contentType.startsWith("audio/") -> EducationPlayer(url, audio = true)
        a.contentType.startsWith("video/") -> EducationPlayer(url, audio = false)
        else -> Text(a.originalName, style = MaterialTheme.typography.bodySmall)
    }
}

package com.barati.razi.feature.education

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.appointments.AppointmentLogic
import com.barati.razi.core.network.EducationDto
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.theme.ValiasrColors

/** آموزش‌هایی که مسئول آموزش برای همین مراجع (بیمار یا همراه) فرستاده است. */
@Composable
fun MyEducationList(container: AppContainer, padding: PaddingValues, signedIn: Boolean, onSignIn: () -> Unit) {
    if (!signedIn) {
        SignInPrompt(padding, R.string.edu_signin_needed, onSignIn)
        return
    }
    val language = LocalAppLanguage.current
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = openId != null) {
        openId = null
        refresh++
    }
    val id = openId
    if (id != null) {
        EducationDetail(container, id, padding, signedIn, onSignIn)
        return
    }
    val loader = rememberLoader(language, refresh) { container.educationRepository.mine(language) }
    LoadContent(loader, Modifier.padding(padding)) { items ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.isEmpty()) {
                item { Text(stringResource(R.string.edu_mine_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(items, key = { it.id }) { e -> AssignedCard(e) { openId = e.id } }
        }
    }
}

@Composable
private fun AssignedCard(e: EducationDto, onClick: () -> Unit) {
    val language = LocalAppLanguage.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(ValiasrColors.BrandSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(kindIcon(e.kind), contentDescription = stringResource(kindLabel(e.kind)), tint = ValiasrColors.Brand) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(e.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!e.seen) {
                        Text(
                            stringResource(R.string.edu_new),
                            style = MaterialTheme.typography.labelSmall,
                            color = ValiasrColors.Brand,
                            modifier = Modifier.background(ValiasrColors.BrandSoft, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                e.assignmentNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                e.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                e.assignedAt?.let {
                    Text(
                        stringResource(R.string.edu_sent_at, runCatching { AppointmentLogic.dateTimeText(it, language) }.getOrDefault(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun SignInPrompt(padding: PaddingValues, message: Int, onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, Modifier.size(64.dp), tint = ValiasrColors.Brand)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(message), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSignIn, Modifier.height(48.dp)) { Text(stringResource(R.string.signin_action)) }
    }
}

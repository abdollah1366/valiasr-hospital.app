package com.barati.razi.feature.patient

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.HospitalInfo
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.PatientDto
import com.barati.razi.feature.publicinfo.FeedbackBody
import com.barati.razi.feature.publicinfo.UnitsList
import com.barati.razi.ui.components.MenuTile
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.i18n.LanguageAction
import com.barati.razi.ui.theme.brandColor

private enum class PatientTab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    UNITS(R.string.nav_units, Icons.Outlined.LocalHospital, Icons.Filled.LocalHospital),
    TRACK(R.string.nav_track, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    PROFILE(R.string.nav_profile, Icons.Outlined.Person, Icons.Filled.Person),
}

/** شماره‌ها و نشانی‌های لاتین داخل متن راست‌به‌چپ جابه‌جا نشوند. */
private fun ltr(text: String) = "⁦$text⁩"

/** باز کردن پیوند بیرونی (مرورگر، شماره‌گیر، نقشه)؛ اگر برنامه‌ای برایش نباشد، بی‌صدا رد می‌شود. */
private fun UriHandler.tryOpen(uri: String) {
    runCatching { openUri(uri) }
}

/** صفحه اول مراجعان با نوار پایین: خانه، بخش‌ها، پیگیری، پروفایل. */
@Composable
fun PatientHomeScreen(
    container: AppContainer,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    patient: PatientDto?,
    staffLoggedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onFaq: () -> Unit,
    onNews: () -> Unit,
    onContact: () -> Unit,
    onInternational: () -> Unit,
    onAbout: () -> Unit,
    onTrackIncident: () -> Unit,
    onStaff: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = PatientTab.entries[tab]
    BackHandler(enabled = current != PatientTab.HOME) { tab = PatientTab.HOME.ordinal }

    RaziScaffold(
        title = if (current == PatientTab.HOME) stringResource(R.string.hospital_full_name) else stringResource(current.label),
        actions = {
            if (patient == null && current == PatientTab.HOME) {
                TextButton(onClick = onSignIn) { Text(stringResource(R.string.signin_action)) }
            }
            LanguageAction(language, onLanguage)
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                PatientTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == current,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(if (t == current) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        when (current) {
            PatientTab.HOME -> HomeTab(
                padding,
                language,
                patient,
                onFaq = onFaq,
                onNews = onNews,
                onContact = onContact,
                onFeedback = { tab = PatientTab.TRACK.ordinal },
                onInternational = onInternational,
                onAbout = onAbout,
            )
            PatientTab.UNITS -> UnitsList(container, padding)
            PatientTab.TRACK -> FeedbackBody(container, padding)
            PatientTab.PROFILE -> ProfileTab(
                padding,
                language,
                onLanguage,
                patient,
                staffLoggedIn,
                onSignIn,
                onSignOut,
                onTrackIncident,
                onStaff,
            )
        }
    }
}

@Composable
private fun HomeTab(
    padding: PaddingValues,
    language: AppLanguage,
    patient: PatientDto?,
    onFaq: () -> Unit,
    onNews: () -> Unit,
    onContact: () -> Unit,
    onFeedback: () -> Unit,
    onInternational: () -> Unit,
    onAbout: () -> Unit,
) {
    val uri = LocalUriHandler.current
    // مراجع غیرفارسی‌زبان به احتمال زیاد بیمار بین‌الملل است؛ کارت مربوط بالاتر می‌آید.
    val internationalFirst = language != AppLanguage.FA
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val name = patient?.fullName?.takeIf { it.isNotBlank() }
        Text(
            if (name != null) stringResource(R.string.home_greeting_name, name) else stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        HeroCard(onBook = { uri.tryOpen(HospitalInfo.BOOKING_URL) })
        if (internationalFirst) InternationalCard(onInternational)
        TileGrid(
            listOf(
                Tile(R.string.tile_visiting, R.string.tile_visiting_sub, Icons.Outlined.AccessTime, onFaq),
                Tile(R.string.tile_contact, R.string.tile_contact_sub, Icons.Outlined.Place, onContact),
                Tile(R.string.tile_news, R.string.tile_news_sub, Icons.Outlined.Campaign, onNews),
                Tile(R.string.tile_feedback_short, R.string.tile_feedback_sub, Icons.Outlined.Feedback, onFeedback),
            ),
        )
        if (!internationalFirst) InternationalCard(onInternational)
        WideCard(Icons.Outlined.Info, stringResource(R.string.about_title), stringResource(R.string.about_sub), onAbout)
    }
}

@Composable
private fun HeroCard(onBook: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // نوار باریک قرمز برند؛ فقط برای هویت، نه برای دکمه.
            Box(Modifier.size(width = 32.dp, height = 4.dp).background(brandColor, RoundedCornerShape(2.dp)))
            Text(
                stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Button(
                onClick = onBook,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) { Text(stringResource(R.string.home_book), fontWeight = FontWeight.Bold) }
        }
    }
}

private class Tile(val title: Int, val subtitle: Int, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun TileGrid(tiles: List<Tile>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t -> SmallTile(t, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SmallTile(t: Tile, modifier: Modifier) {
    Card(
        modifier.heightIn(min = 120.dp).clickable(onClick = t.onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(t.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Text(stringResource(t.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(t.subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InternationalCard(onClick: () -> Unit) {
    WideCard(
        Icons.Outlined.Public,
        stringResource(R.string.international_title),
        stringResource(R.string.international_sub),
        onClick,
        container = MaterialTheme.colorScheme.secondaryContainer,
        tint = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun WideCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surface,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, Modifier.size(32.dp), tint = tint)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProfileTab(
    padding: PaddingValues,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    patient: PatientDto?,
    staffLoggedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onTrackIncident: () -> Unit,
    onStaff: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (patient != null) {
                    patient.fullName?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    InfoRow(Icons.Outlined.Phone, stringResource(R.string.profile_mobile), ltr(patient.mobile))
                } else {
                    Text(stringResource(R.string.profile_signed_out))
                    Button(onClick = onSignIn, Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.signin_action)) }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.language) + ": " + language.nativeName,
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    LanguageAction(language, onLanguage)
                }
            }
        }
        MenuTile(
            stringResource(if (staffLoggedIn) R.string.tile_staff_logged_in else R.string.tile_staff),
            stringResource(R.string.tile_staff_sub),
            Icons.Outlined.Badge,
            onStaff,
        )
        MenuTile(stringResource(R.string.tile_track), stringResource(R.string.tile_track_sub), Icons.Outlined.Search, onTrackIncident)
        if (patient != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.profile_sign_out)) }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun UnconfirmedNote() {
    Text(
        stringResource(R.string.unconfirmed_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** مسیر و تماس: نشانی، نقشه، تلفن و ایمیل. */
@Composable
fun ContactScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    RaziScaffold(title = stringResource(R.string.contact_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.contact_address), Modifier.padding(horizontal = 12.dp))
                    }
                    Button(onClick = { uri.tryOpen(HospitalInfo.MAP_URI) }, Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Outlined.Map, contentDescription = null)
                        Text(stringResource(R.string.contact_open_map), Modifier.padding(start = 8.dp))
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow(Icons.Outlined.Phone, stringResource(R.string.contact_phone), ltr(HospitalInfo.PHONE_DISPLAY)) {
                        uri.tryOpen("tel:${HospitalInfo.PHONE}")
                    }
                    HorizontalDivider()
                    InfoRow(Icons.Outlined.Email, stringResource(R.string.contact_email), ltr(HospitalInfo.EMAIL)) {
                        uri.tryOpen("mailto:${HospitalInfo.EMAIL}")
                    }
                }
            }
        }
    }
}

/** راهنمای بیماران بین‌الملل. */
@Composable
fun InternationalScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    RaziScaffold(title = stringResource(R.string.intl_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.intl_intro), style = MaterialTheme.typography.bodyLarge)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        R.string.intl_service_admission,
                        R.string.intl_service_interpreter,
                        R.string.intl_service_stay,
                        R.string.intl_service_cost,
                    ).forEach { res ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(res), Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
            Button(onClick = { uri.tryOpen("tel:${HospitalInfo.PHONE}") }, Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Outlined.Phone, contentDescription = null)
                Text(stringResource(R.string.intl_contact), Modifier.padding(start = 8.dp))
            }
            UnconfirmedNote()
        }
    }
}

/** معرفی بیمارستان: آمار اصلی، خدمات و بخش‌های تخصصی (متن رسمی بیمارستان). */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    RaziScaffold(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge)
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.about_er_n) + "  " + stringResource(R.string.about_er),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(stringResource(R.string.about_er_visits), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.about_icu_n), stringResource(R.string.about_icu), Modifier.weight(1f))
                Stat(stringResource(R.string.about_ccu_n), stringResource(R.string.about_ccu), Modifier.weight(1f))
                Stat(stringResource(R.string.about_or_n), stringResource(R.string.about_or), Modifier.weight(1f))
            }
            AboutSection(R.string.about_depts_title, R.string.about_depts)
            AboutSection(R.string.about_diag_title, R.string.about_diag)
            AboutSection(R.string.about_special_title, R.string.about_special)
            Text(
                stringResource(R.string.about_approach),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(
        modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AboutSection(title: Int, body: Int) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(body))
        }
    }
}

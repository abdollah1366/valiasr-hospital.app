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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import com.barati.razi.core.util.toPersianDigits
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.HospitalInfo
import com.barati.razi.core.i18n.AppLanguage
import com.barati.razi.core.network.PatientDto
import com.barati.razi.feature.publicinfo.FeedbackBody
import com.barati.razi.feature.publicinfo.UnitsList
import com.barati.razi.ui.components.MenuTile
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.TileGrid
import com.barati.razi.ui.components.TileItem
import com.barati.razi.ui.components.WideCard
import com.barati.razi.ui.components.DeveloperCredit
import com.barati.razi.ui.i18n.LanguageAction
import com.barati.razi.ui.theme.brandColor

/** شماره‌ها و نشانی‌های لاتین داخل متن راست‌به‌چپ جابه‌جا نشوند. */
private fun ltr(text: String) = "⁦$text⁩"

/** باز کردن پیوند بیرونی (مرورگر، شماره‌گیر، نقشه)؛ اگر برنامه‌ای برایش نباشد، بی‌صدا رد می‌شود. */
internal fun UriHandler.tryOpen(uri: String) {
    runCatching { openUri(uri) }
}

@Composable
internal fun ProfileTab(
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

/** مسیر و تماس: نشانی، نقشه، تلفن، ایمیل و شبکه‌های اجتماعی. */
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
                    HorizontalDivider()
                    InfoRow(Icons.Outlined.PhotoCamera, stringResource(R.string.contact_instagram), ltr("@" + HospitalInfo.SOCIAL_ID)) {
                        uri.tryOpen(HospitalInfo.INSTAGRAM_URL)
                    }
                    HorizontalDivider()
                    InfoRow(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.contact_telegram), ltr("@" + HospitalInfo.SOCIAL_ID)) {
                        uri.tryOpen(HospitalInfo.TELEGRAM_URL)
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
            DeveloperCredit()
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

/** راهنمای پذیرش و بستری: مراحل (از نمودار رسمی بیمارستان) و نکات مهم. */
@Composable
fun AdmissionGuideScreen(onBack: () -> Unit) {
    val steps = listOf(
        R.string.step1 to null,
        R.string.step2 to R.string.step2_detail,
        R.string.step3 to R.string.step3_detail,
        R.string.step4 to R.string.step4_detail,
        R.string.step5 to null,
    )
    val tips = listOf(R.string.tip1, R.string.tip2, R.string.tip3, R.string.tip4, R.string.tip5, R.string.tip6, R.string.tip7)
    RaziScaffold(title = stringResource(R.string.admission_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.admission_steps_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column {
                steps.forEachIndexed { i, (title, detail) -> StepRow(i + 1, title, detail, last = i == steps.lastIndex) }
            }
            Text(stringResource(R.string.admission_tips_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    tips.forEach { res ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(res), Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

/** یک گام از خط زمانی: دایره شماره‌دار، خط اتصال به گام بعد، عنوان و توضیح. */
@Composable
private fun StepRow(number: Int, title: Int, detail: Int?, last: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(localDigits(number), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.primaryContainer))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, bottom = if (last) 0.dp else 16.dp, top = 4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            detail?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun localDigits(n: Int): String =
    if (LocalAppLanguage.current == AppLanguage.FA) toPersianDigits(n.toString()) else n.toString()

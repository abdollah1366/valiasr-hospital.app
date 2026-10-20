package com.barati.razi.feature.publicinfo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.core.network.AnnouncementDto
import com.barati.razi.core.network.FeedbackRequest
import com.barati.razi.core.network.FeedbackStatusDto
import com.barati.razi.core.network.Labels
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.UnitDto
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.toLatinDigits
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.MenuTile
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.PrimaryButton
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.SelectField
import com.barati.razi.ui.components.rememberLoader
import kotlinx.coroutines.launch

@Composable
fun PublicHomeScreen(
    isLoggedIn: Boolean,
    onUnits: () -> Unit,
    onFaq: () -> Unit,
    onNews: () -> Unit,
    onFeedback: () -> Unit,
    onTrack: () -> Unit,
    onStaff: () -> Unit,
) {
    RaziScaffold(title = "بیمارستان رازی تبریز") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("به اپلیکیشن بیمارستان رازی خوش آمدید", style = MaterialTheme.typography.titleLarge)
            MenuTile("راهنمای بخش‌ها و درمانگاه‌ها", "محل، تلفن و ساعات کار", Icons.Filled.LocalHospital, onUnits)
            MenuTile("پرسش‌های متداول", "مدارک لازم، ساعات ملاقات و ...", Icons.Filled.QuestionAnswer, onFaq)
            MenuTile("اطلاعیه‌ها", "اخبار و تغییرات خدمات بیمارستان", Icons.Filled.Campaign, onNews)
            MenuTile("پیشنهاد، انتقاد و شکایت", "ثبت و پیگیری با کد", Icons.Filled.Feedback, onFeedback)
            MenuTile("پیگیری گزارش ناشناس", "با کد و رمز پیگیری", Icons.Filled.Search, onTrack)
            Spacer(Modifier.height(8.dp))
            MenuTile(
                if (isLoggedIn) "ورود به بخش کارکنان" else "ورود کارکنان",
                "ثبت گزارش خطا، بخشنامه‌ها و ...",
                Icons.Filled.Badge,
                onStaff,
            )
        }
    }
}

@Composable
fun UnitsScreen(container: AppContainer, onBack: () -> Unit) {
    val loader = rememberLoader { container.contentRepository.publicUnits() }
    RaziScaffold(title = "راهنمای بخش‌ها", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { units ->
            if (units.isEmpty()) {
                MessageBox("هنوز اطلاعاتی ثبت نشده است", Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(units, key = { it.id }) { u -> UnitCard(u) }
                }
            }
        }
    }
}

@Composable
private fun UnitCard(u: UnitDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(u.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            u.location?.let { Text("محل: $it") }
            u.phone?.let { Text("تلفن: $it") }
            u.hours?.let { Text("ساعات کار: $it") }
            u.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun FaqScreen(container: AppContainer, onBack: () -> Unit) {
    val loader = rememberLoader { container.contentRepository.faqs() }
    var expanded by remember { mutableStateOf<String?>(null) }
    RaziScaffold(title = "پرسش‌های متداول", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { faqs ->
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(faqs, key = { it.id }) { f ->
                    Card(Modifier.fillMaxWidth().clickable { expanded = if (expanded == f.id) null else f.id }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(f.question, fontWeight = FontWeight.Bold)
                            if (expanded == f.id) {
                                Spacer(Modifier.height(8.dp))
                                Text(f.answer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnnouncementCard(a: AnnouncementDto, expandedInitially: Boolean = false, footer: @Composable () -> Unit = {}) {
    var expanded by remember { mutableStateOf(expandedInitially) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (Labels.announcementCategories[a.category] ?: a.category) + " · " + Jalali.formatIso(a.publishedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (expanded) Text(a.body)
            footer()
        }
    }
}

@Composable
fun PublicNewsScreen(container: AppContainer, onBack: () -> Unit) {
    val loader = rememberLoader { container.contentRepository.publicAnnouncements() }
    RaziScaffold(title = "اطلاعیه‌ها", onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { list ->
            if (list.isEmpty()) {
                MessageBox("اطلاعیه‌ای وجود ندارد", Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { items(list, key = { it.id }) { AnnouncementCard(it) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(container: AppContainer, onBack: () -> Unit) {
    val units = rememberLoader { container.contentRepository.publicUnits() }
    var kind by remember { mutableStateOf("suggestion") }
    var unit by remember { mutableStateOf<UnitDto?>(null) }
    var body by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<String?>(null) }
    var trackCode by remember { mutableStateOf("") }
    var tracked by remember { mutableStateOf<FeedbackStatusDto?>(null) }
    var trackError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    RaziScaffold(title = "پیشنهاد، انتقاد و شکایت", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (created != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ثبت شد. کد پیگیری شما:", fontWeight = FontWeight.Bold)
                        Text(created!!, style = MaterialTheme.typography.headlineSmall)
                        Text("این کد را یادداشت کنید تا بعداً پاسخ را ببینید.")
                    }
                }
            } else {
                Text("نوع", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Labels.feedbackKinds.forEach { (k, label) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) })
                    }
                }
                val unitList = (units.state as? com.barati.razi.ui.components.Load.Data<List<UnitDto>>)?.value.orEmpty()
                SelectField("بخش مربوط (اختیاری)", unit, unitList, { it.name }, { unit = it }, allowNone = true)
                OutlinedTextField(body, { body = it; error = null }, Modifier.fillMaxWidth(), label = { Text("متن *") }, minLines = 4)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("نام (اختیاری)") }, singleLine = true)
                OutlinedTextField(
                    mobile,
                    { mobile = toLatinDigits(it).filter(Char::isDigit).take(11); error = null },
                    Modifier.fillMaxWidth(),
                    label = { Text("شماره موبایل (اختیاری)") },
                    singleLine = true,
                )
                ErrorText(error)
                PrimaryButton("ارسال", loading = loading, onClick = {
                    error = when {
                        body.trim().length < 5 -> "متن را کامل‌تر بنویسید"
                        mobile.isNotEmpty() && !Regex("^09\\d{9}$").matches(mobile) -> "شماره موبایل باید ۱۱ رقم و با ۰۹ شروع شود"
                        else -> null
                    }
                    if (error == null) {
                        loading = true
                        scope.launch {
                            val req = FeedbackRequest(
                                kind = kind,
                                body = body.trim(),
                                name = name.trim().ifEmpty { null },
                                mobile = mobile.ifEmpty { null },
                                unitId = unit?.id,
                            )
                            when (val r = container.contentRepository.submitFeedback(req)) {
                                is Result.Ok -> created = r.value.trackingCode
                                is Result.Err -> error = r.error.message
                            }
                            loading = false
                        }
                    }
                })
            }

            Spacer(Modifier.height(16.dp))
            Text("پیگیری با کد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(trackCode, { trackCode = it; trackError = null }, Modifier.fillMaxWidth(), label = { Text("کد پیگیری (مثلاً FB-…)") }, singleLine = true)
            ErrorText(trackError)
            PrimaryButton("پیگیری", onClick = {
                scope.launch {
                    when (val r = container.contentRepository.feedbackStatus(toLatinDigits(trackCode))) {
                        is Result.Ok -> tracked = r.value
                        is Result.Err -> { tracked = null; trackError = r.error.message }
                    }
                }
            })
            tracked?.let { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("وضعیت: " + (Labels.feedbackStatus[t.status] ?: t.status), fontWeight = FontWeight.Bold)
                        Text("تاریخ ثبت: " + Jalali.formatIso(t.createdAt))
                        t.response?.let { Text("پاسخ: $it") }
                    }
                }
            }
        }
    }
}

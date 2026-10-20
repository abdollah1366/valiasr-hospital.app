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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barati.razi.AppContainer
import com.barati.razi.R
import com.barati.razi.core.network.AnnouncementDto
import com.barati.razi.core.network.FeedbackRequest
import com.barati.razi.core.network.FeedbackStatusDto
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.UnitDto
import com.barati.razi.core.util.toLatinDigits
import com.barati.razi.ui.components.ErrorText
import com.barati.razi.ui.components.LoadContent
import com.barati.razi.ui.components.MessageBox
import com.barati.razi.ui.components.PrimaryButton
import com.barati.razi.ui.components.RaziScaffold
import com.barati.razi.ui.components.SelectField
import com.barati.razi.ui.components.rememberErrorText
import com.barati.razi.ui.components.rememberLoader
import com.barati.razi.ui.i18n.LocalAppLanguage
import com.barati.razi.ui.i18n.PatientLabels
import kotlinx.coroutines.launch

/** فهرست بخش‌ها؛ زبانه «بخش‌ها» در صفحه اول مراجعان. */
@Composable
fun UnitsList(container: AppContainer, padding: PaddingValues) {
    val language = LocalAppLanguage.current
    val loader = rememberLoader(language) { container.contentRepository.publicUnits(language) }
    LoadContent(loader, Modifier.padding(padding)) { units ->
        if (units.isEmpty()) {
            MessageBox(stringResource(R.string.units_empty), Modifier.padding(padding))
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

@Composable
private fun UnitCard(u: UnitDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(u.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            u.location?.let { Text(stringResource(R.string.unit_location, it)) }
            u.phone?.let { Text(stringResource(R.string.unit_phone, it)) }
            u.hours?.let { Text(stringResource(R.string.unit_hours, it)) }
            u.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun FaqScreen(container: AppContainer, onBack: () -> Unit) {
    val language = LocalAppLanguage.current
    val loader = rememberLoader(language) { container.contentRepository.faqs(language) }
    var expanded by remember { mutableStateOf<String?>(null) }
    RaziScaffold(title = stringResource(R.string.faq_title), onBack = onBack) { padding ->
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
                PatientLabels.announcementCategory(a.category) + " · " + PatientLabels.date(a.publishedAt),
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
    val language = LocalAppLanguage.current
    val loader = rememberLoader(language) { container.contentRepository.publicAnnouncements(language) }
    RaziScaffold(title = stringResource(R.string.news_title), onBack = onBack) { padding ->
        LoadContent(loader, Modifier.padding(padding)) { list ->
            if (list.isEmpty()) {
                MessageBox(stringResource(R.string.news_empty), Modifier.padding(padding))
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

/** ثبت و پیگیری پیشنهاد و شکایت؛ زبانه «پیگیری» در صفحه اول مراجعان. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackBody(container: AppContainer, padding: PaddingValues) {
    val language = LocalAppLanguage.current
    val units = rememberLoader(language) { container.contentRepository.publicUnits(language) }
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
    val errorText = rememberErrorText()

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (created != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.feedback_created), fontWeight = FontWeight.Bold)
                    Text(created!!, style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.feedback_created_note))
                }
            }
        } else {
            Text(stringResource(R.string.feedback_kind), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PatientLabels.feedbackKinds.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(PatientLabels.feedbackKind(k)) })
                }
            }
            val unitList = (units.state as? com.barati.razi.ui.components.Load.Data<List<UnitDto>>)?.value.orEmpty()
            SelectField(stringResource(R.string.feedback_unit), unit, unitList, { it.name }, { unit = it }, allowNone = true)
            OutlinedTextField(body, { body = it; error = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.feedback_body)) }, minLines = 4)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.feedback_name)) }, singleLine = true)
            OutlinedTextField(
                mobile,
                { mobile = toLatinDigits(it).filter(Char::isDigit).take(11); error = null },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.feedback_mobile)) },
                singleLine = true,
            )
            ErrorText(error)
            val errorBody = stringResource(R.string.feedback_error_body)
            val errorMobile = stringResource(R.string.feedback_error_mobile)
            PrimaryButton(stringResource(R.string.feedback_send), loading = loading, onClick = {
                error = when {
                    body.trim().length < 5 -> errorBody
                    mobile.isNotEmpty() && !Regex("^09\\d{9}$").matches(mobile) -> errorMobile
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
                            is Result.Err -> error = errorText(r.error)
                        }
                        loading = false
                    }
                }
            })
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.feedback_track_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(trackCode, { trackCode = it; trackError = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.feedback_track_code)) }, singleLine = true)
        ErrorText(trackError)
        PrimaryButton(stringResource(R.string.feedback_track_button), onClick = {
            scope.launch {
                when (val r = container.contentRepository.feedbackStatus(toLatinDigits(trackCode))) {
                    is Result.Ok -> tracked = r.value
                    is Result.Err -> { tracked = null; trackError = errorText(r.error) }
                }
            }
        })
        tracked?.let { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.feedback_status, PatientLabels.feedbackStatus(t.status)), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.feedback_created_at, PatientLabels.date(t.createdAt)))
                    t.response?.let { Text(stringResource(R.string.feedback_response, it)) }
                }
            }
        }
    }
}

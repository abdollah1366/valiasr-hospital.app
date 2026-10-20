package com.barati.razi.feature.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barati.razi.core.data.IncidentRepository
import com.barati.razi.core.form.Answer
import com.barati.razi.core.form.FormLogic
import com.barati.razi.core.form.FormSchema
import com.barati.razi.core.form.FormSection
import com.barati.razi.core.network.IncidentCreateRequest
import com.barati.razi.core.network.Result
import com.barati.razi.core.network.UnitDto
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SubmitResult {
    data class Sent(val trackingCode: String, val secret: String?) : SubmitResult
    /** بدون اینترنت: در صف ماند و بعداً خودکار ارسال می‌شود. */
    data class Queued(val secret: String?) : SubmitResult
    data class Failed(val message: String) : SubmitResult
}

data class IncidentFormState(
    val loading: Boolean = true,
    val schema: FormSchema? = null,
    val units: List<UnitDto> = emptyList(),
    val unitsError: String? = null,
    val kind: String? = null,
    val unit: UnitDto? = null,
    val occurredOn: LocalDate? = null,
    val shift: String = "unknown",
    val anonymous: Boolean = false,
    val answers: Map<String, Answer> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    /** ۰ = اطلاعات پایه؛ ۱..n = بخش‌های قابل‌مشاهده فرم؛ n+1 = مرور و ارسال */
    val page: Int = 0,
    val submitting: Boolean = false,
    val result: SubmitResult? = null,
) {
    val sections: List<FormSection>
        get() = if (schema == null || kind == null) emptyList() else FormLogic.visibleSections(schema, kind, answers)
    val reviewPage: Int get() = sections.size + 1
}

class IncidentFormViewModel(
    private val repo: IncidentRepository,
    private val onQueued: () -> Unit,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val _state = MutableStateFlow(IncidentFormState())
    val state: StateFlow<IncidentFormState> = _state.asStateFlow()

    /** شناسه یکتای همین گزارش؛ ارسال‌های تکراری (پس از قطعی شبکه) در سرور یکی شمرده می‌شوند. */
    private val clientUuid = UUID.randomUUID().toString()
    private val secret = IncidentRepository.newSecret()

    init {
        viewModelScope.launch {
            val schema = repo.loadForm()
            val units = repo.units()
            _state.update {
                it.copy(
                    loading = false,
                    schema = schema,
                    units = (units as? Result.Ok)?.value.orEmpty(),
                    unitsError = (units as? Result.Err)?.error?.message,
                )
            }
        }
    }

    fun reloadUnits() = viewModelScope.launch {
        val units = repo.units()
        _state.update {
            it.copy(units = (units as? Result.Ok)?.value ?: it.units, unitsError = (units as? Result.Err)?.error?.message)
        }
    }

    fun setKind(kind: String) = _state.update { it.copy(kind = kind, errors = it.errors - "kind") }
    fun setUnit(unit: UnitDto?) = _state.update { it.copy(unit = unit, errors = it.errors - "unit") }
    fun setDate(date: LocalDate?) = _state.update { it.copy(occurredOn = date, errors = it.errors - "date") }
    fun setShift(shift: String) = _state.update { it.copy(shift = shift) }
    fun setAnonymous(value: Boolean) = _state.update { it.copy(anonymous = value) }

    fun setAnswer(fieldId: String, answer: Answer?) = _state.update {
        val next = if (answer == null) it.answers - fieldId else it.answers + (fieldId to answer)
        it.copy(answers = next, errors = it.errors - fieldId)
    }

    fun back() = _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0), errors = emptyMap()) }

    fun next() = _state.update { s ->
        val errors = when (s.page) {
            0 -> buildMap {
                if (s.kind == null) put("kind", "نوع رویداد را انتخاب کنید")
                if (s.unit == null) put("unit", "بخش محل وقوع را انتخاب کنید")
                if (s.occurredOn == null) put("date", "تاریخ وقوع را انتخاب کنید")
                else if (s.occurredOn.isAfter(today())) put("date", "تاریخ نمی‌تواند بعد از امروز باشد")
            }
            in 1..s.sections.size -> FormLogic.validateSection(s.sections[s.page - 1], s.answers)
            else -> emptyMap()
        }
        if (errors.isNotEmpty()) s.copy(errors = errors) else s.copy(page = (s.page + 1).coerceAtMost(s.reviewPage), errors = emptyMap())
    }

    fun goTo(page: Int) = _state.update { it.copy(page = page.coerceIn(0, it.reviewPage), errors = emptyMap()) }

    fun submit() {
        val s = _state.value
        val schema = s.schema ?: return
        val kind = s.kind ?: return
        val errors = FormLogic.validate(schema, kind, s.answers)
        if (errors.isNotEmpty() || s.unit == null || s.occurredOn == null) {
            // به اولین بخشی که خطا دارد برمی‌گردیم
            val page = s.sections.indexOfFirst { sec -> sec.fields.any { it.id in errors } } + 1
            _state.update { it.copy(errors = errors, page = if (page > 0) page else 0) }
            return
        }
        val anonymousSecret = if (s.anonymous) secret else null
        val request = IncidentCreateRequest(
            clientUuid = clientUuid,
            kind = kind,
            unitId = s.unit.id,
            occurredOn = s.occurredOn.toString(),
            shift = s.shift,
            isAnonymous = s.anonymous,
            followUpSecret = anonymousSecret,
            formVersion = schema.version,
            answers = FormLogic.toJson(schema, kind, s.answers),
        )
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            repo.enqueue(request)
            val flush = repo.flush()
            val code = flush.sent[clientUuid]
            val stillPending = repo.pending.first().firstOrNull { it.request.clientUuid == clientUuid }
            val result = when {
                code != null -> SubmitResult.Sent(code, anonymousSecret)
                stillPending?.failedPermanently == true -> {
                    repo.discard(clientUuid)
                    SubmitResult.Failed(stillPending.lastError ?: "ارسال ناموفق بود")
                }
                else -> {
                    onQueued()
                    SubmitResult.Queued(anonymousSecret)
                }
            }
            _state.update { it.copy(submitting = false, result = result) }
        }
    }

    fun dismissFailure() = _state.update { it.copy(result = null) }
}

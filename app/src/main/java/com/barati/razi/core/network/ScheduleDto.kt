package com.barati.razi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SchedulePersonDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("personnel_code") val personnelCode: String = "",
    @SerialName("job_title") val jobTitle: String? = null,
)

@Serializable
data class MyShiftDto(
    val day: String,
    val jalali: String,
    val code: Int,
    val label: String,
    val title: String,
    /** work | off | leave */
    val kind: String,
    @SerialName("unit_id") val unitId: String,
    @SerialName("unit_name") val unitName: String,
    @SerialName("schedule_id") val scheduleId: String,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("work_hours") val workHours: Double = 0.0,
)

@Serializable
data class MyScheduleDto(
    val start: String,
    val end: String,
    val items: List<MyShiftDto> = emptyList(),
    @SerialName("next_shift") val nextShift: MyShiftDto? = null,
)

@Serializable
data class ScheduleSummaryDto(
    val id: String,
    @SerialName("unit_id") val unitId: String,
    @SerialName("unit_name") val unitName: String,
    val jyear: Int,
    val jmonth: Int,
    val title: String,
    /** draft | submitted | published */
    val status: String,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("return_note") val returnNote: String? = null,
)

@Serializable
data class UnitScheduleRowDto(
    @SerialName("unit_id") val unitId: String,
    @SerialName("unit_name") val unitName: String,
    @SerialName("unit_type") val unitType: String = "",
    val schedule: ScheduleSummaryDto? = null,
    @SerialName("can_create") val canCreate: Boolean = false,
)

@Serializable
data class ScheduleDayDto(
    val date: String,
    val jalali: String,
    val day: Int,
    val weekday: Int,
    @SerialName("weekday_name") val weekdayName: String,
    @SerialName("is_holiday") val isHoliday: Boolean = false,
    @SerialName("holiday_title") val holidayTitle: String? = null,
)

@Serializable
data class ScheduleCellDto(
    val day: String,
    val code: Int? = null,
    val label: String = "",
    val kind: String = "work",
    @SerialName("span_days") val spanDays: Int = 0,
)

@Serializable
data class ScheduleMemberDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("personnel_code") val personnelCode: String = "",
    @SerialName("job_title") val jobTitle: String? = null,
    @SerialName("is_guest") val isGuest: Boolean = false,
    val cells: List<ScheduleCellDto> = emptyList(),
    @SerialName("work_hours") val workHours: Double = 0.0,
    @SerialName("oncall_hours") val oncallHours: Double = 0.0,
    @SerialName("overtime_hours") val overtimeHours: Double? = null,
)

@Serializable
data class ScheduleWarningDto(
    val code: String,
    val message: String,
    @SerialName("user_id") val userId: String? = null,
    val day: String? = null,
    val period: String? = null,
)

@Serializable
data class ScheduleDetailDto(
    val id: String,
    @SerialName("unit_id") val unitId: String,
    @SerialName("unit_name") val unitName: String,
    val jyear: Int,
    val jmonth: Int,
    val title: String,
    val status: String,
    @SerialName("return_note") val returnNote: String? = null,
    val days: List<ScheduleDayDto> = emptyList(),
    val members: List<ScheduleMemberDto> = emptyList(),
    val warnings: List<ScheduleWarningDto> = emptyList(),
    /** کارهای مجاز کاربر روی این برنامه: approve، return و ... */
    val actions: List<String> = emptyList(),
)

@Serializable
data class ReturnBody(val note: String)

@Serializable
data class OnDutyPersonDto(
    val person: SchedulePersonDto,
    val mobile: String? = null,
    val code: Int,
    val label: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("on_duty_now") val onDutyNow: Boolean = false,
    @SerialName("oncall_now") val oncallNow: Boolean = false,
)

@Serializable
data class OnDutyUnitDto(
    @SerialName("unit_id") val unitId: String,
    @SerialName("unit_name") val unitName: String,
    val published: Boolean = true,
    val people: List<OnDutyPersonDto> = emptyList(),
)

@Serializable
data class OnDutyDto(
    val day: String,
    val jalali: String,
    val units: List<OnDutyUnitDto> = emptyList(),
)

@Serializable
data class SwapCellDto(
    val day: String,
    val jalali: String,
    val code: Int? = null,
    val label: String? = null,
)

@Serializable
data class SwapDto(
    val id: String,
    @SerialName("schedule_id") val scheduleId: String,
    @SerialName("unit_name") val unitName: String,
    @SerialName("month_title") val monthTitle: String,
    val requester: SchedulePersonDto,
    val partner: SchedulePersonDto,
    @SerialName("requester_day") val requesterDay: SwapCellDto,
    @SerialName("partner_day") val partnerDay: SwapCellDto,
    val note: String? = null,
    /** pending_partner | pending_manager | approved | rejected | cancelled */
    val status: String,
    @SerialName("decision_note") val decisionNote: String? = null,
    /** accept، reject، approve، cancel */
    val actions: List<String> = emptyList(),
)

@Serializable
data class SwapBody(
    @SerialName("schedule_id") val scheduleId: String,
    @SerialName("my_day") val myDay: String,
    @SerialName("partner_id") val partnerId: String,
    @SerialName("partner_day") val partnerDay: String,
    val note: String? = null,
)

@Serializable
data class SwapAnswerBody(val note: String? = null)

package com.healthmonitor.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthmonitor.app.data.local.entities.*
import com.healthmonitor.app.data.repository.HealthMonitorRepository
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.ActivePatientManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ExportUiState(
    val isLoading: Boolean  = false,
    val error: String?      = null,
    val success: Boolean    = false
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    application: Application,
    private val repository: HealthMonitorRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun exportAndShare() {
        viewModelScope.launch {
            _uiState.value = ExportUiState(isLoading = true)

            runCatching {
                val ctx       = getApplication<Application>().applicationContext
                val patientId = ActivePatientManager.getActivePatientId()
                    ?: error("يرجى اختيار مريض أولاً.")
                val caseId    = ActiveCaseManager.getActiveCaseId()

                // ── Fetch all data ────────────────────────────────────────
                val patient    = repository.getPatientById(patientId)
                    ?: error("تعذّر تحميل بيانات المريض.")
                val case_      = caseId?.let { repository.getCaseById(it) }
                val meds       = repository.getMedicationsByPatientOnce(patientId)
                    .filter { it.caseId == caseId && !it.isDeleted }
                val bpReadings = repository.getLatestBloodPressure(patientId)
                val symptoms   = repository.getSymptomsByPatient(patientId)

                // Collect flows as one-shot snapshots
                val bpList  = repository.getBloodPressureReadings(patientId)
                    .let { flow ->
                        var result = emptyList<BloodPressureEntity>()
                        val job = viewModelScope.launch {
                            flow.collect { result = it }
                        }
                        kotlinx.coroutines.delay(100)
                        job.cancel()
                        result
                    }

                val symptomList = repository.getSymptomsByPatient(patientId)
                    .let { flow ->
                        var result = emptyList<SymptomEntity>()
                        val job = viewModelScope.launch {
                            flow.collect { result = it }
                        }
                        kotlinx.coroutines.delay(100)
                        job.cancel()
                        result
                    }

                val labReports = repository.getLabReportsByPatient(patientId)
                    .let { flow ->
                        var result = emptyList<LabReportEntity>()
                        val job = viewModelScope.launch {
                            flow.collect { result = it }
                        }
                        kotlinx.coroutines.delay(100)
                        job.cancel()
                        result
                    }

                // ── Build text report ─────────────────────────────────────
                val text = buildReportText(
                    patient     = patient,
                    case_       = case_,
                    medications = meds,
                    bpReadings  = bpList.take(20),
                    symptoms    = symptomList.take(20),
                    labReports  = labReports.take(10)
                )

                // ── Write to cache file ───────────────────────────────────
                val fileName = "medical_report_${patient.name.replace(" ", "_")}_${dateStamp()}.txt"
                val file = File(ctx.cacheDir, fileName).apply { writeText(text) }

                // ── Share via Android share sheet ─────────────────────────
                shareFile(ctx, file)

            }.onSuccess {
                _uiState.value = ExportUiState(success = true)
            }.onFailure { e ->
                _uiState.value = ExportUiState(error = e.message ?: "تعذّر تصدير التقرير.")
            }
        }
    }

    fun clearState() { _uiState.value = ExportUiState() }

    // ── Report builder ────────────────────────────────────────────────────────

    private fun buildReportText(
        patient: PatientEntity,
        case_: CaseEntity?,
        medications: List<MedicationEntity>,
        bpReadings: List<BloodPressureEntity>,
        symptoms: List<SymptomEntity>,
        labReports: List<LabReportEntity>
    ): String = buildString {

        val sep    = "═".repeat(50)
        val subSep = "─".repeat(40)
        val now    = SimpleDateFormat("d MMMM yyyy  hh:mm a", Locale.getDefault()).format(Date())

        // ── Header ────────────────────────────────────────────────────────
        appendLine(sep)
        appendLine("          التقرير الطبي الشامل")
        appendLine("          تاريخ التصدير: $now")
        appendLine(sep)
        appendLine()

        // ── Patient info ──────────────────────────────────────────────────
        appendLine("▌ بيانات المريض")
        appendLine(subSep)
        appendLine("  الاسم:           ${patient.name}")
        if (patient.age > 0) appendLine("  العمر:           ${patient.age} سنة")
        if (patient.gender.isNotBlank()) appendLine("  الجنس:           ${patient.gender}")
        if (!patient.bloodType.isNullOrBlank()) appendLine("  فصيلة الدم:     ${patient.bloodType}")
        if (patient.medicalConditions.isNotBlank()) appendLine("  الحالات الطبية: ${patient.medicalConditions}")
        if (!patient.emergencyContact.isNullOrBlank()) {
            appendLine("  جهة الطوارئ:    ${patient.emergencyContact}  ${patient.emergencyPhone ?: ""}")
        }
        appendLine()

        // ── Case info ─────────────────────────────────────────────────────
        if (case_ != null) {
            appendLine("▌ الحالة الطبية")
            appendLine(subSep)
            appendLine("  العنوان:  ${case_.title}")
            if (!case_.doctorName.isNullOrBlank()) appendLine("  الطبيب:   ${case_.doctorName}")
            appendLine("  الحالة:   ${if (case_.isClosed) "مغلقة" else "مفتوحة"}")
            appendLine("  تاريخ الفتح: ${formatDate(case_.createdAt)}")
            if (!case_.notes.isNullOrBlank()) appendLine("  ملاحظات:  ${case_.notes}")
            appendLine()
        }

        // ── Medications ───────────────────────────────────────────────────
        appendLine("▌ الأدوية (${medications.size})")
        appendLine(subSep)
        if (medications.isEmpty()) {
            appendLine("  لا توجد أدوية مسجلة.")
        } else {
            medications.forEach { med ->
                appendLine("  • ${med.name}  —  ${med.dosage} ${med.unit}")
                appendLine("    التكرار: ${med.frequency}  |  المدة: ${med.durationDays} يوم")
                appendLine("    الحالة: ${if (med.isActive) "نشط" else "موقوف"}")
                if (!med.notes.isNullOrBlank()) appendLine("    ملاحظة: ${med.notes}")
            }
        }
        appendLine()

        // ── Blood pressure ────────────────────────────────────────────────
        appendLine("▌ قراءات ضغط الدم (آخر ${bpReadings.size})")
        appendLine(subSep)
        if (bpReadings.isEmpty()) {
            appendLine("  لا توجد قراءات.")
        } else {
            bpReadings.forEach { r ->
                val status = when {
                    r.systolic < 120 && r.diastolic < 80 -> "طبيعي"
                    r.systolic < 140 && r.diastolic < 90 -> "مرتفع قليلاً"
                    else                                   -> "مرتفع"
                }
                val pulse = r.pulse?.let { "  نبض: $it bpm" } ?: ""
                val spo2  = r.oxygenSaturation?.let { "  أكسجين: $it%" } ?: ""
                appendLine("  ${formatDate(r.time)}  →  ${r.systolic}/${r.diastolic} mmHg  ($status)$pulse$spo2")
            }
            // Average
            val avgSys = bpReadings.map { it.systolic }.average().toInt()
            val avgDia = bpReadings.map { it.diastolic }.average().toInt()
            appendLine()
            appendLine("  المتوسط: $avgSys/$avgDia mmHg")
        }
        appendLine()

        // ── Symptoms ──────────────────────────────────────────────────────
        appendLine("▌ الأعراض (آخر ${symptoms.size})")
        appendLine(subSep)
        if (symptoms.isEmpty()) {
            appendLine("  لا توجد أعراض مسجلة.")
        } else {
            symptoms.forEach { s ->
                val inhaler = if (s.inhalerUsed) {
                    val improved = when (s.improvementAfterInhaler) {
                        true  -> " — تحسّن بعد البخاخ"
                        false -> " — لم يتحسن"
                        else  -> " — استُخدم البخاخ"
                    }
                    improved
                } else ""
                appendLine("  ${formatDate(s.time)}  →  ${s.symptomType}  (${s.severity})$inhaler")
                if (!s.notes.isNullOrBlank()) appendLine("    ملاحظة: ${s.notes}")
            }
        }
        appendLine()

        // ── Lab reports ───────────────────────────────────────────────────
        if (labReports.isNotEmpty()) {
            appendLine("▌ التقارير المخبرية (${labReports.size})")
            appendLine(subSep)
            labReports.forEach { r ->
                appendLine("  • ${r.reportName}  —  ${formatDate(r.reportDate)}")
            }
            appendLine()
        }

        // ── Footer ────────────────────────────────────────────────────────
        appendLine(sep)
        appendLine("  تم إنشاء هذا التقرير تلقائياً بتطبيق Health Monitor")
        appendLine("  هذا التقرير للأغراض الطبية فقط — يرجى مراجعة الطبيب المختص.")
        appendLine(sep)
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    private fun shareFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type    = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "التقرير الطبي — ${file.nameWithoutExtension}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة التقرير").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun formatDate(ts: Long): String =
        SimpleDateFormat("d MMM yyyy  hh:mm a", Locale.getDefault()).format(Date(ts))

    private fun dateStamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
}
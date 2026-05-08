package com.healthmonitor.app.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthmonitor.app.data.ai.GeminiHealthAiService
import com.healthmonitor.app.data.local.entities.LabReportEntity
import com.healthmonitor.app.data.local.entities.LabReportItemEntity
import com.healthmonitor.app.data.repository.HealthMonitorRepository
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.ActivePatientManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class LabReportWithItems(
    val report: LabReportEntity,
    val items: List<LabReportItemEntity>
)

data class LabReportUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class LabReportViewModel @Inject constructor(
    application: Application,
    private val repository: HealthMonitorRepository,
    private val geminiService: GeminiHealthAiService
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LabReportUiState())
    val uiState: StateFlow<LabReportUiState> = _uiState.asStateFlow()

    // Flat list of reports filtered to the active case date window
    private val _reports = MutableStateFlow<List<LabReportEntity>>(emptyList())
    val reports: StateFlow<List<LabReportEntity>> = _reports.asStateFlow()

    // Reports with their items fully loaded — used for detail expansion
    private val _reportsWithItems = MutableStateFlow<List<LabReportWithItems>>(emptyList())
    val reportsWithItems: StateFlow<List<LabReportWithItems>> = _reportsWithItems.asStateFlow()

    private val patientId: String?
        get() = ActivePatientManager.getActivePatientId()

    private val caseId: String?
        get() = ActiveCaseManager.getActiveCaseId()

    init {
        observePatientAndCase()
    }

    private fun observePatientAndCase() {
        viewModelScope.launch {
            combine(
                ActivePatientManager.activePatientIdFlow,
                ActiveCaseManager.activeCaseIdFlow
            ) { pid, cid -> pid to cid }
                .collect { (pid, _) ->
                    if (pid != null) loadReports(pid)
                    else _reportsWithItems.value = emptyList()
                }
        }
    }

    private fun loadReports(pid: String) {
        viewModelScope.launch {
            repository.getLabReportsByPatient(pid).collect { reports ->
                _reports.value = reports
                // Load items for each report
                val withItems = reports.map { report ->
                    LabReportWithItems(
                        report = report,
                        items  = repository.getItemsForReportOnce(report.id)
                    )
                }
                _reportsWithItems.value = withItems
            }
        }
    }

    /**
     * Scans a lab report image, extracts structured data via Gemini, and persists it.
     */
    fun scanAndSaveLabReport(image: Bitmap) {
        val pid = patientId ?: run {
            _uiState.value = LabReportUiState(error = "يرجى اختيار مريض أولاً.")
            return
        }

        viewModelScope.launch {
            _uiState.value = LabReportUiState(isLoading = true)

            runCatching {
                val structured = geminiService.extractLabReportFromImage(image)

                if (structured.items.isEmpty()) {
                    error("لم يتم اكتشاف أي نتائج في الصورة. تأكد من وضوح التقرير.")
                }

                // Parse report date from the extracted "YYYY-MM-DD" string
                val reportDateMillis = structured.reportDate?.let { dateStr ->
                    runCatching {
                        LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }.getOrNull()
                } ?: LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val report = LabReportEntity(
                    patientId  = pid,
                    caseId     = caseId,
                    reportName = structured.reportName,
                    reportDate = reportDateMillis
                )

                repository.insertLabReport(report)

                val items = structured.items.map { item ->
                    LabReportItemEntity(
                        reportId       = report.id,
                        testItem       = item.testItem,
                        simpleDescription = item.simpleDescription,
                        result         = item.result,
                        unit           = item.unit,
                        referenceRange = item.referenceRange,
                        status         = item.status
                    )
                }
                repository.insertLabReportItems(items)

                structured.reportName
            }.onSuccess { name ->
                _uiState.value = LabReportUiState(successMessage = "تم حفظ تقرير «$name» بنجاح.")
            }.onFailure { e ->
                _uiState.value = LabReportUiState(
                    error = e.message ?: "حدث خطأ أثناء معالجة الصورة."
                )
            }
        }
    }

    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            repository.softDeleteLabReport(reportId)
        }
    }

    fun clearState() {
        _uiState.value = LabReportUiState()
    }
}
package com.healthmonitor.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthmonitor.app.data.ai.GeminiHealthAiService
import com.healthmonitor.app.data.ai.MedicineInfoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

data class AiToolsUiState(
    val reportText: String          = "",
    val selectedImageName: String?  = null,
    val summary: String             = "",
    val isLoading: Boolean          = false,
    val error: String?              = null
)

data class MedicineInfoUiState(
    val medicineName: String        = "",
    val result: MedicineInfoResult? = null,
    val isLoading: Boolean          = false,
    val error: String?              = null
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class AiToolsViewModel @Inject constructor(
    private val geminiService: GeminiHealthAiService
) : ViewModel() {

    // ── Report summarizer state ───────────────────────────────────────────

    private val _uiState = MutableStateFlow(AiToolsUiState())
    val uiState: StateFlow<AiToolsUiState> = _uiState.asStateFlow()

    // ── Medicine info state ───────────────────────────────────────────────

    private val _medicineInfoState = MutableStateFlow(MedicineInfoUiState())
    val medicineInfoState: StateFlow<MedicineInfoUiState> = _medicineInfoState.asStateFlow()

    // ── Report actions ────────────────────────────────────────────────────

    fun updateReportText(value: String) {
        _uiState.update { it.copy(reportText = value, error = null) }
    }

    fun reportImageLoadFailed() {
        _uiState.update { it.copy(error = "تعذّر فتح صورة التقرير. يرجى اختيار صورة أخرى.") }
    }

    fun summarizeReport() {
        val text = _uiState.value.reportText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "أدخل نص التقرير أولًا.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, summary = "") }
            runCatching {
                geminiService.summarizeMedicalReportInArabic(text)
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        summary   = summary.ifBlank { "لم يُرجع الذكاء الاصطناعي ملخصًا واضحًا." },
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error     = error.message ?: "تعذّر الاتصال بخدمة الذكاء الاصطناعي."
                    )
                }
            }
        }
    }

    fun summarizeReportImage(bitmap: Bitmap, imageName: String? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedImageName = imageName ?: "صورة تقرير",
                    isLoading         = true,
                    error             = null,
                    summary           = ""
                )
            }
            runCatching {
                geminiService.summarizeMedicalReportImageInArabic(bitmap)
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        summary   = summary.ifBlank { "لم يُرجع الذكاء الاصطناعي ملخصًا واضحًا." },
                        isLoading = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error     = error.message ?: "تعذّر تحليل صورة التقرير."
                    )
                }
            }
        }
    }

    fun clear() {
        _uiState.value = AiToolsUiState()
    }

    // ── Medicine info actions ─────────────────────────────────────────────

    /**
     * Fetches comprehensive information about a medicine by name.
     * Called from both the AI Tools screen and the medicine card button.
     */
    fun fetchMedicineInfo(medicineName: String) {
        if (medicineName.isBlank()) return
        viewModelScope.launch {
            _medicineInfoState.update {
                it.copy(
                    medicineName = medicineName,
                    isLoading    = true,
                    error        = null,
                    result       = null
                )
            }
            runCatching {
                geminiService.getMedicineInfo(medicineName)
            }.onSuccess { result ->
                _medicineInfoState.update {
                    it.copy(result = result, isLoading = false)
                }
            }.onFailure { error ->
                _medicineInfoState.update {
                    it.copy(
                        isLoading = false,
                        error     = error.message ?: "تعذّر الحصول على معلومات الدواء."
                    )
                }
            }
        }
    }

    fun clearMedicineInfo() {
        _medicineInfoState.value = MedicineInfoUiState()
    }
}
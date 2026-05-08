package com.healthmonitor.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.CaseEntity
import com.healthmonitor.app.ui.design.HMDialog
import com.healthmonitor.app.ui.design.HMTextField
import com.healthmonitor.app.ui.viewmodel.CaseViewModel
import com.healthmonitor.app.ui.viewmodel.PatientViewModel

@Composable
fun CasesScreen(
    navController: NavHostController,
    caseViewModel: CaseViewModel   = hiltViewModel(),
    patientViewModel: PatientViewModel = hiltViewModel()
) {
    // Collect the reactive active-patient flow so this screen recomposes on switch
    val resolvedPatientId by patientViewModel.activePatientIdFlow.collectAsState()

    val cases by if (resolvedPatientId != null) {
        caseViewModel.getCasesForPatient(resolvedPatientId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CaseEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "حالات المريض",
                style = MaterialTheme.typography.headlineMedium.copy(color = Color.White),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { showAdd = true },
                enabled = resolvedPatientId != null
            ) {
                Text("إضافة حالة")
            }
        }

        if (resolvedPatientId == null) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Text(
                    "يرجى اختيار مريض أولاً من القائمة العلوية.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF888888))
                )
            }
            return@Column
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (cases.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Text(
                    "لا توجد حالات مسجلة لهذا المريض. اضغط «إضافة حالة» للبدء.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF888888))
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cases, key = { it.id }) { c ->
                CaseRow(
                    c       = c,
                    onOpen  = { caseViewModel.setActiveCase(c.id) },
                    onClose = { caseViewModel.closeCase(c) },
                    onDelete = { deleteTarget = c }
                )
            }
        }
    }

    if (showAdd) {
        AddCaseDialog(
            onDismiss = { showAdd = false },
            onSave    = { title, doctor, notes ->
                // resolvedPatientId is guaranteed > 0 here (button is disabled otherwise)
                caseViewModel.addCase(resolvedPatientId!!, title, doctor, notes)
                showAdd = false
            }
        )
    }

    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف الحالة") },
            text = { Text("هل تريد حذف الحالة '${c.title}'؟ سيتم حذفها مؤقتاً.") },
            confirmButton = {
                Button(onClick = { caseViewModel.deleteCase(c); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun CaseRow(c: CaseEntity, onOpen: () -> Unit, onClose: () -> Unit, onDelete: () -> Unit) {
    val statusColor = if (c.isClosed) Color(0xFF555555) else Color(0xFF4CAF50)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(8.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    c.title,
                    style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFFE8E8E8))
                )
                if (!c.doctorName.isNullOrBlank()) {
                    Text(
                        c.doctorName,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFBBBBBB))
                    )
                }
                Text(
                    if (c.isClosed) "مغلقة" else "مفتوحة",
                    style = MaterialTheme.typography.labelSmall.copy(color = statusColor)
                )
            }
            if (!c.isClosed) {
                Row {
                    Button(
                        onClick = onOpen,
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("فتح") }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onClose) { Text("إغلاق") }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFFF5252))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "مغلقة",
                        style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF555555))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFFF5252))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCaseDialog(
    onDismiss: () -> Unit,
    onSave: (String, String?, String?) -> Unit
) {
    var title  by remember { mutableStateOf("") }
    var doctor by remember { mutableStateOf("") }
    var notes  by remember { mutableStateOf("") }

    HMDialog(
        onDismiss       = onDismiss,
        title           = "إضافة حالة",
        confirmText     = "حفظ",
        onConfirm       = {
            if (title.isNotBlank()) {
                onSave(title.trim(), doctor.ifBlank { null }, notes.ifBlank { null })
            }
        },
        confirmEnabled  = title.isNotBlank(),
        dismissText     = "إلغاء"
    ) {
        HMTextField(
            value         = title,
            onValueChange = { title = it },
            label         = "عنوان الحالة *",
            placeholder   = "مثال: متابعة ضغط الدم",
            isError       = false,
            singleLine    = true
        )
        HMTextField(
            value         = doctor,
            onValueChange = { doctor = it },
            label         = "اسم الطبيب (اختياري)",
            singleLine    = true
        )
        HMTextField(
            value         = notes,
            onValueChange = { notes = it },
            label         = "ملاحظات (اختياري)",
            singleLine    = false,
            minLines      = 2,
            maxLines      = 4
        )
    }
}

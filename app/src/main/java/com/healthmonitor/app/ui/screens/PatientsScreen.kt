package com.healthmonitor.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.PatientEntity
import com.healthmonitor.app.ui.viewmodel.PatientViewModel

@Composable
fun PatientsScreen(
    navController: NavHostController,
    viewModel: PatientViewModel = hiltViewModel()
) {
    val patients        by viewModel.getAllPatients().collectAsState(initial = emptyList())
    val activePatientId by viewModel.activePatientIdFlow.collectAsState()
    var deleteTarget    by remember { mutableStateOf<PatientEntity?>(null) }
    var showAdd         by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFF0F0F0F),
        floatingActionButton = {
            FloatingActionButton(
                onClick           = { showAdd = true },
                containerColor    = Color(0xFF4CAF50),
                contentColor      = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مريض")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "المرضى",
                style    = MaterialTheme.typography.headlineMedium.copy(
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (patients.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("لا يوجد مرضى مسجلين بعد.", color = Color(0xFFBBBBBB))
                        Text(
                            "اضغط + لإضافة مريض جديد.",
                            color = Color(0xFF888888)
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(patients, key = { it.id }) { patient ->
                        PatientRow(
                            patient  = patient,
                            isActive = patient.id == activePatientId,
                            onSelect = { viewModel.setActivePatientId(patient.id) },
                            onDelete = { deleteTarget = patient }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddPatientDialog(
            onDismiss = { showAdd = false },
            onSave    = { name, age, gender ->
                viewModel.addPatient(name, age.toIntOrNull() ?: 0, gender)
                showAdd = false
            }
        )
    }

    deleteTarget?.let { patient ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف المريض") },
            text = { Text("هل أنت متأكد أنك تريد حذف المريض '${patient.name}'؟ سيتم نقل الحذف كـ soft-delete.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePatient(patient)
                    deleteTarget = null
                }) { Text("حذف") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun PatientRow(patient: PatientEntity, isActive: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(8.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border   = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) Color(0xFF4CAF50) else Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint     = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        patient.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = if (isActive) Color(0xFF4CAF50) else Color(0xFFE8E8E8)
                        )
                    )
                }
                if (patient.age > 0) {
                    Text(
                        "العمر: ${patient.age}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFBBBBBB))
                    )
                }
                if (patient.gender.isNotBlank()) {
                    Text(
                        patient.gender,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888888))
                    )
                }
            }
            if (!isActive) {
                Row {
                    Button(
                        onClick = onSelect,
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape   = RoundedCornerShape(8.dp)
                    ) { Text("تحديد") }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color(0xFFFF5252))
                    }
                }
            } else {
                Text(
                    "نشط",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF4CAF50))
                )
            }
        }
    }
}

@Composable
private fun AddPatientDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name   by remember { mutableStateOf("") }
    var age    by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("ذكر") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1A1A1A),
        titleContentColor = Color(0xFFE8E8E8),
        textContentColor  = Color(0xFFE8E8E8),
        title = { Text("مريض جديد", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("الاسم *") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF4CAF50),
                        focusedLabelColor    = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color(0xFF2A2A2A),
                        focusedTextColor     = Color(0xFFE8E8E8),
                        unfocusedTextColor   = Color(0xFFE8E8E8)
                    )
                )
                OutlinedTextField(
                    value         = age,
                    onValueChange = { age = it.filter(Char::isDigit) },
                    label         = { Text("العمر") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF4CAF50),
                        focusedLabelColor    = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color(0xFF2A2A2A),
                        focusedTextColor     = Color(0xFFE8E8E8),
                        unfocusedTextColor   = Color(0xFFE8E8E8)
                    )
                )
                Text("الجنس", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF888888)))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ذكر", "أنثى").forEach { option ->
                        FilterChip(
                            selected = gender == option,
                            onClick  = { gender = option },
                            label    = { Text(option) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor    = Color(0xFF2D5E2D),
                                selectedLabelColor        = Color(0xFF4CAF50),
                                containerColor            = Color(0xFF2A2A2A),
                                labelColor                = Color(0xFFBBBBBB)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (name.isNotBlank()) onSave(name.trim(), age.trim(), gender) },
                enabled  = name.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("حفظ") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border  = BorderStroke(1.dp, Color(0xFF555555)),
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBBBBBB))
            ) { Text("إلغاء") }
        }
    )
}

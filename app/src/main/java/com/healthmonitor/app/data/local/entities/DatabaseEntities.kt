package com.healthmonitor.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

import java.util.UUID

@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val age: Int,
    val gender: String,
    val bloodType: String? = null,
    val medicalConditions: String = "",
    val emergencyContact: String? = null,
    val emergencyPhone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val caseId: String? = null,
    val name: String,
    val dosage: String,
    val dosageFormKey: String = "",
    val unit: String = "mg",
    val frequency: String = "once_daily",
    val timesPerDay: Int = 1,
    val scheduledTimes: String = "",   // JSON array ["08:00","20:00"]
    val inventoryMode: String = "course",
    val durationDays: Int = 7,         // Duration in days (e.g., 3, 7, 10)
    val totalQuantity: Double? = null,
    val currentQuantity: Double? = null,
    val quantityPerDose: Double = 1.0,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

// closedAt stores the epoch-millis when the case was closed.
// Used to filter BP readings and symptoms by the case's active time window.
// null means the case is still open (use System.currentTimeMillis() as the upper bound).
@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val doctorName: String? = null,
    val title: String,
    val notes: String? = null,
    val isClosed: Boolean = false,
    /** Epoch-millis when the case was closed. Null while case is still open. */
    val closedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "medication_logs")
data class MedicationLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val patientId: String,
    val date: Long,
    val scheduledTime: String = "",
    val time: Long = System.currentTimeMillis(),
    val taken: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "blood_pressure_readings")
data class BloodPressureEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int? = null,
    val oxygenSaturation: Int? = null,
    val date: Long,
    val time: Long,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "symptoms")
data class SymptomEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val date: Long,
    val time: Long,
    val symptomType: String,
    val severity: String,
    val notes: String? = null,
    val inhalerUsed: Boolean = false,
    val improvementAfterInhaler: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

/** Body temperature reading — patient-owned, filtered by case date range. */
@Entity(tableName = "body_temperature_readings")
data class BodyTemperatureEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    /** Temperature value in Celsius. */
    val temperature: Float,
    /** Measurement site: "oral", "axillary", "rectal", "tympanic", "forehead" */
    val site: String = "oral",
    val date: Long,
    val time: Long,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "inhaler_logs")
data class InhalerLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val date: Long,
    val time: Long,
    val inhalerType: String,
    val reason: String,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "lab_tests")
data class LabTestEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val testName: String,
    val testDate: Long,
    val resultValue: Double? = null,
    val resultUnit: String? = null,
    val referenceRange: String? = null,
    val status: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

// AFTER
@Entity(
    tableName = "medication_schedules",
    foreignKeys = [ForeignKey(
        entity = MedicationEntity::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["medicationId"])]
)
data class MedicationScheduleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val scheduledTime: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "doctor_notes")
data class DoctorNoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val doctorName: String? = null,
    val visitDate: Long,
    val diagnosis: String? = null,
    val recommendations: String? = null,
    val medicationsPrescribed: String = "",
    val nextVisitDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

/**
 * One lab report (e.g. CBC, Lipid Profile) captured from an image.
 * reportDate = the actual test date extracted from the document (epoch-millis).
 * capturedAt = when the user scanned the image.
 */
@Entity(tableName = "lab_reports")
data class LabReportEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val caseId: String? = null,
    /** Human-readable report name, e.g. "CBC", "Lipid Profile" */
    val reportName: String,
    /** Actual test date extracted from the document (epoch-millis, start-of-day) */
    val reportDate: Long,
    /** When the user scanned/imported the report */
    val capturedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

/**
 * One individual parameter row inside a lab report.
 * status: "Normal" | "High" | "Low"
 */
@Entity(
    tableName = "lab_report_items",
    foreignKeys = [ForeignKey(
        entity = LabReportEntity::class,
        parentColumns = ["id"],
        childColumns = ["reportId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["reportId"])]
)
data class LabReportItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val reportId: String,
    val testItem: String,
    val result: String,
    val unit: String? = null,
    val referenceRange: String? = null,
    /** "Normal" | "High" | "Low" */
    val status: String = "Normal",
    val simpleDescription: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

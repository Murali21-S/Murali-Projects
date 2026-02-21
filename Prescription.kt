package com.example.medihelp.model

data class Prescription(
    val id: String = "",
    val patientId: String = "",
    val doctorId: String = "",
    val medicines: List<String> = emptyList(),
    val date: String = "",
    val notes: String = ""
) 
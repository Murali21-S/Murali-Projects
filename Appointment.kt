package com.example.medihelp.model

data class Appointment(
    val id: String = "",
    val patientId: String = "",
    val doctorId: String = "",
    val date: String = "",
    val time: String = "",
    val status: String = "pending" // pending/confirmed/done
) 
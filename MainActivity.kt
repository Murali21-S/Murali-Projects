package com.example.medihelp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.medihelp.ui.theme.MediHelpTheme
import com.example.medihelp.ui.screens.LoginScreen
import com.example.medihelp.ui.screens.RegisterScreen
import com.example.medihelp.ui.screens.PatientDashboardScreen
import com.example.medihelp.ui.screens.DoctorDashboardScreen
import com.example.medihelp.ui.screens.AppointmentBookingScreen
import com.example.medihelp.ui.screens.PrescriptionScreen
import com.example.medihelp.ui.screens.ViewPrescriptionsScreen
import com.example.medihelp.ui.screens.ManageAppointmentsScreen
import com.example.medihelp.ui.screens.DoctorListScreen
import com.example.medihelp.ui.screens.PatientListScreen
import com.example.medihelp.ui.screens.DoctorRegisterScreen
import com.example.medihelp.ui.screens.VideoCallScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

sealed class AuthScreen {
    data object Register : AuthScreen()
    data object Login : AuthScreen()
    data object DoctorRegister : AuthScreen()
    data class Main(val role: String) : AuthScreen() // "patient" or "doctor"
    data class BookAppointment(val doctorId: String) : AuthScreen()
    data object ViewPrescriptions : AuthScreen()
    data object ManageAppointments : AuthScreen()
    data class WritePrescription(val patientId: String) : AuthScreen()
    data object SelectDoctor : AuthScreen()
    data object SelectPatient : AuthScreen()
    data class VideoCall(val channelName: String) : AuthScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Google Play Services
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            googleApiAvailability.showErrorNotification(this, resultCode)
        }
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        val channelNameFromIntent = intent.getStringExtra("channelName")
        setContent {
            MediHelpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MediHelpApp(channelNameFromIntent)
                }
            }
        }
    }
}

@Composable
fun MediHelpApp(channelNameFromIntent: String? = null) {
    var currentScreen by remember { mutableStateOf<AuthScreen>(AuthScreen.Register) }
    val auth = FirebaseAuth.getInstance()
    var userId by remember { mutableStateOf<String?>(null) }
    var selectedDoctorId by remember { mutableStateOf<String?>(null) }
    var selectedDoctorName by remember { mutableStateOf<String?>(null) }

    // If launched from notification, go directly to video call
    LaunchedEffect(channelNameFromIntent) {
        if (!channelNameFromIntent.isNullOrEmpty()) {
            currentScreen = AuthScreen.VideoCall(channelNameFromIntent)
        }
    }

    // When login is successful, set userId
    val onLoginSuccess = {
        userId = auth.currentUser?.uid
    }

    // Fetch user role when userId changes
    LaunchedEffect(userId) {
        userId?.let {
            val doc = FirebaseFirestore.getInstance().collection("users").document(it).get().await()
            val role = doc.getString("role") ?: "patient"
            currentScreen = AuthScreen.Main(role)
        }
    }

    fun sendFCMToDoctor(
        doctorFcmToken: String,
        channelName: String,
        patientName: String,
        serverKey: String
    ) {
        val client = OkHttpClient()
        val json = JSONObject()
        val notification = JSONObject()
        notification.put("title", "Incoming Video Call")
        notification.put("body", "$patientName is calling you!")
        val data = JSONObject()
        data.put("channelName", channelName)
        json.put("to", doctorFcmToken)
        json.put("notification", notification)
        json.put("data", data)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://fcm.googleapis.com/fcm/send")
            .addHeader("Authorization", "key=$serverKey") // <-- Replace with your server key
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        Thread {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("FCM send failed: ${response.body?.string()}")
                } else {
                    println("FCM sent: ${response.body?.string()}")
                }
            }
        }.start()
    }

    when (val screen = currentScreen) {
        is AuthScreen.Register -> RegisterScreen(
            onRegisterSuccess = { currentScreen = AuthScreen.Login },
            onSwitchToLogin = { currentScreen = AuthScreen.Login },
            onBack = { currentScreen = AuthScreen.Login }
        )
        is AuthScreen.DoctorRegister -> DoctorRegisterScreen(
            onRegisterSuccess = { currentScreen = AuthScreen.Login },
            onSwitchToLogin = { currentScreen = AuthScreen.Login },
            onBack = { currentScreen = AuthScreen.Login }
        )
        is AuthScreen.Login -> LoginScreen(
            onLoginSuccess = onLoginSuccess,
            onSwitchToRegister = { currentScreen = AuthScreen.Register },
            onSwitchToDoctorRegister = { currentScreen = AuthScreen.DoctorRegister },
            onBack = null // No back from login
        )
        is AuthScreen.Main -> {
            if (screen.role == "doctor") {
                DoctorDashboardScreen(
                    onManageAppointments = { currentScreen = AuthScreen.ManageAppointments },
                    onWritePrescription = { currentScreen = AuthScreen.SelectPatient },
                    onStartVideoCall = {
                        val doctorId = userId ?: "doctor"
                        val patientId = selectedDoctorId ?: "patient"
                        val channel = "call_${doctorId}_$patientId"
                        FirebaseFirestore.getInstance().collection("users").document(doctorId).get()
                            .addOnSuccessListener { doc ->
                                val doctorFcmToken = doc.getString("fcmToken")
                                val patientName = auth.currentUser?.displayName ?: "Patient"
                                if (doctorFcmToken != null) {
                                    sendFCMToDoctor(
                                        doctorFcmToken = doctorFcmToken,
                                        channelName = channel,
                                        patientName = patientName,
                                        serverKey = "YOUR_SERVER_KEY_HERE"
                                    )
                                }
                                currentScreen = AuthScreen.VideoCall(channel)
                            }
                    },
                    onLogout = { currentScreen = AuthScreen.Login }
                )
            } else {
                PatientDashboardScreen(
                    onBookAppointment = { currentScreen = AuthScreen.SelectDoctor },
                    onViewPrescriptions = { currentScreen = AuthScreen.ViewPrescriptions },
                    onStartVideoCall = {
                        if (selectedDoctorId != null && userId != null) {
                            val channel = "call_${selectedDoctorId}_${userId}"
                            FirebaseFirestore.getInstance().collection("users").document(selectedDoctorId!!).get()
                                .addOnSuccessListener { doc ->
                                    val doctorFcmToken = doc.getString("fcmToken")
                                    val patientName = auth.currentUser?.displayName ?: "Patient"
                                    if (doctorFcmToken != null) {
                                        sendFCMToDoctor(
                                            doctorFcmToken = doctorFcmToken,
                                            channelName = channel,
                                            patientName = patientName,
                                            serverKey = "YOUR_SERVER_KEY_HERE"
                                        )
                                    }
                                    currentScreen = AuthScreen.VideoCall(channel)
                                }
                        }
                    },
                    onLogout = { currentScreen = AuthScreen.Login },
                    selectedDoctorId = selectedDoctorId,
                    selectedDoctorName = selectedDoctorName
                )
            }
        }
        is AuthScreen.SelectDoctor -> DoctorListScreen(
            onDoctorSelected = { doctorId, doctorName ->
                selectedDoctorId = doctorId
                selectedDoctorName = doctorName
                currentScreen = AuthScreen.Main("patient")
            },
            onBack = { currentScreen = AuthScreen.Main("patient") }
        )
        is AuthScreen.SelectPatient -> PatientListScreen(
            onPatientSelected = { patientId -> currentScreen = AuthScreen.WritePrescription(patientId) },
            onBack = { currentScreen = AuthScreen.Main("doctor") }
        )
        is AuthScreen.BookAppointment -> AppointmentBookingScreen(
            doctorId = screen.doctorId,
            onBooked = { currentScreen = AuthScreen.Main("patient") },
            onBack = { currentScreen = AuthScreen.SelectDoctor }
        )
        is AuthScreen.ViewPrescriptions -> ViewPrescriptionsScreen(
            onBack = { currentScreen = AuthScreen.Main("patient") }
        )
        is AuthScreen.ManageAppointments -> ManageAppointmentsScreen(
            onBack = { currentScreen = AuthScreen.Main("doctor") }
        )
        is AuthScreen.WritePrescription -> PrescriptionScreen(
            patientId = screen.patientId,
            onPrescribed = { currentScreen = AuthScreen.Main("doctor") },
            onBack = { currentScreen = AuthScreen.SelectPatient }
        )
        is AuthScreen.VideoCall -> VideoCallScreen(
            channelName = screen.channelName,
            onLeave = { currentScreen = AuthScreen.Main("patient") }
        )
    }
}
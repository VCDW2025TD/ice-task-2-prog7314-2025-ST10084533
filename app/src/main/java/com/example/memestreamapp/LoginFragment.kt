package com.example.memestreamapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase


class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignIn
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    private lateinit var sharedPreferences: SharedPreferences
    private val BIOMETRIC_PREF_KEY = "biometric_enabled"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize FirebaseAuth instance
        auth = Firebase.auth
        sharedPreferences = requireActivity().getSharedPreferences(
            "app_prefs",
            android.content.Context.MODE_PRIVATE
        )

        // Configure Google Sign-In Options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        // Create GoogleSignInClient
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        // Setup ActivityResultLauncher for Google Sign-In
        signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data: Intent? = result.data
                handleSignInResult(data)
            } else {
                Toast.makeText(requireContext(), "Sign in cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up Google Sign-In button click
        val googleSignInButton: Button = view.findViewById(R.id.btn_google_sign_in)
        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }

        // Check if user is already signed in
        checkCurrentUser()
    }

    private fun signInWithGoogle() {
        // Launch the Google sign-in screen
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }
    private fun handleSignInResult(data: Intent?) {
        try {
            // Get the GoogleSignInAccount from the result
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            account?.let {
                // Extract the ID token and send to Firebase
                firebaseAuthWithGoogle(it.idToken!!)
            }
        } catch (e: ApiException) {
            Toast.makeText(requireContext(), "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success, navigate to main app
                    val user = auth.currentUser
                    Toast.makeText(requireContext(), "Welcome ${user?.displayName}", Toast.LENGTH_SHORT).show()

                    // Navigate to main activity and trigger biometric authentication
                    navigateToMainApp()
                } else {
                    // If sign in fails, display a message to the user.
                    Toast.makeText(requireContext(), "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is already signed in, navigate to main app
            navigateToMainApp()
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(requireActivity(), MainActivity::class.java)
        startActivity(intent)
        requireActivity().finish() // Finish the activity so user can't go back
    }

    // Biometric methods
    private fun isBiometricEnabled(): Boolean {
        return sharedPreferences.getBoolean(BIOMETRIC_PREF_KEY, false)
    }

    private fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(BIOMETRIC_PREF_KEY, enabled).apply()
    }

    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(requireContext())
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(
                    requireContext(),
                    "No biometric hardware available",
                    Toast.LENGTH_SHORT
                ).show()
                false
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Toast.makeText(
                    requireContext(),
                    "Biometric hardware unavailable",
                    Toast.LENGTH_SHORT
                ).show()
                false
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(
                    requireContext(),
                    "No biometric credentials enrolled",
                    Toast.LENGTH_SHORT
                ).show()
                // Optionally open settings to enroll
                val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                    )
                }
                startActivity(intent)
                false
            }

            else -> false
        }
    }

    private fun showBiometricSetupPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable Biometric Login")
            .setSubtitle("Use your fingerprint or face to quickly access the app")
            .setNegativeButtonText("Skip")
            .setConfirmationRequired(false)
            .build()

        val biometricPrompt = createBiometricPrompt(
            onSuccess = {
                setBiometricEnabled(true)
                Toast.makeText(requireContext(), "Biometric login enabled", Toast.LENGTH_SHORT).show()
                navigateToMainApp()
            },
            onError = { errorCode, errString ->
                Toast.makeText(requireContext(), "Biometric setup failed: $errString", Toast.LENGTH_SHORT).show()
                navigateToMainApp() // Continue without biometric
            },
            onFailed = {
                Toast.makeText(requireContext(), "Biometric not recognized", Toast.LENGTH_SHORT).show()
                navigateToMainApp() // Continue without biometric
            },
            onNegativeButton = {
                setBiometricEnabled(false)
                navigateToMainApp() // User chose to skip
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showBiometricPrompt() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Login")
            .setSubtitle("Authenticate to continue")
            .setNegativeButtonText("Use Account Login")
            .setConfirmationRequired(true)
            .build()

        val biometricPrompt = createBiometricPrompt(
            onSuccess = {
                // Biometric success - navigate to main app
                navigateToMainApp()
            },
            onError = { errorCode, errString ->
                Toast.makeText(requireContext(), "Biometric error: $errString", Toast.LENGTH_SHORT).show()
                // Fall back to Google sign-in
                signOutAndRestart()
            },
            onFailed = {
                Toast.makeText(requireContext(), "Authentication failed", Toast.LENGTH_SHORT).show()
                // Allow retry by showing prompt again
                showBiometricPrompt()
            },
            onNegativeButton = {
                // User chose to use account login instead
                signOutAndRestart()
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    private fun createBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (Int, CharSequence) -> Unit,
        onFailed: () -> Unit,
        onNegativeButton: () -> Unit
    ): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(requireContext())

        return BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onNegativeButton()
                } else {
                    onError(errorCode, errString)
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        })
    }

    private fun signOutAndRestart() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener(requireActivity()) {
            // Restart the activity to show login screen
            requireActivity().finish()
            startActivity(requireActivity().intent)
        }
    }

    override fun onStart() {
        super.onStart()
        checkCurrentUser()
    }

}







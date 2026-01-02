package app.luzzy.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.extensions.toast
import com.goodwy.commons.extensions.viewBinding
import app.luzzy.R
import app.luzzy.auth.GoogleAuthRepository
import app.luzzy.databinding.ActivityGoogleLoginBinding
import app.luzzy.network.RetrofitClient
import app.luzzy.network.models.GoogleLoginRequest
import app.luzzy.utils.SharedPrefsManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.launch

class GoogleLoginActivity : SimpleActivity() {

    companion object {
        private const val TAG = "GoogleLoginActivity"
    }

    private val binding by viewBinding(ActivityGoogleLoginBinding::inflate)
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleAuthRepository: GoogleAuthRepository

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar(binding.toolbar)
        setupEdgeToEdge(padBottomSystem = listOf(binding.scrollView))

        googleAuthRepository = GoogleAuthRepository(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(getString(R.string.google_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupUI()
    }

    private fun setupUI() {
        binding.googleSignInButton.setOnClickListener {
            signIn()
        }

        binding.skipButton.setOnClickListener {
            finish()
        }
    }

    private fun signIn() {
        setLoading(true)
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)

            Log.d(TAG, "Inicio de sesión exitoso: ${account.email}")

            sendToServer(account)
        } catch (e: ApiException) {
            Log.e(TAG, "Error en inicio de sesión con Google", e)
            setLoading(false)
            toast(R.string.google_login_failed)
        }
    }

    private fun sendToServer(account: GoogleSignInAccount) {
        lifecycleScope.launch {
            try {
                val fcmToken = SharedPrefsManager.getCurrentToken(this@GoogleLoginActivity) ?: ""

                val request = GoogleLoginRequest(
                    email = account.email ?: "",
                    deviceToken = fcmToken,
                    displayName = account.displayName,
                    photoUrl = account.photoUrl?.toString()
                )

                Log.d(TAG, "Enviando datos al servidor...")

                val response = RetrofitClient.apiService.googleLogin(request)

                if (response.isSuccessful && response.body() != null) {
                    val serverToken = response.body()!!.token
                    val userData = response.body()!!.user

                    googleAuthRepository.saveGoogleAuthData(
                        token = serverToken,
                        email = userData.email,
                        displayName = userData.displayName,
                        photoUrl = userData.photoUrl
                    )

                    SharedPrefsManager.saveAuthToken(this@GoogleLoginActivity, serverToken)

                    Log.d(TAG, "Login exitoso, datos guardados")

                    toast(R.string.google_login_success)
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Log.e(TAG, "Error del servidor: ${response.code()}")
                    toast(R.string.google_login_server_error)
                    setLoading(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al enviar al servidor", e)
                toast(R.string.google_login_server_error)
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.googleSignInButton.isEnabled = !loading
        binding.skipButton.isEnabled = !loading

        if (loading) {
            binding.progressIndicator.visibility = View.VISIBLE
        } else {
            binding.progressIndicator.visibility = View.GONE
        }
    }
}

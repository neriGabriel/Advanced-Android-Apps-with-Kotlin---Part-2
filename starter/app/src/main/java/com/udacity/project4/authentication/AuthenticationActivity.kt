package com.udacity.project4.authentication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.udacity.project4.R
import com.udacity.project4.locationreminders.RemindersActivity
import kotlinx.android.synthetic.main.activity_authentication.*


/**
 * This class should be the starting point of the app, It asks the users to sign in / register, and redirects the
 * signed in users to the RemindersActivity.
 */
class AuthenticationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authentication)

        login_button.setOnClickListener { doLogin() }
    }


    private fun doLogin() {
        val authenticationProviders = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder()
            .setAvailableProviders(authenticationProviders)
            .setTheme(R.style.AppTheme)
            .setLogo(R.drawable.ic_launcher_background)
            .build(),
            SING_IN_RESULT_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when(requestCode) {
            SING_IN_RESULT_CODE -> {
                val response = IdpResponse.fromResultIntent(data)
                if(resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "Successfully signed as user: " +
                            "${FirebaseAuth.getInstance().currentUser?.displayName}!")

                    startActivity(Intent(applicationContext, RemindersActivity::class.java))
                } else {
                    Log.e(TAG, "Authentication failed, error: ${response?.error?.errorCode}")
                    Toast.makeText(applicationContext,
                        "Authentication failed, error: ${response?.error?.errorCode}",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val SING_IN_RESULT_CODE = 1000
        const val TAG = "AuthenticationActivity"
    }
}

package com.tarunguptaraja.coldemailer

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tarunguptaraja.coldemailer.presentation.ats.AtsScorerActivity
import com.tarunguptaraja.coldemailer.presentation.interview.MockInterviewActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BottomNavHelper @Inject constructor(
    private val profilePreferenceManager: ProfilePreferenceManager
) {

    fun setupBottomNav(
        activity: Activity,
        bottomNav: BottomNavigationView,
        currentNavId: Int
    ) {
        bottomNav.selectedItemId = currentNavId

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_ats -> {
                    if (currentNavId != R.id.nav_ats) {
                        activity.startActivity(Intent(activity, AtsScorerActivity::class.java))
                        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        activity.finish()
                    }
                    true
                }
                R.id.nav_mailer -> {
                    if (currentNavId != R.id.nav_mailer) {
                        if (profilePreferenceManager.hasProfile()) {
                            activity.startActivity(Intent(activity, SendMailActivity::class.java))
                            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            activity.finish()
                        } else {
                            Toast.makeText(activity, "Please set up your profile first", Toast.LENGTH_SHORT).show()
                            activity.startActivity(Intent(activity, MainActivity::class.java))
                            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            activity.finish()
                        }
                    }
                    true
                }
                R.id.nav_mock_interview -> {
                    if (currentNavId != R.id.nav_mock_interview) {
                        activity.startActivity(Intent(activity, MockInterviewActivity::class.java))
                        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        activity.finish()
                    }
                    true
                }
                R.id.nav_profile -> {
                    if (currentNavId != R.id.nav_profile) {
                        activity.startActivity(Intent(activity, MainActivity::class.java))
                        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        activity.finish()
                    }
                    true
                }
                else -> false
            }
        }
    }
}

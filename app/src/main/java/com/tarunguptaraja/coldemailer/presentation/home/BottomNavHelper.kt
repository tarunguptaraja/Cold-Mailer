package com.tarunguptaraja.coldemailer.presentation.home

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tarunguptaraja.coldemailer.MainActivity
import com.tarunguptaraja.coldemailer.ProfilePreferenceManager
import com.tarunguptaraja.coldemailer.R
import com.tarunguptaraja.coldemailer.SendMailActivity
import com.tarunguptaraja.coldemailer.presentation.ats.AtsScorerActivity

object BottomNavHelper {
    fun setupBottomNav(
        activity: Activity,
        bottomNavigationView: BottomNavigationView,
        currentMenuItemId: Int,
        profilePreferenceManager: ProfilePreferenceManager
    ) {
        bottomNavigationView.selectedItemId = currentMenuItemId

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == currentMenuItemId) return@setOnItemSelectedListener true

            when (item.itemId) {
                R.id.nav_ats -> {
                    val intent = Intent(activity, AtsScorerActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    activity.startActivity(intent)
                    activity.overridePendingTransition(0, 0)
                }
                R.id.nav_mailer -> {
                    if (profilePreferenceManager.hasProfile()) {
                        val intent = Intent(activity, SendMailActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                    } else {
                        Toast.makeText(activity, "Please set up your Job Roles first", Toast.LENGTH_SHORT).show()
                        return@setOnItemSelectedListener false
                    }
                }
                R.id.nav_mock_interview -> {
                    val intent = Intent(activity, com.tarunguptaraja.coldemailer.presentation.interview.MockInterviewActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    activity.startActivity(intent)
                    activity.overridePendingTransition(0, 0)
                }
                R.id.nav_profile -> {
                    val intent = Intent(activity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    activity.startActivity(intent)
                    activity.overridePendingTransition(0, 0)
                }
            }
            true
        }
    }
}

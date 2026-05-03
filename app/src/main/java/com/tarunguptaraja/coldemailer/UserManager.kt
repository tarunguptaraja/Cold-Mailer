package com.tarunguptaraja.coldemailer

import android.content.Context
import android.provider.Settings
import com.google.firebase.firestore.FirebaseFirestore
import com.tarunguptaraja.coldemailer.domain.model.JobRole
import com.tarunguptaraja.coldemailer.domain.model.Profile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profilePreferenceManager: ProfilePreferenceManager,
    private val tokenManager: TokenManager,
    private val remoteConfigManager: RemoteConfigManager
) {

    private val firestore = FirebaseFirestore.getInstance()

    val androidId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    suspend fun performFullSync(): Boolean {
        return try {
            val userDoc = firestore.collection("Users").document(androidId)
            val snapshot = userDoc.get().await()

            if (!snapshot.exists()) {
                // First time ever, push local to remote if local exists
                val localProfile = profilePreferenceManager.getProfile()
                if (localProfile != null) {
                    pushLocalToRemote(localProfile)
                } else {
                    // New user, nothing to sync yet
                }
                return true
            }

            // Sync Profile Info (Name/Contact)
            val remoteLastUpdated = snapshot.getLong("lastUpdated") ?: 0L
            val localProfile = profilePreferenceManager.getProfile()
            
            val shouldPull = localProfile == null || 
                            remoteLastUpdated > localProfile.lastUpdated || 
                            (remoteLastUpdated == localProfile.lastUpdated && localProfile.roles.isEmpty())
            
            if (shouldPull) {
                // Pull from remote
                val name = snapshot.getString("name") ?: ""
                val contact = snapshot.getString("contactNumber") ?: ""
                val tokens = snapshot.getLong("tokensRemaining") ?: 100000L
                tokenManager.setTokens(tokens)
                
                // Pull Roles
                val rolesSnapshot = userDoc.collection("roles").get().await()
                val roles = rolesSnapshot.documents.map { doc ->
                    JobRole(
                        id = doc.id,
                        roleName = doc.getString("roleName") ?: "",
                        subject = doc.getString("subject") ?: "",
                        body = doc.getString("body") ?: "",
                        resumeFileName = doc.getString("resumeFileName") ?: "",
                        resumeText = doc.getString("resumeText") ?: "",
                        lastUpdated = doc.getLong("lastUpdated") ?: 0L
                    )
                }
                
                val newLocalProfile = Profile(name, contact, roles, remoteLastUpdated)
                profilePreferenceManager.saveProfile(newLocalProfile)
            } else if (localProfile != null && localProfile.lastUpdated > remoteLastUpdated) {
                // Push local to remote
                pushLocalToRemote(localProfile)
            }

            // Sync Token Transactions
            syncTokenTransactions(userDoc)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun pushLocalToRemote(profile: Profile) {
        try {
            val userDoc = firestore.collection("Users").document(androidId)
            val data = hashMapOf(
                "name" to profile.name,
                "contactNumber" to profile.contactNumber,
                "tokensRemaining" to tokenManager.getRemainingTokens(),
                "lastUpdated" to profile.lastUpdated
            )
            userDoc.set(data, com.google.firebase.firestore.SetOptions.merge()).await()

            val rolesCol = userDoc.collection("roles")
            profile.roles.forEach { role ->
                val roleData = hashMapOf(
                    "roleName" to role.roleName,
                    "subject" to role.subject,
                    "body" to role.body,
                    "resumeFileName" to role.resumeFileName,
                    "resumeText" to role.resumeText,
                    "lastUpdated" to role.lastUpdated
                )
                rolesCol.document(role.id).set(roleData).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncTokenTransactions(userDoc: com.google.firebase.firestore.DocumentReference) {
        try {
            val remoteTxSnapshot = userDoc.collection("transactions").get().await()
            val remoteTx = remoteTxSnapshot.documents.map { doc ->
                com.tarunguptaraja.coldemailer.domain.model.TokenTransaction(
                    id = doc.id,
                    amount = doc.getLong("amount")?.toInt() ?: 0,
                    type = doc.getString("type") ?: "",
                    description = doc.getString("description") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }

            val localTx = profilePreferenceManager.getTransactions()
            
            // Merge by ID
            val mergedTx = (remoteTx + localTx).distinctBy { it.id }.sortedByDescending { it.timestamp }
            profilePreferenceManager.saveTransactions(mergedTx)

            // Push new local ones to remote
            localTx.filter { l -> remoteTx.none { r -> r.id == l.id } }.forEach { tx ->
                val txData = hashMapOf(
                    "amount" to tx.amount,
                    "type" to tx.type,
                    "description" to tx.description,
                    "timestamp" to tx.timestamp
                )
                userDoc.collection("transactions").document(tx.id).set(txData).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun initializeUserIfRequired(name: String, contactNumber: String): Long {
        return try {
            val userDoc = firestore.collection("Users").document(androidId)
            val snapshot = userDoc.get().await()

            if (!snapshot.exists()) {
                remoteConfigManager.fetchAndActivate()
                val awardedTokens = remoteConfigManager.getOnboardingTokens()
                
                val initialData = hashMapOf(
                    "name" to name,
                    "contactNumber" to contactNumber,
                    "tokensRemaining" to awardedTokens,
                    "lastUpdated" to System.currentTimeMillis()
                )
                userDoc.set(initialData).await()
                tokenManager.setTokens(awardedTokens)
                
                val profile = Profile(name, contactNumber, emptyList(), System.currentTimeMillis())
                profilePreferenceManager.saveProfile(profile)
                
                // Add initial transaction
                val tx = com.tarunguptaraja.coldemailer.domain.model.TokenTransaction(
                    id = java.util.UUID.randomUUID().toString(),
                    amount = awardedTokens.toInt(),
                    type = "AWARD",
                    description = "Onboarding Welcome Bonus",
                    timestamp = System.currentTimeMillis()
                )
                addTokenTransaction(tx)
                
                awardedTokens
            } else {
                0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    suspend fun addTokenTransaction(tx: com.tarunguptaraja.coldemailer.domain.model.TokenTransaction) {
        profilePreferenceManager.addTransaction(tx)
        try {
            val userDoc = firestore.collection("Users").document(androidId)
            val txData = hashMapOf(
                "amount" to tx.amount,
                "type" to tx.type,
                "description" to tx.description,
                "timestamp" to tx.timestamp
            )
            userDoc.collection("transactions").document(tx.id).set(txData).await()
            userDoc.update("tokensRemaining", tokenManager.getRemainingTokens()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun isUserRegisteredInFirestore(): Boolean {
        return try {
            val snapshot = firestore.collection("Users").document(androidId).get().await()
            snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }
    
    fun syncProfileToFirestore(profile: Profile) {
        val userDoc = firestore.collection("Users").document(androidId)
        val data = hashMapOf<String, Any>(
            "name" to profile.name,
            "contactNumber" to profile.contactNumber,
            "lastUpdated" to profile.lastUpdated
        )
        userDoc.set(data, com.google.firebase.firestore.SetOptions.merge())
        
        val rolesCollection = userDoc.collection("roles")
        profile.roles.forEach { role ->
            val roleData = hashMapOf<String, Any>(
                "roleName" to role.roleName,
                "subject" to role.subject,
                "body" to role.body,
                "resumeFileName" to role.resumeFileName,
                "resumeText" to role.resumeText,
                "lastUpdated" to role.lastUpdated
            )
            rolesCollection.document(role.id).set(roleData)
        }
    }
    
    fun deleteRoleFromFirestore(roleId: String) {
        val userDoc = firestore.collection("Users").document(androidId)
        userDoc.collection("roles").document(roleId).delete()
    }

    fun syncTokensToFirestore(tokens: Long) {
        val userDoc = firestore.collection("Users").document(androidId)
        userDoc.update("tokensRemaining", tokens)
    }
}

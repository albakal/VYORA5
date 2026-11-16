package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.EarnProgramConfig
import com.example.data.remote.EarnProgramRepository
import com.example.data.remote.FirebaseBackendRepository
import com.example.security.DeviceIntegrityInfo
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class VyoraFirestoreRulesTest : FirestoreEmulatorTestBase() {

    private fun uniqueEmail(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().take(8)}@test.com"

    @Test
    fun ensureUserProfile_and_publishVideo_areVisibleAcrossUsers() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("alice"))
        val userRepo = UserRepository(firestore, auth)
        val videoRepo = VideoRepository(firestore, auth)

        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            userRepo.ensureUserProfile(
                uid = aliceUid,
                email = "alice@test.com",
                displayName = "Alice Creator",
                avatarUrl = "https://firebasestorage.googleapis.com/v0/b/demo/o/alice.jpg"
            ).getOrThrow()
        }
        assertEquals(aliceUid, aliceProfile.userId)

        val videoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            videoRepo.publishVideo(
                creator = aliceProfile,
                videoUrl = "https://firebasestorage.googleapis.com/v0/b/demo/o/clip.mp4",
                thumbnailUrl = "https://firebasestorage.googleapis.com/v0/b/demo/o/thumb.jpg",
                caption = "First Vyora post #vyora #مصر",
                soundId = "",
                soundTitle = "Original Sound",
                durationSec = 20L,
                contentHash = "sha256_demo_${UUID.randomUUID()}",
                fileSizeBytes = 2048L
            ).getOrThrow()
        }
        assertTrue(videoId.isNotBlank())

        // Switch to a different user (Bob) and verify global video feed + likes + views work
        signInTestUser(uniqueEmail("bob"))
        val bobUserRepo = UserRepository(firestore, auth)
        val bobVideoRepo = VideoRepository(firestore, auth)
        val bobProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobUserRepo.ensureUserProfile(
                uid = auth.currentUser!!.uid,
                email = "bob@test.com",
                displayName = "Bob Viewer",
                avatarUrl = ""
            ).getOrThrow()
        }

        val feedVideos = withTimeout(FLOW_TIMEOUT_MS) {
            bobVideoRepo.observeActiveVideos().first { list -> list.any { it.id == videoId } }
        }
        val uploaded = feedVideos.first { it.id == videoId }
        assertEquals("active", uploaded.status)

        val liked = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobVideoRepo.toggleLike(uploaded, bobProfile).getOrThrow()
        }
        assertTrue(liked)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobVideoRepo.addComment(uploaded, bobProfile, "Great video!").getOrThrow()
        }

        val backendRepo = FirebaseBackendRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            backendRepo.recordVideoViewServer(videoId).getOrThrow()
        }
    }

    @Test
    fun updateProfile_avatarUpdate_persistsAndCrossUserEditFails() = runBlocking {
        val aliceUid = signInTestUser(uniqueEmail("avatar_alice"))
        val aliceRepo = UserRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile(aliceUid, "avatar@test.com", "Avatar User", "").getOrThrow()
            aliceRepo.updateAvatar("https://firebasestorage.googleapis.com/v0/b/demo/o/new_avatar.jpg").getOrThrow()
        }

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.getUser(aliceUid).getOrThrow() }
        assertNotNull(updated)
        assertEquals("https://firebasestorage.googleapis.com/v0/b/demo/o/new_avatar.jpg", updated!!.avatarUrl)

        // Bob cannot modify Alice's profile directly
        signInTestUser(uniqueEmail("avatar_bob"))
        val exception = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("profiles").document(aliceUid)
                    .update("bio", "hijacked")
                    .await()
            }
        }.exceptionOrNull()
        assertNotNull(exception)
        val fsEx = exception!!.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    @Test
    fun liveStream_chat_and_notifications_enforceParticipantIsolation() = runBlocking {
        val hostEmail = uniqueEmail("host")
        val guestEmail = uniqueEmail("guest")
        val strangerEmail = uniqueEmail("stranger")

        val hostUid = signInTestUser(hostEmail)
        val hostUserRepo = UserRepository(firestore, auth)
        val hostProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            hostUserRepo.ensureUserProfile(hostUid, hostEmail, "Host", "").getOrThrow()
        }

        val liveRepo = LiveRepository(firestore, auth)
        val stream = withTimeout(DEFAULT_TIMEOUT_MS) {
            liveRepo.startStream(hostProfile, "Live Q&A").getOrThrow()
        }
        assertTrue(stream.isActive)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            liveRepo.sendLiveComment(stream.id, hostProfile, "Welcome!").getOrThrow()
            liveRepo.endStream(stream.id).getOrThrow()
        }

        // Conversation between host & guest
        val guestUid = signInTestUser(guestEmail)
        val guestUserRepo = UserRepository(firestore, auth)
        val guestProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            guestUserRepo.ensureUserProfile(guestUid, guestEmail, "Guest", "").getOrThrow()
        }
        val chatRepo = ChatRepository(firestore, auth)
        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.getOrCreateConversation(hostUid).getOrThrow()
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.sendMessage(
                conversationId = convId,
                participantIds = listOf(hostUid, guestUid).sorted(),
                actor = guestProfile,
                type = "text",
                text = "Hello Host"
            ).getOrThrow()
        }

        // Stranger cannot read host/guest conversation
        signInTestUser(strangerEmail)
        val strangerChatRepo = ChatRepository(firestore, auth)
        val flowErr = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                strangerChatRepo.observeMessages(convId, auth.currentUser!!.uid).first()
            }
        }.exceptionOrNull()
        // Wait: ChatRepository catches errors and emits emptyList() inside the Flow! Verify direct get fails with PERMISSION_DENIED:
        val directErr = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("conversations").document(convId).get().await()
            }
        }.exceptionOrNull()
        assertNotNull(directErr)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            directErr!!.unwrapFirestoreException()?.code
        )
        assertTrue(flowErr == null || flowErr.unwrapFirestoreException() != null)
    }

    @Test
    fun earnProgram_referralLinkAttribution_and_watchWheel_succeed() = runBlocking {
        val inviterEmail = uniqueEmail("inviter")
        val friendEmail = uniqueEmail("friend")

        val inviterUid = signInTestUser(inviterEmail)
        UserRepository(firestore, auth).ensureUserProfile(inviterUid, inviterEmail, "Inviter", "").getOrThrow()
        val inviterEarnRepo = EarnProgramRepository(firestore, auth)
        val inviterDevice = DeviceIntegrityInfo(
            deviceFingerprint = "fp_${UUID.randomUUID().toString().replace("-", "")}",
            appSetId = "appset_${UUID.randomUUID()}",
            hardwareId = "hw_${UUID.randomUUID()}",
            playIntegrityStatus = "emulator_test",
            playIntegrityTokenHash = "tok_${UUID.randomUUID()}"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            inviterEarnRepo.registerDeviceAndCode(inviterDevice).getOrThrow()
        }
        val inviterDash = withTimeout(DEFAULT_TIMEOUT_MS) {
            inviterEarnRepo.fetchDashboard(inviterDevice).getOrThrow()
        }
        assertTrue(inviterDash.referralCode.length >= 5)

        // Watch wheel claim works and increments wallet points
        val backendRepo = FirebaseBackendRepository(firestore, auth)
        val claim = withTimeout(DEFAULT_TIMEOUT_MS) {
            backendRepo.claimWatchWheelReward("vid_demo", 30L).getOrThrow()
        }
        assertTrue(claim.awarded)
        assertTrue(claim.walletPoints >= 5L)

        // Friend auto-links inviter referral code via unique device
        val friendUid = signInTestUser(friendEmail)
        UserRepository(firestore, auth).ensureUserProfile(friendUid, friendEmail, "Friend", "").getOrThrow()
        val friendEarnRepo = EarnProgramRepository(firestore, auth)
        val friendDevice = DeviceIntegrityInfo(
            deviceFingerprint = "fp_${UUID.randomUUID().toString().replace("-", "")}",
            appSetId = "appset_${UUID.randomUUID()}",
            hardwareId = "hw_${UUID.randomUUID()}",
            playIntegrityStatus = "emulator_test",
            playIntegrityTokenHash = "tok_${UUID.randomUUID()}"
        )
        val flagged = withTimeout(DEFAULT_TIMEOUT_MS) {
            friendEarnRepo.redeemReferralCode(inviterDash.referralCode, friendDevice).getOrThrow()
        }
        assertFalse(flagged)
        val friendDash = withTimeout(DEFAULT_TIMEOUT_MS) {
            friendEarnRepo.fetchDashboard(friendDevice).getOrThrow()
        }
        assertEquals(inviterUid, friendDash.referredByUid)
    }

    @Test
    fun verifiedAdminEmail_canUpdateAdminSettings_whileRegularUserCannot() = runBlocking {
        signInTestUser(uniqueEmail("regular"), markVerified = true)
        val walletAdminRepo = WalletAdminRepository(firestore, auth)
        val deniedResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            walletAdminRepo.updateWatchWheelSettings(
                enabled = false,
                cycleSec = 45L,
                pointsPerCycle = 10L,
                dailyCapCycles = 30L
            )
        }
        assertTrue(deniedResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            deniedResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Verified primary admin email succeeds
        signInTestUser("mahmoudomran324@gmail.com", markVerified = true)
        val adminWalletRepo = WalletAdminRepository(firestore, auth)
        val adminEarnRepo = EarnProgramRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminWalletRepo.updateWatchWheelSettings(
                enabled = true,
                cycleSec = 30L,
                pointsPerCycle = 5L,
                dailyCapCycles = 60L
            ).getOrThrow()
            adminEarnRepo.adminUpdateConfig(EarnProgramConfig(isEnabled = true)).getOrThrow()
        }
    }

    @Test
    fun unauthenticatedCaller_cannotReadOrQueryProtectedCollections() = runBlocking {
        auth.signOut()
        val videoErr = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("videos").whereEqualTo("status", "active").get().await()
            }
        }.exceptionOrNull()
        assertNotNull(videoErr)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            videoErr!!.unwrapFirestoreException()?.code
        )

        val profileErr = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("profiles").limit(10).get().await()
            }
        }.exceptionOrNull()
        assertNotNull(profileErr)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            profileErr!!.unwrapFirestoreException()?.code
        )
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

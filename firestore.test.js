const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-vyora-09b7e2be-39f1-49bc-910d-92eb9b4daa3f";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_EMAIL = "mahmoudomran324@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function dbFor(ctx) {
  return ctx.firestore({ databaseId: DATABASE_ID });
}

test("Unauthenticated user cannot read profiles, videos, wallets, or earn_program", async () => {
  const unauth = dbFor(testEnv.unauthenticatedContext());
  await assertFails(unauth.collection("profiles").doc(ALICE_UID).get());
  await assertFails(unauth.collection("videos").where("status", "==", "active").get());
  await assertFails(unauth.collection("wallets").doc(ALICE_UID).get());
  await assertFails(unauth.collection("earn_program").doc(ALICE_UID).get());
});

test("PII isolation: user private info cannot be read or written by another user", async () => {
  const alice = dbFor(testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }));
  const bob = dbFor(testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }));

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(alice.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("private").doc("info").get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
      userId: ALICE_UID,
      email: "spoof@example.com",
      createdAt: pastTimestamp(),
    })
  );
});

test("Email spoofing test: unverified admin email is rejected on admin actions", async () => {
  const spoofAdmin = dbFor(
    testEnv.authenticatedContext("spoof_1", { email: ADMIN_EMAIL, email_verified: false })
  );
  await assertFails(
    spoofAdmin.collection("admin_settings").doc("global").set({
      pointValueUsd: 0.01,
      minWithdrawalUsd: 10.0,
      rewardsEnabled: true,
      maxVideoDurationSec: 180,
      watchWheelEnabled: true,
      watchWheelCycleSec: 30,
      watchWheelPointsPerCycle: 5,
      watchWheelDailyCapCycles: 60,
      updatedAt: pastTimestamp(),
    })
  );

  const realAdmin = dbFor(
    testEnv.authenticatedContext("admin_1", { email: ADMIN_EMAIL, email_verified: true })
  );
  await assertSucceeds(
    realAdmin.collection("admin_settings").doc("global").set({
      pointValueUsd: 0.01,
      minWithdrawalUsd: 10.0,
      rewardsEnabled: true,
      maxVideoDurationSec: 180,
      watchWheelEnabled: true,
      watchWheelCycleSec: 30,
      watchWheelPointsPerCycle: 5,
      watchWheelDailyCapCycles: 60,
      updatedBy: "admin_1",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Profile creation & avatar update succeed for owner and block shadow privilege escalation", async () => {
  const alice = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const bob = dbFor(testEnv.authenticatedContext(BOB_UID));

  await assertFails(
    alice.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      isPrivate: false,
      isBanned: false,
      followersCount: 1000,
      followingCount: 0,
      likesCount: 0,
      createdAt: pastTimestamp(),
    })
  );

  await assertFails(
    alice.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      isPrivate: false,
      isBanned: false,
      followersCount: 0,
      followingCount: 0,
      likesCount: 0,
      ghostAdmin: true,
      createdAt: pastTimestamp(),
    })
  );

  await assertSucceeds(
    alice.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      bio: "",
      avatarUrl: "",
      isPrivate: false,
      isBanned: false,
      followersCount: 0,
      followingCount: 0,
      likesCount: 0,
      interests: [],
      language: "ar",
      themeMode: "dark",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(
    alice.collection("profiles").doc(ALICE_UID).update({
      avatarUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/avatar.jpg",
      updatedAt: pastTimestamp(),
    })
  );

  await assertFails(
    bob.collection("profiles").doc(ALICE_UID).update({
      avatarUrl: "https://evil.example/a.jpg",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Videos: publish, active query alignment, batch like atomicity, and owner delete", async () => {
  const alice = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const bob = dbFor(testEnv.authenticatedContext(BOB_UID));

  await assertSucceeds(
    alice.collection("videos").doc("vid_1").set({
      userId: ALICE_UID,
      creatorUsername: "alice",
      creatorAvatarUrl: "",
      videoUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/v1.mp4",
      thumbnailUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/t1.jpg",
      caption: "Hello #vyora",
      hashtags: ["vyora"],
      soundId: "",
      soundTitle: "Original Sound",
      durationSec: 15,
      viewsCount: 0,
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      bookmarksCount: 0,
      status: "active",
      contentHash: "abc123",
      fileSizeBytes: 1024,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Unfiltered list must fail (rules are not filters)
  await assertFails(bob.collection("videos").get());
  // Filtered active feed must succeed for any signed-in user
  await assertSucceeds(bob.collection("videos").where("status", "==", "active").get());

  // Atomic batch like as Bob
  const likeBatch = bob.batch();
  likeBatch.set(bob.collection("videos").doc("vid_1").collection("likes").doc(BOB_UID), {
    userId: BOB_UID,
    videoId: "vid_1",
    createdAt: pastTimestamp(),
  });
  likeBatch.update(bob.collection("videos").doc("vid_1"), {
    likesCount: 1,
    updatedAt: pastTimestamp(),
  });
  await assertSucceeds(likeBatch.commit());

  // Forged like increment without like doc fails
  await assertFails(
    bob.collection("videos").doc("vid_1").update({
      likesCount: 2,
      updatedAt: pastTimestamp(),
    })
  );

  // Bob cannot delete Alice's video; Alice can
  await assertFails(bob.collection("videos").doc("vid_1").delete());
  await assertSucceeds(alice.collection("videos").doc("vid_1").delete());
});

test("Conversations & notifications enforce participant and recipient isolation", async () => {
  const alice = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const bob = dbFor(testEnv.authenticatedContext(BOB_UID));
  const charlie = dbFor(testEnv.authenticatedContext("charlie_789"));

  const convId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    alice.collection("conversations").doc(convId).set({
      participantIds: [ALICE_UID, BOB_UID],
      lastMessage: "Hi",
      lastMessageType: "text",
      lastSenderId: ALICE_UID,
      lastMessageAt: pastTimestamp(),
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertFails(charlie.collection("conversations").doc(convId).get());
  await assertFails(alice.collection("conversations").get());
  await assertSucceeds(
    bob.collection("conversations").where("participantIds", "array-contains", BOB_UID).get()
  );

  await assertSucceeds(
    alice.collection("notifications").doc("n1").set({
      recipientId: BOB_UID,
      actorId: ALICE_UID,
      actorUsername: "alice",
      actorAvatarUrl: "",
      type: "like",
      targetId: "vid_1",
      message: "Liked your video",
      isRead: false,
      createdAt: pastTimestamp(),
    })
  );
  await assertFails(charlie.collection("notifications").where("recipientId", "==", BOB_UID).get());
  await assertSucceeds(bob.collection("notifications").where("recipientId", "==", BOB_UID).get());
});

test("Earn With Us prevents client self-approval and blocks wallet balance inflation", async () => {
  const alice = dbFor(testEnv.authenticatedContext(ALICE_UID));

  await assertFails(
    alice.collection("earn_program").doc(ALICE_UID).set({
      userId: ALICE_UID,
      referralCode: "VY123456",
      personalVideosCount: 0,
      invitedFriendsCount: 0,
      completedFriendsCount: 0,
      status: "approved",
      isDeviceEligible: true,
      suspiciousFlag: false,
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(
    alice.collection("earn_program").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      referralCode: "VY123456",
      personalVideosCount: 0,
      invitedFriendsCount: 0,
      completedFriendsCount: 0,
      status: "in_progress",
      isDeviceEligible: true,
      suspiciousFlag: false,
      updatedAt: pastTimestamp(),
    })
  );

  await assertFails(
    alice.collection("wallets").doc(ALICE_UID).set({
      userId: ALICE_UID,
      points: 999999,
      balanceUsd: 9999.0,
      totalEarnedUsd: 9999.0,
      updatedAt: pastTimestamp(),
    })
  );
});

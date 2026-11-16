# VYORA Firebase Backend Setup

VYORA uses **Firebase Authentication (Google Sign-In)**, **Cloud Firestore** (named database `ai-studio-android-vyora-09b7e2be-39f1-49bc-910d-92eb9b4daa3f`), and **Firebase Storage** (`com-aistudio-tokshort-ap-f5c5c.firebasestorage.app`).

## 1. Enable Firebase Storage & Publish Rules
1. Open [Firebase Console](https://console.firebase.google.com/project/com-aistudio-tokshort-ap-f5c5c/storage) for project `com-aistudio-tokshort-ap-f5c5c`.
2. Click **Get Started** on Storage if it is not enabled yet.
3. Paste the contents of `/storage.rules` into the Storage **Rules** tab and click **Publish**.

## 2. Google Sign-In
- `app/google-services.json` is configured for package `com.aistudio.vyora.kxpvqr` with SHA-1 `8B:0B:3F:38:64:F2:4C:D7:E1:DE:4A:4A:56:52:00:08:33:89:43:0D`.
- Enable **Google** under **Firebase Console -> Authentication -> Sign-in method** if not already enabled.

## 3. Automatic Referral Invite Links
- Links use `https://vyora.app/invite?ref=<CODE>` and `vyora://invite?ref=<CODE>`.
- Tapping an invite link automatically captures `?ref=` and attributes the invited friend upon sign-in without manual code entry.

## 4. Optional Live Video Broadcast (Agora)
- Add `AGORA_APP_ID` in AI Studio **Secrets** if you want multi-viewer Agora RTC video streaming alongside Firebase live rooms and chat.

## 5. GitHub Actions APK Build (`build-apk.yml`)
- The workflow checks for `app/google-services.json` in the repository first.
- If `app/google-services.json` is omitted when pushing to GitHub, either upload `app/google-services.json` directly into `app/` or add a GitHub Actions Secret named `GOOGLE_SERVICES_JSON` containing the contents of `app/google-services.json` (or `GOOGLE_SERVICES_JSON_BASE64`).

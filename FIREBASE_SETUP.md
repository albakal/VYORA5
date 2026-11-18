# VYORA Firebase Backend Setup

VYORA uses **Firebase Authentication (Google Sign-In)**, **Cloud Firestore** (named database `ai-studio-android-vyora-09b7e2be-39f1-49bc-910d-92eb9b4daa3f`), and **Firebase Storage** (`com-aistudio-tokshort-ap-f5c5c.firebasestorage.app`).

## 1. Automated Provisioning & Rules
- `app/google-services.json` is automatically provisioned for package `com.aistudio.vyora.rmnqwx` with debug SHA-1 `8B:0B:3F:38:64:F2:4C:D7:E1:DE:4A:4A:56:52:00:08:33:89:43:0D` and SHA-256 `F2:C2:F7:BF:A8:D2:7D:5D:A7:A9:D8:EF:5B:69:C7:44:E4:CC:6B:83:73:F0:45:94:56:B7:3B:BE:E5:6C:51:42`.
- Firestore rules (`/firestore.rules`) are deployed automatically to `ai-studio-android-vyora-09b7e2be-39f1-49bc-910d-92eb9b4daa3f`.

## 2. Enable Firebase Storage & Publish Storage Rules
1. Open [Firebase Console](https://console.firebase.google.com/project/com-aistudio-tokshort-ap-f5c5c/storage) for project `com-aistudio-tokshort-ap-f5c5c`.
2. Click **Get Started** on Storage if not already enabled.
3. Paste `/storage.rules` into the Storage **Rules** tab and click **Publish**.

## 3. Automatic Referral Invite Links
- Links use `https://vyora.app/invite?ref=<CODE>` and `vyora://invite?ref=<CODE>`.
- Tapping an invite link automatically captures `?ref=` and attributes the invited friend upon Google Sign-In.

## 4. Optional Live Video Broadcast (Agora)
- Add `AGORA_APP_ID` in AI Studio **Secrets** (or GitHub Repository Secrets) if you want multi-viewer Agora RTC video streaming alongside Firebase live rooms and chat.

## 5. GitHub Actions APK Build (`build-apk.yml`)
- The workflow checks for `app/google-services.json` in the repository first (or restores it from `GOOGLE_SERVICES_JSON` / `GOOGLE_SERVICES_JSON_BASE64` GitHub Secrets).
- On push to `main` or `master`, `.github/workflows/build-apk.yml` builds the APK and publishes it under GitHub Releases (`vyora-latest/VYORA.apk`).

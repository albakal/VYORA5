# دليل إعداد Supabase وبناء VYORA APK عبر GitHub Actions

يعمل تطبيق **VYORA** بالكامل على بنية **Supabase** السحابية:
- **المصادقة (Supabase Auth / GoTrue)**: تسجيل الدخول وإنشاء الحساب عبر البريد الإلكتروني وكلمة المرور (مع خيار تسجيل الدخول بحساب Google عند ضبط `SUPABASE_GOOGLE_WEB_CLIENT_ID`).
- **قاعدة البيانات والمنطق الذري (PostgreSQL + RLS + PL/pgSQL RPCs)**: إدارة ملفات المستخدمين، الفيديوهات، التعليقات، المتابعات، المحادثات، الإشعارات، البث المباشر، والمحفظة وطلبات السحب.
- **التخزين السحابي (Supabase Storage)**: رفع الفيديوهات، الصور المصغرة، الصور الشخصية، ووسائط الدردشة إلى الحاوية `vyora-media`.

---

## 1. تهيئة قاعدة بيانات Supabase والتخزين (`supabase/schema.sql`)

1. افتح لوحة تحكم مشروعك في **Supabase** ← **SQL Editor** ← **New Query**.
2. انسخ محتوى الملف `supabase/schema.sql` بالكامل وقم بتشغيله (**Run**).
   - ينشئ السكربت جميع الجداول (`vyora_profiles`, `vyora_videos_ledger`, `vyora_wallets`, `vyora_wallet_transactions`, إلخ) مع سياسات الحماية RLS.
   - ينشئ حاوية التخزين العامة `vyora-media` وسياسات الرفع والوصول الخاصة بها.
   - ينشئ دوال RPC الذرية لحساب المشاهدات والإعجابات والرصيد والسحب.

---

## 2. إعداد المفاتيح بأمان (AI Studio Secrets / GitHub Secrets)

أضف المتغيرات التالية في **Secrets Panel** داخل Google AI Studio، وكذلك في **Settings → Secrets and variables → Actions** داخل مستودع GitHub:

- `SUPABASE_URL`: رابط مشروعك في Supabase (مثال: `https://<project-ref>.supabase.co`)
- `SUPABASE_PUBLISHABLE_KEY`: المفتاح العام للعميل (Publishable / `anon` key المحمي بسياسات RLS)
- `SUPABASE_STORAGE_BUCKET`: اسم حاوية التخزين (الافتراضي: `vyora-media`)
- `SUPABASE_GOOGLE_WEB_CLIENT_ID`: *(اختياري)* معرّف Web OAuth Client ID إذا أردت تفعيل زر المتابعة بحساب Google

> ⚠️ **تنبيه أمني**: أي قيمة يتم تمريرها عبر `BuildConfig` تندمج داخل حزمة Android APK ويمكن استخراجها بالهندسة العكسية؛ لذلك استخدم **فقط** المفتاح العام المحمي بسياسات RLS (`SUPABASE_PUBLISHABLE_KEY`) داخل تطبيق الأندرويد، ولا تضع أبداً المفتاح السري (`SUPABASE_SECRET_KEY` / `service_role`) أو `AGORA_APP_CERTIFICATE` في `.env` أو داخل الكود.

---

## 3. تفعيل البث المباشر عبر Agora (اختياري عبر Edge Function)

إذا أردت تفعيل البث المرئي الحي عبر Agora:
```bash
supabase secrets set AGORA_APP_ID=YOUR_AGORA_APP_ID AGORA_APP_CERTIFICATE=YOUR_AGORA_CERT
supabase functions deploy get-agora-token
```

---

## 4. البناء التلقائي ورابط التحميل المباشر على GitHub (`albakal/VYORA4`)

مسار البناء `.github/workflows/build-apk.yml` جاهز بالكامل للبناء السحابي:
1. ادفع التغييرات إلى مستودع `https://github.com/albakal/VYORA4`.
2. سيعمل **GitHub Actions** تلقائياً على قبول تراخيص Android SDK، وتجهيز `.env` من GitHub Secrets، وتوقيع التطبيق وبناء `VYORA.apk`، ورفعه في Artifacts وGitHub Releases.
3. رابط التنزيل المباشر بعد اكتمال التشغيل بنجاح:
   `https://github.com/albakal/VYORA4/releases/latest/download/VYORA.apk`

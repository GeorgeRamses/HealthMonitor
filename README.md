# Health Monitor - Android App

تطبيق متابعة صحية متكامل لتتبع الأدوية والضغط والأعراض

**لوالدك الحاج رمسيس إبراهيم عويضة**

---

## ✨ الميزات الرئيسية

### 📱 لوحة التحكم (Dashboard)
- عرض ملخص يومي للصحة
- قراءة ضغط الدم الأخيرة
- نسبة الالتزام بالأدوية
- تنبيهات سريعة الأعراض
- إجراءات سريعة

### 💊 إدارة الأدوية
- قائمة كاملة بالأدوية وأوقاتها
- تتبع يومي للالتزام بالأدوية
- تنبيهات بمواعيد الأدوية
- إحصائيات الالتزام
- تاريخ الأدوية المتبقية

### 🔴 قياس ضغط الدم
- تسجيل القراءات (Systolic/Diastolic)
- تسجيل النبض
- ملاحظات إضافية
- عرض حالة الضغط (طبيعي/مرتفع/شديد)
- سجل تاريخي بالقراءات

### 🫁 تتبع الأعراض
- تسجيل الأعراض (كرشة نفس، تزييق، ألم صدر، إلخ)
- تحديد شدة الأعراض
- تسجيل استخدام البخاخ
- تسجيل التحسّن بعد البخاخ
- سجل كامل بالأعراض

### ⚙️ الإعدادات
- معلومات المريض
- إعدادات التنبيهات
- نسخ احتياطية البيانات
- مشاركة البيانات مع الطبيب

---

## 🛠️ التقنيات المستخدمة

### Frontend
- **Kotlin** + **Jetpack Compose** - UI declarative
- **Material 3** - Design System
- **Navigation Compose** - Navigation
- **Arabic Support** - دعم كامل للعربية

### Backend & Database
- **Supabase** - Database & Authentication
- **PostgreSQL** - Relational Database
- **Realtime** - Live Updates

### Architecture
- **MVVM** - Model-View-ViewModel
- **Clean Architecture** - فصل الطبقات
- **Repository Pattern** - إدارة البيانات
- **StateFlow** - State Management

### Libraries
- **Hilt** - Dependency Injection
- **Room** - Local Database (SQLite)
- **Coroutines** - Asynchronous Operations
- **Kotlin Serialization** - JSON Serialization

---

## 🚀 كيفية الاستخدام

### 1. إعداد Supabase

```bash
# 1. أنشئ حساب على Supabase (https://supabase.com)
# 2. أنشئ project جديد
# 3. انسخ الـ URL و API Key

# 4. أضف الـ credentials في SupabaseService.kt
fun createSupabaseInstance(): SupabaseClient {
    return createSupabaseClient(
        supabaseUrl = "YOUR_SUPABASE_URL",
        supabaseKey = "YOUR_SUPABASE_ANON_KEY"
    )
}

# 5. شغّل الـ SQL schema من supabase-schema.sql
```

### 2. بناء التطبيق

```bash
# Clone the repository
git clone <repo-url>
cd health-monitor

# Build the app
./gradlew build

# Run on emulator or device
./gradlew installDebug
```

### 3. استخدام التطبيق

1. **Dashboard** - عرض ملخص يومي
2. **Medications** - قائمة الأدوية وتتبع الالتزام
3. **Blood Pressure** - قياس وتسجيل الضغط
4. **Symptoms** - تسجيل الأعراض والبخاخ
5. **Settings** - الإعدادات والمعلومات

---

## 📊 الأدوية الحالية

```
1. Torseretic 20mg     - 08:00 (ضغط + مدرّ)
2. Specton 25mg        - 12:00 (مضاد حيوي)
3. Cardura 4mg         - 20:00 (ضغط)
4. Norvasc 5mg         - 08:30 (ضغط + قلب)
5. Forxiga 10mg        - 13:00 (السكر + قلب)
6. ELiquis 5mg         - 08:00 & 20:00 ⭐ (مميع دم - جلطة)
7. Tamsulin 0.4mg      - (البروستاتا)
8. Aldomet 250mg       - (ضغط)
9. Atorvastatin 20mg   - (الكوليسترول)
10. Augmentin 800mg    - (مضاد حيوي)
11. Diflucan 150mg     - (فطريات)
12. Daflon 500mg       - (الأوعية الدموية)
```

---

## 🎨 الواجهة والتصميم

- **Dark Theme** - سهل على العين، مناسب للاستخدام الطويل
- **Industrial Medical Aesthetic** - تصميم احترافي طبي
- **Arabic First** - دعم كامل للنصوص العربية
- **Responsive** - يعمل على جميع أحجام الشاشات

---

## 📱 متطلبات النظام

- **Android**: 7.0+ (API 24)
- **Memory**: 100 MB
- **Storage**: 50 MB
- **Internet**: مطلوب للمزامنة مع Supabase

---

## 🔐 الأمان والخصوصية

- ✅ التشفير في النقل (TLS)
- ✅ Row Level Security (RLS) في قاعدة البيانات
- ✅ بيانات محلية في SQLite
- ✅ مزامنة آمنة مع الخادم

---

## 📞 الدعم

في حالة أي مشاكل أو استفسارات:

1. تحقق من الـ Logs
2. تأكد من الاتصال بالإنترنت
3. تأكد من credentials Supabase

---

## 📝 الملاحظات

- **ELiquis** مهم جداً - لا تنسَ جرعات الجلطة
- قياس الضغط يومياً في نفس الوقت
- احفظ تقارير الأعراض والضغط للطبيب
- استخدم البخاخ عند اللزوم

---

**Version**: 1.0.0  
**Last Update**: April 2026  
**License**: Open Source

---

جعله الله في ميزان حسناتك، وشفاه الله وعافاه.

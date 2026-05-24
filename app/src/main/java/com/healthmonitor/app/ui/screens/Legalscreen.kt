package com.healthmonitor.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.healthmonitor.app.ui.design.*

// ─────────────────────────────────────────────────────────────────────────────
// Legal Screen — shows either Privacy Policy or Terms of Service
// Route: "legal/{type}" where type = "privacy" or "terms"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LegalScreen(
    type: String,   // "privacy" or "terms"
    navController: NavHostController
) {
    LegalDocumentContent(
        type = type,
        onClose = { navController.popBackStack() }
    )
}

@Composable
fun LegalDocumentDialog(
    type: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(HMSpacing.md),
            color = HMColor.BgBase,
            shape = RoundedCornerShape(HMRadius.lg)
        ) {
            LegalDocumentContent(type = type, onClose = onDismiss)
        }
    }
}

@Composable
private fun LegalDocumentContent(
    type: String,
    onClose: () -> Unit
) {
    val isPrivacy = type == "privacy"
    val title     = if (isPrivacy) "سياسة الخصوصية" else "شروط الاستخدام"
    val icon      = if (isPrivacy) Icons.Default.Security else Icons.Default.Gavel
    val accentColor = if (isPrivacy) HMColor.BlueBright else HMColor.GreenBright
    val accentBg    = if (isPrivacy) HMColor.BlueBg    else HMColor.GreenBg
    val accentBorder = if (isPrivacy) HMColor.BlueBorder else HMColor.GreenBorder

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HMColor.BgBase)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HMColor.BgSurface)
                .border(width = 0.5.dp, color = HMColor.BorderSubtle,
                    shape = RoundedCornerShape(bottomStart = HMRadius.md, bottomEnd = HMRadius.md))
                .padding(HMSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(accentColor.copy(alpha = 0.12f))
                            .border(1.dp, accentBorder, RoundedCornerShape(HMRadius.sm)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = HMColor.TextPrimary
                        )
                        Text(
                            "تطبيق Health Monitor — مايو 2026",
                            fontSize = 11.sp,
                            color = HMColor.TextSecondary
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "إغلاق", tint = HMColor.TextSecondary)
                }
            }
        }

        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(HMSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(HMSpacing.md)
        ) {
            Spacer(Modifier.height(HMSpacing.xs))

            if (isPrivacy) PrivacyPolicyContent(accentColor, accentBg, accentBorder)
            else TermsOfServiceContent(accentColor, accentBg, accentBorder)

            Spacer(Modifier.height(HMSpacing.xxxl))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Privacy Policy Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PrivacyPolicyContent(
    accentColor: Color,
    accentBg: Color,
    accentBorder: Color
) {
    LegalSection(number = "١", title = "مقدمة", accentColor = accentColor) {
        LegalParagraph("نحن في Health Monitor نلتزم بحماية خصوصيتك وبياناتك الصحية. تشرح هذه السياسة بشكل واضح ما نجمعه، وكيف نستخدمه، وكيف نحميه. باستخدامك التطبيق فأنت توافق على ما ورد في هذه السياسة.")
    }

    LegalSection(number = "٢", title = "البيانات التي يجمعها التطبيق", accentColor = accentColor) {
        LegalSubtitle("أ. البيانات الصحية الشخصية (محفوظة على جهازك فقط)")
        LegalParagraph("يقوم التطبيق بتخزين البيانات التالية محلياً على جهازك فقط ولا يتم رفعها إلى أي خادم خارجي:")
        LegalBullet("بيانات المريض: الاسم، العمر، الجنس، فصيلة الدم، الحالات الطبية")
        LegalBullet("قراءات ضغط الدم ودرجة الحرارة")
        LegalBullet("سجلات الأدوية والجرعات ومواعيد المنبهات")
        LegalBullet("الأعراض والتقارير المخبرية")
        LegalBullet("سجلات استخدام البخاخ")
        Spacer(Modifier.height(HMSpacing.sm))
        LegalHighlightCard(
            "جميع هذه البيانات مشفّرة بتشفير AES-256 باستخدام مفتاح محفوظ في Android Keystore ولا يمكن الوصول إليه من خارج جهازك.",
            accentColor = HMColor.GreenBright, accentBg = HMColor.GreenBg, accentBorder = HMColor.GreenBorder
        )
        Spacer(Modifier.height(HMSpacing.sm))
        LegalSubtitle("ب. بيانات تقنية مجهولة الهوية (بموافقتك فقط)")
        LegalParagraph("إذا وافقت على جمع تقارير الأعطال، يتم إرسال البيانات التالية إلى Firebase Crashlytics:")
        LegalBullet("نوع الجهاز وإصدار نظام Android")
        LegalBullet("سجل الأعطال التقني عند حدوث خطأ في التطبيق")
        LegalBullet("وقت حدوث العطل")
        LegalParagraph("لا تحتوي هذه البيانات على أي معلومات صحية شخصية. يمكنك سحب موافقتك في أي وقت من الإعدادات.")
        Spacer(Modifier.height(HMSpacing.sm))
        LegalSubtitle("ج. الصور المرسلة لتحليل الذكاء الاصطناعي")
        LegalParagraph("عند استخدام ميزة تحليل التقارير الطبية أو معلومات الدواء، يتم إرسال الصور أو النصوص التي تختارها أنت إلى خادم وسيط ثم إلى Google Gemini AI لأغراض التحليل فقط.")
    }

    LegalSection(number = "٣", title = "كيف نحمي بياناتك", accentColor = accentColor) {
        LegalBullet("جميع البيانات الصحية مشفّرة على الجهاز بـ AES-256 عبر SQLCipher")
        LegalBullet("مفتاح التشفير محفوظ في Android Keystore المدعوم بالأجهزة ولا يمكن استخراجه")
        LegalBullet("لا يوجد خادم مركزي يحفظ بياناتك الصحية")
        LegalBullet("اتصال التطبيق بالإنترنت يقتصر على خدمات الذكاء الاصطناعي فقط وبموافقتك")
    }

    LegalSection(number = "٤", title = "مشاركة البيانات مع أطراف ثالثة", accentColor = accentColor) {
        LegalParagraph("لا نبيع بياناتك ولا نشاركها مع أطراف ثالثة لأغراض تجارية. الاستثناء الوحيد:")
        LegalBullet("Google Firebase Crashlytics: بيانات تقنية مجهولة الهوية بموافقتك فقط")
        LegalBullet("Google Gemini AI عبر خادمنا الوسيط: الصور أو النصوص التي ترسلها أنت لتحليل التقارير")
    }

    LegalSection(number = "٥", title = "حقوقك", accentColor = accentColor) {
        LegalBullet("حق الوصول: يمكنك الاطلاع على جميع بياناتك من داخل التطبيق")
        LegalBullet("حق الحذف: يمكنك حذف أي بيانات أو حذف جميع البيانات بإلغاء تثبيت التطبيق")
        LegalBullet("حق سحب الموافقة: يمكنك إيقاف جمع بيانات الأعطال في أي وقت من الإعدادات")
        LegalBullet("حق التصدير: يمكنك تصدير تقريرك الطبي الكامل من شاشة الإعدادات")
    }

    LegalSection(number = "٦", title = "إخلاء مسؤولية الذكاء الاصطناعي", accentColor = accentColor) {
        LegalHighlightCard(
            "هذا البند بالغ الأهمية. يُرجى قراءته بعناية.",
            accentColor = HMColor.AmberBright, accentBg = HMColor.AmberBg, accentBorder = HMColor.AmberBorder
        )
        Spacer(Modifier.height(HMSpacing.sm))
        LegalParagraph("ميزات الذكاء الاصطناعي في التطبيق مدعومة بتقنية Google Gemini AI. هذه الميزات تُقدَّم للتوعية العامة فقط وتخضع للقيود التالية:")
        LegalBullet("نتائج الذكاء الاصطناعي ليست تشخيصاً طبياً ولا تُغني عن استشارة طبيب مختص")
        LegalBullet("قد تحتوي النتائج على أخطاء أو معلومات غير دقيقة")
        LegalBullet("لا نتحمل أي مسؤولية قانونية أو طبية عن قرارات تُبنى على نتائج الذكاء الاصطناعي")
        LegalBullet("تحليل التقارير يعتمد على جودة الصورة المرفوعة وقد لا يكون دقيقاً في جميع الحالات")
        LegalBullet("معلومات الأدوية للتوعية فقط — استشر صيدلانيك أو طبيبك دائماً قبل تعديل علاجك")
        Spacer(Modifier.height(HMSpacing.sm))
        LegalHighlightCard(
            "باستخدامك لميزات الذكاء الاصطناعي فأنت تُقرّ بأنك تستخدمها كأداة مساعدة للمعلومات فقط وليس كمرجع طبي نهائي، وتتحمل كامل المسؤولية عن أي قرار تتخذه بناءً على هذه المعلومات.",
            accentColor = HMColor.RedBright, accentBg = HMColor.RedBg, accentBorder = HMColor.RedBorder
        )
    }

    LegalSection(number = "٧", title = "التغييرات على هذه السياسة", accentColor = accentColor) {
        LegalParagraph("قد نحدّث هذه السياسة من وقت لآخر. سيتم إعلامك بأي تغييرات جوهرية عبر التطبيق. استمرارك في استخدام التطبيق بعد التحديث يعني موافقتك على السياسة الجديدة.")
    }

    LegalSection(number = "٨", title = "التواصل معنا", accentColor = accentColor) {
        LegalParagraph("إذا كان لديك أي استفسار حول هذه السياسة أو بياناتك، يُرجى التواصل معنا عبر صفحة التطبيق على Google Play.")
    }

    LegalFooter()
}

// ─────────────────────────────────────────────────────────────────────────────
// Terms of Service Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TermsOfServiceContent(
    accentColor: Color,
    accentBg: Color,
    accentBorder: Color
) {
    LegalSection(number = "١", title = "القبول والموافقة", accentColor = accentColor) {
        LegalParagraph("بتنزيلك أو تثبيتك أو استخدامك لتطبيق Health Monitor فأنت توافق على الالتزام بهذه الشروط. إذا كنت لا توافق على أي من هذه الشروط، يُرجى عدم استخدام التطبيق.")
    }

    LegalSection(number = "٢", title = "طبيعة التطبيق وحدوده", accentColor = accentColor) {
        LegalParagraph("Health Monitor هو تطبيق لمتابعة الصحة الشخصية مصمم لمساعدة المستخدمين على تتبع بياناتهم الصحية وتنظيم أدويتهم. يجب أن تفهم وتوافق على ما يلي:")
        LegalBullet("التطبيق أداة متابعة شخصية وليس أداة تشخيص طبي")
        LegalBullet("التطبيق لا يُقدِّم استشارات طبية ولا يحل محل الطبيب أو الصيدلاني")
        LegalBullet("لا يجب الاعتماد على التطبيق وحده في اتخاذ قرارات طبية حرجة")
        LegalBullet("التطبيق غير مخصص للحالات الطارئة — في الطوارئ اتصل بالإسعاف فوراً")
    }

    LegalSection(number = "٣", title = "إخلاء المسؤولية الطبية", accentColor = accentColor) {
        LegalHighlightCard(
            "هذا البند الأهم في الشروط. يُرجى قراءته بعناية تامة.",
            accentColor = HMColor.AmberBright, accentBg = HMColor.AmberBg, accentBorder = HMColor.AmberBorder
        )
        Spacer(Modifier.height(HMSpacing.sm))
        LegalSubtitle("أ. المحتوى الطبي العام")
        LegalParagraph("أي معلومات طبية تظهر في التطبيق هي للتوعية والإرشاد العام فقط. لا يتحمل مطورو التطبيق أي مسؤولية عن:")
        LegalBullet("أي ضرر جسدي أو صحي ناتج عن الاعتماد على معلومات التطبيق")
        LegalBullet("أي قرار طبي يتخذه المستخدم بناءً على نتائج التطبيق")
        LegalBullet("أي تأخير في طلب الرعاية الطبية الطارئة")
        Spacer(Modifier.height(HMSpacing.sm))
        LegalSubtitle("ب. نتائج الذكاء الاصطناعي — إخلاء مسؤولية خاص")
        LegalParagraph("ميزات الذكاء الاصطناعي تعمل بتقنية Google Gemini AI. فيما يخص هذه النتائج تحديداً:")
        LegalBullet("نتائج تحليل التقارير الطبية قد تحتوي على أخطاء في القراءة أو التفسير")
        LegalBullet("معلومات الأدوية المُولَّدة بالذكاء الاصطناعي قد لا تعكس أحدث الإرشادات الطبية")
        LegalBullet("الذكاء الاصطناعي قد يُخطئ في تحديد القيم الطبيعية لبعض التحاليل")
        LegalBullet("لا يجب استخدام نتائج الذكاء الاصطناعي لتعديل الجرعات أو إيقاف الأدوية دون استشارة طبيب")
        LegalBullet("مطورو التطبيق لا يتحملون أي مسؤولية قانونية أو مدنية أو جنائية عن أي ضرر ناتج عن استخدام نتائج الذكاء الاصطناعي")
        Spacer(Modifier.height(HMSpacing.sm))
        LegalHighlightCard(
            "باستخدامك لميزات الذكاء الاصطناعي فأنت تُقرّ صراحةً بأنك تستخدمها بمحض إرادتك وعلى مسؤوليتك الكاملة، وأن النتائج استرشادية فقط وليست طبية.",
            accentColor = HMColor.RedBright, accentBg = HMColor.RedBg, accentBorder = HMColor.RedBorder
        )
        Spacer(Modifier.height(HMSpacing.sm))
        LegalSubtitle("ج. تذكيرات الأدوية")
        LegalParagraph("نظام تذكيرات الأدوية في التطبيق أداة مساعدة فقط. مطورو التطبيق لا يتحملون أي مسؤولية عن:")
        LegalBullet("حالات نسيان جرعة نتيجة عدم عمل المنبه لأي سبب تقني")
        LegalBullet("التفاعلات الدوائية أو المضاعفات الناتجة عن الأدوية المسجلة في التطبيق")
        LegalBullet("أخطاء في إدخال الجرعات أو المواعيد من قِبل المستخدم")
    }

    LegalSection(number = "٤", title = "مسؤوليات المستخدم", accentColor = accentColor) {
        LegalParagraph("أنت كمستخدم مسؤول عن:")
        LegalBullet("إدخال بيانات صحيحة ودقيقة في التطبيق")
        LegalBullet("الاحتفاظ بنسخ احتياطية من بياناتك الصحية المهمة")
        LegalBullet("استشارة الطبيب لأي قرار طبي بصرف النظر عما يعرضه التطبيق")
        LegalBullet("عدم مشاركة الجهاز مع أشخاص غير موثوقين لحماية بياناتك الصحية")
        LegalBullet("الإبلاغ عن أي مشكلة في التطبيق عبر صفحة Google Play")
    }

    LegalSection(number = "٥", title = "الملكية الفكرية", accentColor = accentColor) {
        LegalParagraph("جميع حقوق الملكية الفكرية للتطبيق بما في ذلك الكود البرمجي وواجهة المستخدم والتصميم محفوظة للمطور. يُمنع:")
        LegalBullet("نسخ التطبيق أو توزيعه أو بيعه دون إذن مسبق")
        LegalBullet("عكس هندسة الكود البرمجي (Reverse Engineering)")
        LegalBullet("استخدام اسم أو شعار التطبيق دون إذن")
    }

    LegalSection(number = "٦", title = "التعديلات والإيقاف", accentColor = accentColor) {
        LegalParagraph("نحتفظ بالحق في:")
        LegalBullet("تعديل أو تحديث هذه الشروط في أي وقت مع إخطار المستخدمين")
        LegalBullet("إيقاف أو تعديل أي ميزة في التطبيق دون إشعار مسبق")
        LegalBullet("إيقاف التطبيق كلياً في حال الضرورة")
    }

    LegalSection(number = "٧", title = "القانون المنطبق", accentColor = accentColor) {
        LegalParagraph("تخضع هذه الشروط لقوانين جمهورية مصر العربية. أي نزاع ينشأ عن استخدام التطبيق يخضع للاختصاص القضائي المصري.")
    }

    LegalSection(number = "٨", title = "التواصل", accentColor = accentColor) {
        LegalParagraph("لأي استفسار أو شكوى تتعلق بهذه الشروط، يُرجى التواصل معنا عبر صفحة التطبيق على Google Play.")
    }

    LegalFooter()
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared building blocks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LegalSection(
    number: String,
    title: String,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(HMRadius.full))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(HMRadius.full)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    number,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = HMColor.TextPrimary
            )
        }
        Spacer(Modifier.height(HMSpacing.sm))
        // Section content indented
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HMSpacing.xl),
            content = content
        )
    }
}

@Composable
private fun LegalSubtitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = HMColor.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = HMSpacing.xs),
        style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Rtl),
        textAlign = TextAlign.Right
    )
}

@Composable
private fun LegalParagraph(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = HMColor.TextSecondary,
        lineHeight = 22.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = HMSpacing.xs),
        style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Rtl),
        textAlign = TextAlign.Right
    )
}

@Composable
private fun LegalBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = HMColor.TextSecondary,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
            style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Rtl),
            textAlign = TextAlign.Right
        )
        Spacer(Modifier.width(HMSpacing.sm))
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(HMRadius.full))
                .background(HMColor.TextSecondary.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun LegalHighlightCard(
    text: String,
    accentColor: Color,
    accentBg: Color,
    accentBorder: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HMRadius.sm))
            .background(accentBg)
            .border(1.dp, accentBorder, RoundedCornerShape(HMRadius.sm))
            .padding(HMSpacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = accentColor,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Rtl),
            textAlign = TextAlign.Right
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(HMRadius.full))
                .background(accentColor)
        )
    }
}

@Composable
private fun LegalFooter() {
    Spacer(Modifier.height(HMSpacing.md))
    HMDivider()
    Spacer(Modifier.height(HMSpacing.md))
    Text(
        "Health Monitor — جميع الحقوق محفوظة © 2026",
        fontSize = 11.sp,
        color = HMColor.TextDisabled,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Legal buttons — used in both SettingsScreen and ConsentDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LegalButtons(
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
    ) {
        HMPressable(onClick = onPrivacy, modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HMRadius.sm))
                    .background(HMColor.BlueBright.copy(alpha = 0.08f))
                    .border(1.dp, HMColor.BlueBright.copy(alpha = 0.3f), RoundedCornerShape(HMRadius.sm))
                    .padding(horizontal = HMSpacing.md, vertical = HMSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.xs)
            ) {
                Icon(
                    Icons.Default.Security, null,
                    tint = HMColor.BlueBright,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "سياسة الخصوصية",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = HMColor.BlueBright
                )
            }
        }
        HMPressable(onClick = onTerms, modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HMRadius.sm))
                    .background(HMColor.GreenBright.copy(alpha = 0.08f))
                    .border(1.dp, HMColor.GreenBright.copy(alpha = 0.3f), RoundedCornerShape(HMRadius.sm))
                    .padding(horizontal = HMSpacing.md, vertical = HMSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.xs)
            ) {
                Icon(
                    Icons.Default.Gavel, null,
                    tint = HMColor.GreenBright,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "شروط الاستخدام",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = HMColor.GreenBright
                )
            }
        }
    }
}

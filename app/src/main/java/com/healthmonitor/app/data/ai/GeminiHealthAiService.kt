package com.healthmonitor.app.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.healthmonitor.app.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale

// ─────────────────────────────────────────────────────────────────────────────
// Data models  (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

data class ExtractedMedicationInfo(
    val name: String?,
    val dosage: String?,
    val frequency: String?,
    val instructions: String?
)

data class MedicineInfoResult(
    val arabicName: String?,
    val category: String?,
    val primaryUses: String?,
    val commonSideEffects: String?,
    val importantWarnings: String?,
    val generalInstructions: String?,
    val disclaimer: String
)

data class LabReportItem(
    val testItem: String,
    val simpleDescription: String?,
    val result: String,
    val unit: String?,
    val referenceRange: String?,
    val status: String
)

data class StructuredLabReport(
    val reportName: String,
    val reportDate: String?,
    val items: List<LabReportItem>
)

// ─────────────────────────────────────────────────────────────────────────────
// Service
//
// FIX: The Gemini API key no longer lives in the APK.
//
// Old flow:  Android → Gemini API  (key embedded in BuildConfig)
// New flow:  Android → Cloudflare Worker proxy → Gemini API
//                      (key lives only in Cloudflare's secret store)
//
// The Android app only knows:
//   PROXY_BASE_URL  — the Worker URL  (not secret, fine to ship)
//   PROXY_APP_SECRET — a shared secret that prevents strangers from using
//                      your proxy  (set in local.properties, NOT committed)
//
// How to set up — add to local.properties:
//   PROXY_BASE_URL=https://your-worker.your-subdomain.workers.dev
//   PROXY_APP_SECRET=any-long-random-string-you-invented
// ─────────────────────────────────────────────────────────────────────────────

@Singleton
class GeminiHealthAiService @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis  = 120_000
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { _, response ->
                response.status.value >= 500 || response.status == HttpStatusCode.RequestTimeout
            }
            delayMillis { retry ->
                (1000L * (1L shl retry)).coerceAtMost(5000L)
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    suspend fun summarizeMedicalReportInArabic(
        reportText: String,
        reportImages: List<Bitmap> = emptyList()
    ): String {
        val systemInstruction = """
            أنت مساعد طبي متخصص في شرح التقارير الطبية للمرضى بأسلوب مبسط وواضح.
            
            قاعدة ذهبية لا تُكسر أبداً:
            - كل جملة تكتبها يجب أن تبدأ باسم الشيء الذي تتحدث عنه.
            - ممنوع تماماً كتابة جملة بدون ذكر اسم الفحص أو القيمة أو العضو أولاً.
            - مثال صحيح: "سكر الدم: 180 mg/dL — أعلى من الطبيعي (الطبيعي أقل من 100) ⚠️"
            - مثال خاطئ: "القيمة مرتفعة عن الطبيعي" — مرفوض لأنه لا يحدد ما الذي يُقاس.
            
            مبادئك الأساسية:
            ١. اشرح النتائج بلغة عربية بسيطة يفهمها غير المتخصصين.
            ٢. لكل نتيجة: اذكر اسمها أولاً ثم قيمتها ثم شرحها مع المدى الطبيعي.
            ٣. استخدم ✓ للقيم الطبيعية و ⚠️ لغير الطبيعية.
            ٤. لا تضع تشخيصًا نهائيًا ولا تقترح تغيير الجرعات.
            ٥. انصح دائمًا بمراجعة الطبيب للنتائج غير الطبيعية.
        """.trimIndent()

        val userPrompt = """
            يرجى تحليل وشرح هذا التقرير الطبي:
            
            $reportText
            
            قدّم التحليل بالتنسيق التالي بالضبط:
            
            ## ملخص التقرير
            (سطر أو سطرين يوضحان نوع التقرير والغرض منه)
            
            ## النتائج التفصيلية
            لكل قيمة أو نتيجة في التقرير، اكتب سطراً بالشكل:
            - **[اسم الفحص أو القيمة]**: [القيمة الفعلية] — [شرح مبسط بالعربية] [✓ أو ⚠️]
            
            ## القيم خارج النطاق الطبيعي
            - **[اسم القيمة]**: [القيمة] — [لماذا هي مهمة وما الذي قد تعنيه]
            
            ## التوصيات
            نصائح عملية مختصرة للمريض بناءً على نتائج التقرير.
        """.trimIndent()

        return generateContent(
            systemInstruction = systemInstruction,
            userPrompt        = userPrompt,
            images            = reportImages,
            mimeType          = "image/jpeg"
        )
    }

    suspend fun summarizeMedicalReportImageInArabic(reportImage: Bitmap): String {
        val systemInstruction = """
            أنت مساعد طبي متخصص في قراءة وتفسير صور التقارير الطبية ونتائج التحاليل.
            
            قاعدة ذهبية لا تُكسر أبداً:
            - كل جملة تكتبها يجب أن تبدأ باسم الشيء الذي تتحدث عنه.
            - ممنوع تماماً كتابة جملة بدون ذكر اسم العضو أو الفحص أو القيمة أو الإيجاد أولاً.
            
            مبادئك الأساسية:
            ١. اقرأ جميع القيم والأرقام والموجودات الظاهرة في الصورة بدقة.
            ٢. لكل نتيجة: اذكر اسمها أولاً ثم قيمتها ثم شرحها المبسط.
            ٣. حدد القيم الطبيعية بعلامة ✓ والغير طبيعية بعلامة ⚠️.
            ٤. لا تضع تشخيصًا نهائيًا ولا تقترح تغيير الجرعات.
            ٥. أنهِ دائمًا بتوصية بمراجعة الطبيب.
        """.trimIndent()

        val userPrompt = """
            اقرأ صورة التقرير الطبي هذه وقدّم التحليل بالتنسيق التالي بالضبط:
            
            ## ملخص التقرير
            ## النتائج التفصيلية
            ## القيم خارج النطاق الطبيعي
            ## التوصية
        """.trimIndent()

        return generateContent(
            systemInstruction = systemInstruction,
            userPrompt        = userPrompt,
            images            = listOf(reportImage),
            mimeType          = "image/jpeg"
        )
    }

    suspend fun getMedicineInfo(medicineName: String): MedicineInfoResult {
        val systemInstruction = """
            أنت صيدلاني محترف متخصص في تقديم معلومات الأدوية للمرضى.
            مهمتك: تقديم معلومات شاملة وموثوقة عن الأدوية بالعربية.
            القواعد الصارمة:
            - أرجع JSON فقط بدون أي نص إضافي أو markdown.
            - استخدم null فقط إذا كانت المعلومة غير متوفرة إطلاقًا.
            - لا تنصح بجرعات محددة.
        """.trimIndent()

        val userPrompt = """
            قدّم معلومات شاملة عن دواء: "$medicineName"
            أرجع JSON بهذا الهيكل الدقيق فقط:
            {
              "arabicName": "الاسم العربي أو الاسم العلمي للدواء",
              "category": "تصنيف الدواء",
              "primaryUses": "الاستخدامات الرئيسية للدواء",
              "commonSideEffects": "الأعراض الجانبية الشائعة",
              "importantWarnings": "أهم التحذيرات والموانع",
              "generalInstructions": "تعليمات عامة للاستخدام الآمن"
            }
        """.trimIndent()

        val response = generateContent(
            systemInstruction = systemInstruction,
            userPrompt        = userPrompt,
            expectJson        = true
        )

        val cleaned = response.stripCodeFence()
        val obj = runCatching { json.parseToJsonElement(cleaned).jsonObject }.getOrNull()

        return MedicineInfoResult(
            arabicName          = obj?.stringOrNull("arabicName"),
            category            = obj?.stringOrNull("category"),
            primaryUses         = obj?.stringOrNull("primaryUses"),
            commonSideEffects   = obj?.stringOrNull("commonSideEffects"),
            importantWarnings   = obj?.stringOrNull("importantWarnings"),
            generalInstructions = obj?.stringOrNull("generalInstructions"),
            disclaimer          = "⚠️ هذه المعلومات للتوعية العامة فقط. استشر طبيبك أو صيدلانيك دائمًا قبل تعديل علاجك."
        )
    }

    suspend fun extractLabReportFromImage(image: Bitmap): StructuredLabReport {
        val systemInstruction = """
        أنت نظام استخراج بيانات طبية دقيق متخصص في قراءة نتائج التحاليل المخبرية والتقارير التصويرية.
        القواعد الصارمة:
        - أرجع JSON فقط بدون أي نص إضافي أو markdown.
        - استخرج البيانات حرفياً كما هي — لا تقدِّر ولا تخترع قيماً.
        قواعد status: "High" → أعلى من المدى، "Low" → أقل، "Normal" → ضمن المدى.
        قواعد reportDate: استخرج التاريخ الفعلي بصيغة YYYY-MM-DD. إذا لم يوجد → null.
    """.trimIndent()

        val userPrompt = """
        اقرأ هذا التقرير الطبي واستخرج جميع بياناته:
        {
          "reportName": "اسم نوع التقرير",
          "reportDate": "YYYY-MM-DD أو null",
          "items": [
            {
              "testItem": "اسم المعلمة كما هو مكتوب",
              "simpleDescription": "وصف عربي مبسط أو null",
              "result": "القيمة الكاملة كما هي في التقرير",
              "unit": "وحدة القياس أو null",
              "referenceRange": "المدى المرجعي أو null",
              "status": "Normal أو High أو Low"
            }
          ]
        }
    """.trimIndent()

        val response = generateContent(
            systemInstruction = systemInstruction,
            userPrompt        = userPrompt,
            images            = listOf(image),
            mimeType          = "image/jpeg",
            expectJson        = true
        )

        val cleaned = response.stripCodeFence()
        val obj = runCatching { json.parseToJsonElement(cleaned).jsonObject }.getOrNull()
            ?: return StructuredLabReport(reportName = "تقرير طبي", reportDate = null, items = emptyList())

        val reportName = obj.stringOrNull("reportName") ?: "تقرير طبي"
        val reportDate = obj.stringOrNull("reportDate")

        val items = obj["items"]?.jsonArray?.mapNotNull { el ->
            val item     = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val testItem = item.stringOrNull("testItem") ?: return@mapNotNull null
            val result   = item.stringOrNull("result")   ?: return@mapNotNull null
            LabReportItem(
                testItem          = testItem,
                simpleDescription = item.stringOrNull("simpleDescription"),
                result            = result,
                unit              = item.stringOrNull("unit"),
                referenceRange    = item.stringOrNull("referenceRange"),
                status            = item.stringOrNull("status")
                    ?.takeIf { it in listOf("High", "Low", "Normal") } ?: "Normal"
            )
        } ?: emptyList()

        return StructuredLabReport(reportName = reportName, reportDate = reportDate, items = items)
    }

    // ── Core generation ───────────────────────────────────────────────────
    //
    // FIX: Calls PROXY_BASE_URL instead of Gemini directly.
    // The proxy URL and app secret come from BuildConfig (sourced from
    // local.properties which is gitignored — never committed to source control).

    private suspend fun generateContent(
        systemInstruction: String,
        userPrompt: String,
        image: Bitmap? = null,
        images: List<Bitmap> = emptyList(),
        mimeType: String? = null,
        expectJson: Boolean = false
    ): String = withContext(Dispatchers.IO) {

        val proxyBaseUrl = BuildConfig.PROXY_BASE_URL
        require(proxyBaseUrl.isNotBlank() && proxyBaseUrl != "YOUR_PROXY_URL") {
            "PROXY_BASE_URL غير مضبوط في local.properties."
        }

        val proxyAppSecret = BuildConfig.PROXY_APP_SECRET
        require(proxyAppSecret.isNotBlank()) {
            "PROXY_APP_SECRET غير مضبوط في local.properties."
        }

        val model    = BuildConfig.GEMINI_MODEL.ifBlank { DEFAULT_MODEL }
        // The Worker reads ?model= from the query string to pick the Gemini model
        val endpoint = "$proxyBaseUrl?model=$model"

        val allImages = listOfNotNull(image) + images
        val body = buildRequestBody(systemInstruction, userPrompt, allImages, mimeType, expectJson).toString()

        try {
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                // Send the app secret so the Worker can validate it
                header("X-App-Secret", proxyAppSecret)
                setBody(body)
            }
            val rawBody: String = response.body()
            if (response.status != HttpStatusCode.OK) {
                val errMsg = parseErrorMessage(rawBody)
                throw IllegalStateException(errMsg.ifBlank { "فشل طلب AI: ${response.status}" })
            }
            parseTextResponse(rawBody)
        } catch (e: ClientRequestException) {
            val errMsg = runCatching { parseErrorMessage(e.response.body()) }.getOrDefault("")
            throw IllegalStateException(errMsg.ifBlank { "فشل طلب AI: ${e.response.status}" }, e)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("خطأ في الشبكة: تأكد من اتصالك بالإنترنت وأعد المحاولة. (${e.message})", e)
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("خطأ في الاتصال بالخادم: ${e.message}", e)
        }
    }

    // ── Request builder (unchanged) ───────────────────────────────────────

    private fun buildRequestBody(
        systemInstruction: String,
        userPrompt: String,
        images: List<Bitmap> = emptyList(),
        mimeType: String?,
        expectJson: Boolean
    ): JsonObject = buildJsonObject {

        putJsonObject("systemInstruction") {
            putJsonArray("parts") {
                add(buildJsonObject { put("text", systemInstruction) })
            }
        }

        putJsonArray("contents") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", userPrompt) })
                    if (images.isNotEmpty() && mimeType != null) {
                        images.forEach { image ->
                            add(buildJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", mimeType)
                                    put("data", image.toBase64Jpeg())
                                }
                            })
                        }
                    }
                }
            })
        }

        putJsonObject("generationConfig") {
            put("temperature", if (expectJson) 0.1 else 0.4)
            put("topK", 40)
            put("topP", 0.95)
            put("maxOutputTokens", 8192)
            put("responseMimeType", if (expectJson) "application/json" else "text/plain")
        }

        putJsonArray("safetySettings") {
            listOf(
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT"
            ).forEach { category ->
                add(buildJsonObject {
                    put("category", category)
                    put("threshold", "BLOCK_NONE")
                })
            }
        }
    }

    // ── Response parsing (unchanged) ──────────────────────────────────────

    private fun parseTextResponse(raw: String): String {
        val root = json.parseToJsonElement(raw).jsonObject

        root["error"]?.jsonObject?.let { err ->
            throw IllegalStateException(
                err["message"]?.jsonPrimitive?.contentOrNull ?: "خطأ غير معروف من الخادم."
            )
        }

        root["promptFeedback"]?.jsonObject
            ?.get("blockReason")?.jsonPrimitive?.contentOrNull
            ?.let { reason -> throw IllegalStateException("حُجب الطلب: $reason") }

        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("لم يرجع الخادم أي نتائج.")

        val finishReason = candidate["finishReason"]?.jsonPrimitive?.contentOrNull

        val text = candidate["content"]
            ?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("\n")?.trim()
            .orEmpty()

        if (text.isNotBlank()) {
            return if (finishReason == "MAX_TOKENS") {
                "$text\n\n---\n⚠️ **ملاحظة:** التقرير طويل جداً وتم اقتطاع جزء منه."
            } else text
        }

        throw IllegalStateException(
            when (finishReason) {
                "SAFETY"    -> "حُجبت الاستجابة لأسباب أمان."
                "RECITATION"-> "حُجبت الاستجابة لأسباب حقوق الملكية."
                "MAX_TOKENS"-> "تجاوز الرد الحد الأقصى ولم يُرجع أي نص."
                else        -> "أرجع الخادم ردًّا فارغًا."
            }
        )
    }

    private fun parseErrorMessage(raw: String): String =
        runCatching {
            json.parseToJsonElement(raw)
                .jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
                .orEmpty()
        }.getOrDefault("")

    private fun Bitmap.toBase64Jpeg(quality: Int = 75): String {
        val compressedBitmap = if (width > 2048 || height > 2048) {
            val scale    = minOf(2048f / width, 2048f / height)
            this.scale((width * scale).toInt(), (height * scale).toInt())
        } else this
        val out = ByteArrayOutputStream()
        compressedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = this[key] ?: return null
        return when {
            element is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
            element is JsonArray     -> element.mapNotNull { it.jsonPrimitive.contentOrNull }
                .joinToString("\n").takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun String.stripCodeFence(): String =
        trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    companion object {
        private const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
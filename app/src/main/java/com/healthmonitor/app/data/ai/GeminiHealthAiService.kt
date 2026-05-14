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

// ─────────────────────────────────────────────────────────────────────────────
// Data models
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
    /** Plain Arabic description of what this test measures, e.g. "كريات الدم الحمراء" */
    val simpleDescription: String?,
    /** The actual numeric or descriptive value exactly as printed, including measurements */
    val result: String,
    val unit: String?,
    val referenceRange: String?,
    /** "Normal" | "High" | "Low" */
    val status: String
)

data class StructuredLabReport(
    val reportName: String,
    /** ISO date "YYYY-MM-DD" extracted from the document */
    val reportDate: String?,
    val items: List<LabReportItem>
)

// ─────────────────────────────────────────────────────────────────────────────
// Service
// ─────────────────────────────────────────────────────────────────────────────

@Singleton
class GeminiHealthAiService @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000  // Increased to 3 minutes for large images
            connectTimeoutMillis = 30_000   // Increased to 30 seconds
            socketTimeoutMillis = 120_000   // Increased to 2 minutes
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { request, response ->
                // Retry on network errors and 5xx server errors
                response.status.value >= 500 || response.status == HttpStatusCode.RequestTimeout
            }
            delayMillis { retry ->
                // Exponential backoff: 1s, 2s, 4s
                (1000L * (1L shl retry)).coerceAtMost(5000L)
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Summarizes a medical report text and/or images in simplified Arabic for a patient.
     */
    suspend fun summarizeMedicalReportInArabic(reportText: String, reportImages: List<Bitmap> = emptyList()): String {
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
            
            أمثلة:
            - **هيموجلوبين الدم (HGB)**: 11.2 g/dL — أقل من الطبيعي (الطبيعي 12–16 g/dL) ⚠️
            - **سكر الدم الصائم**: 92 mg/dL — ضمن المعدل الطبيعي (70–100 mg/dL) ✓
            - **وظائف الكلى (Creatinine)**: 1.8 mg/dL — مرتفع قليلاً (الطبيعي 0.6–1.2) ⚠️
            
            ## القيم خارج النطاق الطبيعي
            - **[اسم القيمة]**: [القيمة] — [لماذا هي مهمة وما الذي قد تعنيه]
            
            ## التوصيات
            نصائح عملية مختصرة للمريض بناءً على نتائج التقرير.
        """.trimIndent()

        return generateContent(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            images = reportImages,
            mimeType = "image/jpeg"
        )
    }

    /**
     * Reads and summarizes a medical report image in simplified Arabic.
     */
    suspend fun summarizeMedicalReportImageInArabic(reportImage: Bitmap): String {
        val systemInstruction = """
            أنت مساعد طبي متخصص في قراءة وتفسير صور التقارير الطبية ونتائج التحاليل.
            
            قاعدة ذهبية لا تُكسر أبداً:
            - كل جملة تكتبها يجب أن تبدأ باسم الشيء الذي تتحدث عنه.
            - ممنوع تماماً كتابة جملة بدون ذكر اسم العضو أو الفحص أو القيمة أو الإيجاد أولاً.
            - مثال صحيح: "حصوة الكلية اليمنى: يبلغ حجمها 10 ملم وهي خارج النطاق الطبيعي ⚠️"
            - مثال خاطئ: "يبلغ حجمها 10 ملم" — هذا مرفوض لأنه لا يوضح ما الذي يُقاس.
            
            مبادئك الأساسية:
            ١. اقرأ جميع القيم والأرقام والموجودات الظاهرة في الصورة بدقة.
            ٢. لكل نتيجة: اذكر اسمها أولاً ثم قيمتها ثم شرحها المبسط.
            ٣. حدد القيم الطبيعية بعلامة ✓ والغير طبيعية بعلامة ⚠️.
            ٤. وضّح المدى الطبيعي لكل قيمة إن أمكن.
            ٥. لا تضع تشخيصًا نهائيًا ولا تقترح تغيير الجرعات.
            ٦. أنهِ دائمًا بتوصية بمراجعة الطبيب.
        """.trimIndent()

        val userPrompt = """
            اقرأ صورة التقرير الطبي هذه وقدّم التحليل بالتنسيق التالي بالضبط:
            
            ## ملخص التقرير
            (سطر أو سطرين يوضحان نوع التقرير والغرض منه)
            
            ## النتائج التفصيلية
            لكل نتيجة أو قيمة أو موجود في التقرير، اكتب سطراً بالشكل:
            - **[اسم الفحص أو العضو أو القيمة]**: [القيمة الفعلية] — [شرح مبسط] [✓ أو ⚠️]
            
            أمثلة على الشكل الصحيح:
            - **هيموجلوبين الدم**: 11.2 g/dL — أقل من الطبيعي قليلاً (الطبيعي 12–16) ⚠️
            - **الكلية اليمنى**: حجمها طبيعي 10×5 سم — لا توجد حصوات ✓
            - **حصوة الحالب الأيسر**: 8 ملم — تحتاج متابعة طبية ⚠️
            - **سكر الدم الصائم**: 95 mg/dL — ضمن المعدل الطبيعي (70–100) ✓
            
            ## القيم خارج النطاق الطبيعي
            اذكر فقط ما يحتاج انتباهاً، مع ذكر اسمه وسبب الأهمية:
            - **[اسم القيمة]**: [القيمة] — [لماذا هي مهمة وما الذي قد تعنيه]
            
            ## التوصية
            نصيحة عملية مختصرة للمريض بناءً على ما وجدته في التقرير.
            
            تنبيه مهم: لا تكتب أي جملة بدون ذكر اسم الشيء الذي تتحدث عنه في بدايتها.
        """.trimIndent()

        return generateContent(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            images = listOf(reportImage),
            mimeType = "image/jpeg"
        )
    }

    /**
     * Returns comprehensive medicine information in Arabic for a given medicine name.
     * Returns a structured [MedicineInfoResult].
     */
    suspend fun getMedicineInfo(medicineName: String): MedicineInfoResult {
        val systemInstruction = """
            أنت صيدلاني محترف متخصص في تقديم معلومات الأدوية للمرضى.
            مهمتك: تقديم معلومات شاملة وموثوقة عن الأدوية بالعربية.
            
            القواعد الصارمة:
            - أرجع JSON فقط بدون أي نص إضافي أو markdown.
            - استخدم null فقط إذا كانت المعلومة غير متوفرة إطلاقًا.
            - كن دقيقًا وعلميًا مع البساطة في اللغة.
            - لا تنصح بجرعات محددة — هذا دور الطبيب.
        """.trimIndent()

        val userPrompt = """
            قدّم معلومات شاملة عن دواء: "$medicineName"
            
            أرجع JSON بهذا الهيكل الدقيق فقط:
            {
              "arabicName": "الاسم العربي أو الاسم العلمي للدواء",
              "category": "تصنيف الدواء (مثال: مضاد للتخثر، مضاد حيوي...)",
              "primaryUses": "الاستخدامات الرئيسية للدواء (نقاط مختصرة)",
              "commonSideEffects": "الأعراض الجانبية الشائعة (نقاط مختصرة)",
              "importantWarnings": "أهم التحذيرات والموانع (نقاط مختصرة)",
              "generalInstructions": "تعليمات عامة للاستخدام الآمن"
            }
        """.trimIndent()

        val response = generateContent(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            expectJson = true
        )

        val cleaned = response.stripCodeFence()
        val obj = runCatching { json.parseToJsonElement(cleaned).jsonObject }.getOrNull()

        return MedicineInfoResult(
            arabicName = obj?.stringOrNull("arabicName"),
            category = obj?.stringOrNull("category"),
            primaryUses = obj?.stringOrNull("primaryUses"),
            commonSideEffects = obj?.stringOrNull("commonSideEffects"),
            importantWarnings = obj?.stringOrNull("importantWarnings"),
            generalInstructions = obj?.stringOrNull("generalInstructions"),
            disclaimer = "⚠️ هذه المعلومات للتوعية العامة فقط. استشر طبيبك أو صيدلانيك دائمًا قبل تعديل علاجك."
        )
    }

    suspend fun extractLabReportFromImage(image: Bitmap): StructuredLabReport {

        val systemInstruction = """
        أنت نظام استخراج بيانات طبية دقيق متخصص في قراءة نتائج التحاليل المخبرية والتقارير التصويرية.
        
        القواعد الصارمة:
        - أرجع JSON فقط بدون أي نص إضافي أو markdown.
        - استخرج البيانات حرفياً كما هي — لا تقدِّر ولا تخترع قيماً.
        - استخرج القيمة الكاملة دائماً بما فيها الأرقام والقياسات (مثال: "56 cc" وليس "تضخم فقط").
        
        قواعد simpleDescription:
        - اكتب وصفاً عربياً بسيطاً لا يتجاوز 6 كلمات يشرح ما يقيسه هذا الفحص.
        - أمثلة صحيحة:
            "CBC"        → "صورة الدم الكاملة"
            "HGB"        → "هيموجلوبين (بروتين حمل الأكسجين)"
            "Creatinine" → "وظائف الكلى"
            "PSA"        → "بروتين البروستاتا"
            "Prostate Volume" → "حجم غدة البروستاتا"
            "Post Void Residual" → "البول المتبقي بعد التبول"
        - إذا كان الاسم واضحاً للمريض العادي يمكن تركه null.
        
        قواعد result:
        - يجب أن يحتوي على القيمة الكاملة كما تظهر في التقرير.
        - للتقارير التصويرية (أشعة، سونار، MRI): اذكر القيمة العددية أو الوصف الكامل.
          مثال: "56 cc" أو "طبيعي 10×5 سم" أو "≤ 15 مل" — لا تقل فقط "مرتفع" أو "تضخم".
        - إذا لم توجد قيمة رقمية فاكتب الوصف النصي الكامل.
        
        قواعد status:
        - "High"   → النتيجة أعلى من الحد الأعلى للمدى الطبيعي
        - "Low"    → النتيجة أقل من الحد الأدنى
        - "Normal" → ضمن المدى، أو لا يوجد مدى مرجعي قابل للمقارنة
        
        قواعد reportDate:
        - استخرج التاريخ الفعلي من الوثيقة (Collection Date / Report Date / تاريخ الفحص).
        - الصيغة المطلوبة: YYYY-MM-DD. إذا لم يوجد → null.
    """.trimIndent()

        val userPrompt = """
        اقرأ هذا التقرير الطبي واستخرج جميع بياناته بالتنسيق التالي حرفياً:
        
        {
          "reportName": "اسم نوع التقرير بالإنجليزية أو العربية (مثال: Complete Blood Count، Lipid Profile، Prostate Ultrasound)",
          "reportDate": "YYYY-MM-DD أو null",
          "items": [
            {
              "testItem": "اسم المعلمة أو الفحص كما هو مكتوب في التقرير",
              "simpleDescription": "وصف عربي مبسط لما يقيسه هذا الفحص (أقل من 7 كلمات) أو null",
              "result": "القيمة الكاملة مع أي وحدة أو وصف كما هو في التقرير",
              "unit": "وحدة القياس المنفصلة إن وجدت أو null",
              "referenceRange": "المدى المرجعي كنص أو null",
              "status": "Normal أو High أو Low"
            }
          ]
        }
        
        تعليمات خاصة بالتقارير التصويرية (سونار، أشعة، MRI):
        - كل موجود أو قياس = عنصر منفصل في items.
        - يجب أن تظهر القيمة العددية في result (مثال: "56 cc" و"46 مل" وليس "تضخم" فقط).
        - إذا ذُكر قياسان (مثل الكلية: 11×5 سم) فاكتبهما معاً في result.
        
        لا تحذف أي معلمة أو قياس موجود في التقرير.
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
            val item = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
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

    private suspend fun generateContent(
        systemInstruction: String,
        userPrompt: String,
        image: Bitmap? = null,
        images: List<Bitmap> = emptyList(),
        mimeType: String? = null,
        expectJson: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        require(apiKey.isNotBlank() && apiKey != "YOUR_GEMINI_API_KEY") {
            "GEMINI_API_KEY غير مضبوط في BuildConfig."
        }

        val model = BuildConfig.GEMINI_MODEL.ifBlank { DEFAULT_MODEL }
        val endpoint = "$BASE_URL/models/$model:generateContent?key=$apiKey"

        // Combine single image and images list for backward compatibility
        val allImages = listOfNotNull(image) + images
        val body = buildRequestBody(systemInstruction, userPrompt, allImages, mimeType, expectJson).toString()

        try {
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val rawBody: String = response.body()
            if (response.status != HttpStatusCode.OK) {
                val errMsg = parseErrorMessage(rawBody)
                throw IllegalStateException(errMsg.ifBlank { "فشل طلب Gemini: ${response.status}" })
            }
            parseTextResponse(rawBody)
        } catch (e: ClientRequestException) {
            val errMsg = runCatching {
                parseErrorMessage(e.response.body())
            }.getOrDefault("")
            throw IllegalStateException(
                errMsg.ifBlank { "فشل طلب Gemini: ${e.response.status}" }, e
            )
        } catch (e: java.io.IOException) {
            // Handle network errors like "unexpected end of stream"
            throw IllegalStateException("خطأ في الشبكة: تأكد من اتصالك بالإنترنت وأعد المحاولة. (${e.message})", e)
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("خطأ في الاتصال بـ Gemini: ${e.message}", e)
        }
    }

    // ── Request builder ───────────────────────────────────────────────────

    private fun buildRequestBody(
        systemInstruction: String,
        userPrompt: String,
        images: List<Bitmap> = emptyList(),
        mimeType: String?,
        expectJson: Boolean
    ): JsonObject = buildJsonObject {

        // System instruction (separate top-level field — supported by Gemini 1.5+)
        putJsonObject("systemInstruction") {
            putJsonArray("parts") {
                add(buildJsonObject { put("text", systemInstruction) })
            }
        }

        // User turn with optional images
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

        // Generation config
        putJsonObject("generationConfig") {
            put("temperature", if (expectJson) 0.1 else 0.4)
            put("topK", 40)
            put("topP", 0.95)
            put("maxOutputTokens", 8192)
            if (expectJson) {
                put("responseMimeType", "application/json")
            } else {
                put("responseMimeType", "text/plain")
            }
        }

        // Safety settings — keep defaults but be explicit
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

    // ── Response parsing ──────────────────────────────────────────────────

    private fun parseTextResponse(raw: String): String {
        val root = json.parseToJsonElement(raw).jsonObject

        // Explicit error object
        root["error"]?.jsonObject?.let { err ->
            throw IllegalStateException(
                err["message"]?.jsonPrimitive?.contentOrNull ?: "Gemini أرجع خطأ غير معروف."
            )
        }

        // Prompt-level block
        root["promptFeedback"]?.jsonObject
            ?.get("blockReason")?.jsonPrimitive?.contentOrNull
            ?.let { reason -> throw IllegalStateException("حُجب الطلب من Gemini: $reason") }

        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("لم يرجع Gemini أي نتائج.")

        // Candidate-level finish reason
        val finishReason = candidate["finishReason"]?.jsonPrimitive?.contentOrNull

        val text = candidate["content"]
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("\n")
            ?.trim()
            .orEmpty()

        // If we have text, return it — even if truncated
        if (text.isNotBlank()) {
            return if (finishReason == "MAX_TOKENS") {
                "$text\n\n---\n⚠️ **ملاحظة:** التقرير طويل جداً وتم اقتطاع جزء منه. يمكنك تقسيم التقرير إلى صور أصغر للحصول على تحليل أكثر اكتمالاً."
            } else {
                text
            }
        }

        throw IllegalStateException(
            when (finishReason) {
                "SAFETY" -> "حُجبت الاستجابة لأسباب أمان."
                "RECITATION" -> "حُجبت الاستجابة لأسباب حقوق الملكية."
                "MAX_TOKENS" -> "تجاوز الرد الحد الأقصى للطول ولم يُرجع أي نص."
                else -> "أرجع Gemini ردًّا فارغًا."
            }
        )
    }

    private fun parseErrorMessage(raw: String): String =
        runCatching {
            json.parseToJsonElement(raw)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        }.getOrDefault("")

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun Bitmap.toBase64Jpeg(quality: Int = 75): String {
        // Compress image if it's too large to reduce network issues
        val compressedBitmap = if (width > 2048 || height > 2048) {
            val scale = minOf(2048f / width, 2048f / height)
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
        } else {
            this
        }

        val out = ByteArrayOutputStream()
        compressedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    //    private fun JsonObject.stringOrNull(key: String): String? =
//        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.stringOrNull(key: String): String? {
        val element = this[key] ?: return null

        return when {
            // لو القيمة نص عادي (الوضع الطبيعي)
            element is JsonPrimitive -> {
                element.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
            }
            // لو الموديل بعت قائمة (Array) وده اللي بيعمل المشكلة
            element is JsonArray -> {
                element.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .joinToString("\n") // هيحول القائمة لنص سطور تحت بعض
                    .takeIf { it.isNotBlank() }
            }

            else -> null
        }
    }

    private fun String.stripCodeFence(): String =
        trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

    companion object {
        private const val DEFAULT_MODEL = "gemini-2.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}
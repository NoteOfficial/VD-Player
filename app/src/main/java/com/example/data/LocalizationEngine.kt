package com.example.data

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

enum class StudioLanguage(
    val code: String,
    val displayName: String,
    val isRtl: Boolean,
    val locale: Locale
) {
    ENGLISH("en", "English", false, Locale.ENGLISH),
    BENGALI("bn", "বাংলা (Bengali)", false, Locale("bn", "BD")),
    ARABIC("ar", "العربية (Arabic)", true, Locale("ar")),
    CHINESE("zh", "简体中文 (Chinese)", false, Locale.SIMPLIFIED_CHINESE),
    URDU("ur", "اردو (Urdu)", true, Locale("ur", "PK"));

    companion object {
        fun fromCode(code: String): StudioLanguage = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class LocalizedStrings(
    val navHome: String,
    val navProject: String,
    val navLiveCoding: String,
    val navApkTool: String,
    val navProfile: String,
    val newProject: String,
    val importZip: String,
    val quickActions: String,
    val recentProjects: String,
    val settings: String,
    val buildCenter: String,
    val toolchain: String,
    val tutorials: String,
    val assetStudio: String,
    val utilities: String,
    val testCenter: String,
    val security: String,
    val backupRestore: String,
    val myApplications: String,
    val signingKeys: String,
    val gitAndCi: String,
    val aboutLabel: String,
    val searchPlaceholder: String,
    val backLabel: String,
    val cancelLabel: String,
    val confirmLabel: String,
    val saveLabel: String,
    val deleteLabel: String,
    val resetLabel: String,
    val applyLabel: String,
    val openLabel: String,
    val enabledLabel: String,
    val disabledLabel: String,
    val noProjectsTitle: String,
    val noProjectsDesc: String,
    val noBackupsTitle: String,
    val noBackupsDesc: String,
    val noBuildsMessage: String,
    val errorRetry: String,
    val errorDiagnostics: String,
    val stagePreparing: String,
    val stageScanning: String,
    val stageValidating: String,
    val stageCompiling: String,
    val stagePackaging: String,
    val stageSigning: String,
    val stageVerifying: String,
    val stageCompleted: String,
    val stageFailed: String,
    val stageCancelled: String,
    val tutorialsHeader: String,
    val tutorialsCheckpoints: String,
    val tutorialsResumeNext: String,
    val tutorialsOpenModule: String,
    val tutorialsExpectedResult: String,
    val tutorialsCommonError: String,
    val tutorialsCheckpointQuestion: String,
    val tutorialsSeriesAll: String,
    val tutorialsBookmark: String,
    val tutorialsSaved: String,
    val tutorialsPrerequisites: String,
    val tutorialsScopeNote: String,
    val exitConfirmTitle: String,
    val exitConfirmBody: String,
    val exitConfirmAction: String
) {
    fun asMap(): Map<String, String> = linkedMapOf(
        "navHome" to navHome,
        "navProject" to navProject,
        "navLiveCoding" to navLiveCoding,
        "navApkTool" to navApkTool,
        "navProfile" to navProfile,
        "newProject" to newProject,
        "importZip" to importZip,
        "quickActions" to quickActions,
        "recentProjects" to recentProjects,
        "settings" to settings,
        "buildCenter" to buildCenter,
        "toolchain" to toolchain,
        "tutorials" to tutorials,
        "assetStudio" to assetStudio,
        "utilities" to utilities,
        "testCenter" to testCenter,
        "security" to security,
        "backupRestore" to backupRestore,
        "myApplications" to myApplications,
        "signingKeys" to signingKeys,
        "gitAndCi" to gitAndCi,
        "aboutLabel" to aboutLabel,
        "searchPlaceholder" to searchPlaceholder,
        "backLabel" to backLabel,
        "cancelLabel" to cancelLabel,
        "confirmLabel" to confirmLabel,
        "saveLabel" to saveLabel,
        "deleteLabel" to deleteLabel,
        "resetLabel" to resetLabel,
        "applyLabel" to applyLabel,
        "openLabel" to openLabel,
        "enabledLabel" to enabledLabel,
        "disabledLabel" to disabledLabel,
        "noProjectsTitle" to noProjectsTitle,
        "noProjectsDesc" to noProjectsDesc,
        "noBackupsTitle" to noBackupsTitle,
        "noBackupsDesc" to noBackupsDesc,
        "noBuildsMessage" to noBuildsMessage,
        "errorRetry" to errorRetry,
        "errorDiagnostics" to errorDiagnostics,
        "stagePreparing" to stagePreparing,
        "stageScanning" to stageScanning,
        "stageValidating" to stageValidating,
        "stageCompiling" to stageCompiling,
        "stagePackaging" to stagePackaging,
        "stageSigning" to stageSigning,
        "stageVerifying" to stageVerifying,
        "stageCompleted" to stageCompleted,
        "stageFailed" to stageFailed,
        "stageCancelled" to stageCancelled,
        "tutorialsHeader" to tutorialsHeader,
        "tutorialsCheckpoints" to tutorialsCheckpoints,
        "tutorialsResumeNext" to tutorialsResumeNext,
        "tutorialsOpenModule" to tutorialsOpenModule,
        "tutorialsExpectedResult" to tutorialsExpectedResult,
        "tutorialsCommonError" to tutorialsCommonError,
        "tutorialsCheckpointQuestion" to tutorialsCheckpointQuestion,
        "tutorialsSeriesAll" to tutorialsSeriesAll,
        "tutorialsBookmark" to tutorialsBookmark,
        "tutorialsSaved" to tutorialsSaved,
        "tutorialsPrerequisites" to tutorialsPrerequisites,
        "tutorialsScopeNote" to tutorialsScopeNote,
        "exitConfirmTitle" to exitConfirmTitle,
        "exitConfirmBody" to exitConfirmBody,
        "exitConfirmAction" to exitConfirmAction
    )
}

data class LocalizationAuditReport(
    val language: StudioLanguage,
    val totalKeys: Int,
    val translatedKeys: Int,
    val missingOrBlankKeys: List<String>
) {
    val coveragePercent: Int
        get() = if (totalKeys == 0) 100 else (translatedKeys * 100) / totalKeys
}

object LocalizationEngine {
    fun stringsFor(code: String): LocalizedStrings = when (StudioLanguage.fromCode(code)) {
        StudioLanguage.ENGLISH -> LocalizedStrings(
            navHome = "Home",
            navProject = "Project",
            navLiveCoding = "Live Coding",
            navApkTool = "APK Tool",
            navProfile = "Profile",
            newProject = "New Project",
            importZip = "Import ZIP",
            quickActions = "Quick Actions",
            recentProjects = "Recent Projects",
            settings = "Settings",
            buildCenter = "Build Center",
            toolchain = "Toolchain",
            tutorials = "Tutorials",
            assetStudio = "Asset Studio",
            utilities = "Utilities",
            testCenter = "Test Center",
            security = "Privacy & Security",
            backupRestore = "Backup & Restore",
            myApplications = "My Applications",
            signingKeys = "Signing Keys",
            gitAndCi = "Git & GitHub",
            aboutLabel = "About",
            searchPlaceholder = "Search...",
            backLabel = "Back",
            cancelLabel = "Cancel",
            confirmLabel = "Confirm",
            saveLabel = "Save",
            deleteLabel = "Delete",
            resetLabel = "Reset",
            applyLabel = "Apply",
            openLabel = "Open",
            enabledLabel = "Enabled",
            disabledLabel = "Disabled",
            noProjectsTitle = "No Projects Yet",
            noProjectsDesc = "Create a starter project or import a ZIP archive to begin editing and building.",
            noBackupsTitle = "No Snapshots Created",
            noBackupsDesc = "Create a workspace snapshot to enable diffing, SHA-256 checks, and rollback recovery.",
            noBuildsMessage = "No builds recorded yet. Run Quick Build or open Build Center after creating a project.",
            errorRetry = "Retry",
            errorDiagnostics = "Diagnostics",
            stagePreparing = "Preparing",
            stageScanning = "Scanning",
            stageValidating = "Validating",
            stageCompiling = "Compiling",
            stagePackaging = "Packaging",
            stageSigning = "Signing",
            stageVerifying = "Verifying",
            stageCompleted = "Completed",
            stageFailed = "Failed",
            stageCancelled = "Cancelled",
            tutorialsHeader = "Tutorials & Guided Labs",
            tutorialsCheckpoints = "checkpoints completed",
            tutorialsResumeNext = "Resume Next Lesson",
            tutorialsOpenModule = "Open Module",
            tutorialsExpectedResult = "Expected result",
            tutorialsCommonError = "Common mistake",
            tutorialsCheckpointQuestion = "Quick check",
            tutorialsSeriesAll = "All 23 Series",
            tutorialsBookmark = "Bookmark",
            tutorialsSaved = "Saved",
            tutorialsPrerequisites = "Prerequisites",
            tutorialsScopeNote = "Interface controls & 23 series headers are translated in 5 languages; detailed technical lesson steps are authored in English.",
            exitConfirmTitle = "Exit TK Studio?",
            exitConfirmBody = "Your settings and workspace changes are saved locally.",
            exitConfirmAction = "Exit"
        )

        StudioLanguage.BENGALI -> LocalizedStrings(
            navHome = "হোম",
            navProject = "প্রজেক্ট",
            navLiveCoding = "লাইভ কোডিং",
            navApkTool = "এপিকে টুল",
            navProfile = "প্রোফাইল",
            newProject = "নতুন প্রজেক্ট",
            importZip = "ZIP ইম্পোর্ট",
            quickActions = "দ্রুত কাজ",
            recentProjects = "সাম্প্রতিক প্রজেক্ট",
            settings = "সেটিংস",
            buildCenter = "বিল্ড সেন্টার",
            toolchain = "টুলচেইন",
            tutorials = "টিউটোরিয়াল",
            assetStudio = "অ্যাসেট স্টুডিও",
            utilities = "ইউটিলিটিজ",
            testCenter = "টেস্ট সেন্টার",
            security = "নিরাপত্তা ও গোপনীয়তা",
            backupRestore = "ব্যাকআপ ও রিস্টোর",
            myApplications = "আমার অ্যাপস",
            signingKeys = "সাইনিং কী",
            gitAndCi = "গিট ও গিটহাব",
            aboutLabel = "সম্পর্কে",
            searchPlaceholder = "খুঁজুন...",
            backLabel = "ফিরে যান",
            cancelLabel = "বাতিল",
            confirmLabel = "নিশ্চিত করুন",
            saveLabel = "সংরক্ষণ",
            deleteLabel = "মুছুন",
            resetLabel = "রিসেট",
            applyLabel = "প্রয়োগ করুন",
            openLabel = "খুলুন",
            enabledLabel = "সক্রিয়",
            disabledLabel = "নিষ্ক্রিয়",
            noProjectsTitle = "এখনও কোনো প্রজেক্ট নেই",
            noProjectsDesc = "কোডিং ও বিল্ড শুরু করতে একটি নতুন প্রজেক্ট তৈরি করুন বা ZIP ইম্পোর্ট করুন।",
            noBackupsTitle = "কোনো স্ন্যাপশট নেই",
            noBackupsDesc = "ডিফ, SHA-256 যাচাই এবং রোলব্যাক সুবিধার জন্য একটি স্ন্যাপশট তৈরি করুন।",
            noBuildsMessage = "এখনও কোনো বিল্ড রেকর্ড নেই। প্রজেক্ট তৈরি করে বিল্ড সেন্টার চালু করুন।",
            errorRetry = "আবার চেষ্টা",
            errorDiagnostics = "ডায়াগনস্টিকস",
            stagePreparing = "প্রস্তুতি",
            stageScanning = "স্ক্যানিং",
            stageValidating = "যাচাই",
            stageCompiling = "কম্পাইলিং",
            stagePackaging = "প্যাকেজিং",
            stageSigning = "সাইনিং",
            stageVerifying = "যাচাই সম্পন্ন",
            stageCompleted = "সম্পন্ন",
            stageFailed = "ব্যর্থ",
            stageCancelled = "বাতিল",
            tutorialsHeader = "টিউটোরিয়াল ও ল্যাব",
            tutorialsCheckpoints = "চেকপয়েন্ট সম্পন্ন",
            tutorialsResumeNext = "পরবর্তী পাঠ শুরু করুন",
            tutorialsOpenModule = "মডিউল খুলুন",
            tutorialsExpectedResult = "প্রত্যাশিত ফলাফল",
            tutorialsCommonError = "সাধারণ ভুল",
            tutorialsCheckpointQuestion = "দ্রুত যাচাই",
            tutorialsSeriesAll = "সকল ২৩টি সিরিজ",
            tutorialsBookmark = "বুকমার্ক",
            tutorialsSaved = "সংরক্ষিত",
            tutorialsPrerequisites = "পূর্বশর্ত",
            tutorialsScopeNote = "ইন্টারফেস ও ২৩টি সিরিজের শিরোনাম ৫ ভাষায় অনূদিত; বিস্তারিত প্রযুক্তিগত ধাপ ইংরেজিতে লিখিত।",
            exitConfirmTitle = "TK Studio বন্ধ করবেন?",
            exitConfirmBody = "আপনার সেটিংস এবং ওয়ার্কস্পেসের পরিবর্তনগুলো স্থানীয়ভাবে সংরক্ষিত রয়েছে।",
            exitConfirmAction = "প্রস্থান"
        )

        StudioLanguage.ARABIC -> LocalizedStrings(
            navHome = "الرئيسية",
            navProject = "المشاريع",
            navLiveCoding = "البرمجة الحية",
            navApkTool = "أداة APK",
            navProfile = "الملف",
            newProject = "مشروع جديد",
            importZip = "استيراد ZIP",
            quickActions = "إجراءات سريعة",
            recentProjects = "المشاريع الأخيرة",
            settings = "الإعدادات",
            buildCenter = "مركز البناء",
            toolchain = "سلسلة الأدوات",
            tutorials = "الدروس",
            assetStudio = "استوديو الأصول",
            utilities = "الأدوات",
            testCenter = "مركز الاختبار",
            security = "الأمان والخصوصية",
            backupRestore = "النسخ الاحتياطي",
            myApplications = "تطبيقاتي",
            signingKeys = "مفاتيح التوقيع",
            gitAndCi = "Git و GitHub",
            aboutLabel = "حول التطبيق",
            searchPlaceholder = "بحث...",
            backLabel = "رجوع",
            cancelLabel = "إلغاء",
            confirmLabel = "تأكيد",
            saveLabel = "حفظ",
            deleteLabel = "حذف",
            resetLabel = "إعادة ضبط",
            applyLabel = "تطبيق",
            openLabel = "فتح",
            enabledLabel = "مفعّل",
            disabledLabel = "معطّل",
            noProjectsTitle = "لا توجد مشاريع بعد",
            noProjectsDesc = "أنشئ مشروعًا جديدًا أو استورد أرشيف ZIP لبدء التحرير والبناء.",
            noBackupsTitle = "لا توجد نسخ احتياطية",
            noBackupsDesc = "أنشئ لقطة لمشروعك لتفعيل المقارنة وفحص SHA-256 والاستعادة الآمنة.",
            noBuildsMessage = "لا توجد عمليات بناء مسجلة بعد. افتح مركز البناء بعد إنشاء مشروع.",
            errorRetry = "إعادة المحاولة",
            errorDiagnostics = "التشخيص",
            stagePreparing = "التحضير",
            stageScanning = "الفحص",
            stageValidating = "التحقق",
            stageCompiling = "الترجمة",
            stagePackaging = "التحزيم",
            stageSigning = "التوقيع",
            stageVerifying = "التدقيق",
            stageCompleted = "مكتمل",
            stageFailed = "فشل",
            stageCancelled = "ملغى",
            tutorialsHeader = "الدروس والمختبرات",
            tutorialsCheckpoints = "نقاط تحقّق مكتملة",
            tutorialsResumeNext = "متابعة الدرس التالي",
            tutorialsOpenModule = "فتح الوحدة",
            tutorialsExpectedResult = "النتيجة المتوقعة",
            tutorialsCommonError = "خطأ شائع",
            tutorialsCheckpointQuestion = "تحقق سريع",
            tutorialsSeriesAll = "جميع السلاسل (23)",
            tutorialsBookmark = "حفظ",
            tutorialsSaved = "محفوظ",
            tutorialsPrerequisites = "المتطلبات",
            tutorialsScopeNote = "عناصر الواجهة وعناوين السلاسل الـ23 مترجمة بـ5 لغات؛ خطوات الدروس التقنية المفصلة مكتوبة بالإنجليزية.",
            exitConfirmTitle = "الخروج من TK Studio؟",
            exitConfirmBody = "تم حفظ إعداداتك وتغييرات مساحة العمل محليًا.",
            exitConfirmAction = "خروج"
        )

        StudioLanguage.CHINESE -> LocalizedStrings(
            navHome = "主页",
            navProject = "项目",
            navLiveCoding = "实时编码",
            navApkTool = "APK工具",
            navProfile = "个人",
            newProject = "新建项目",
            importZip = "导入ZIP",
            quickActions = "快捷操作",
            recentProjects = "最近项目",
            settings = "设置",
            buildCenter = "构建中心",
            toolchain = "工具链",
            tutorials = "教程",
            assetStudio = "资源工坊",
            utilities = "实用工具",
            testCenter = "测试中心",
            security = "隐私与安全",
            backupRestore = "备份与恢复",
            myApplications = "我的应用",
            signingKeys = "签名密钥",
            gitAndCi = "Git 与 GitHub",
            aboutLabel = "关于",
            searchPlaceholder = "搜索...",
            backLabel = "返回",
            cancelLabel = "取消",
            confirmLabel = "确认",
            saveLabel = "保存",
            deleteLabel = "删除",
            resetLabel = "重置",
            applyLabel = "应用",
            openLabel = "打开",
            enabledLabel = "已启用",
            disabledLabel = "已禁用",
            noProjectsTitle = "暂无项目",
            noProjectsDesc = "创建新项目或导入 ZIP 压缩包即可开始编码与构建。",
            noBackupsTitle = "暂无快照",
            noBackupsDesc = "创建工作区快照即可进行差异对比、SHA-256 校验和回滚恢复。",
            noBuildsMessage = "暂无构建记录。创建项目后可运行快速构建或打开构建中心。",
            errorRetry = "重试",
            errorDiagnostics = "诊断",
            stagePreparing = "准备中",
            stageScanning = "扫描中",
            stageValidating = "校验中",
            stageCompiling = "编译中",
            stagePackaging = "打包中",
            stageSigning = "签名中",
            stageVerifying = "验证中",
            stageCompleted = "已完成",
            stageFailed = "失败",
            stageCancelled = "已取消",
            tutorialsHeader = "教程与实验",
            tutorialsCheckpoints = "个检查点已完成",
            tutorialsResumeNext = "继续下一课",
            tutorialsOpenModule = "打开模块",
            tutorialsExpectedResult = "预期结果",
            tutorialsCommonError = "常见错误",
            tutorialsCheckpointQuestion = "快速检查",
            tutorialsSeriesAll = "全部 23 个系列",
            tutorialsBookmark = "收藏",
            tutorialsSaved = "已收藏",
            tutorialsPrerequisites = "前置条件",
            tutorialsScopeNote = "界面控件与 23 个系列标题支持 5 种语言；详细技术步骤正文为英文。",
            exitConfirmTitle = "退出 TK Studio？",
            exitConfirmBody = "您的设置和工作区更改已保存在本地。",
            exitConfirmAction = "退出"
        )

        StudioLanguage.URDU -> LocalizedStrings(
            navHome = "ہوم",
            navProject = "پروجیکٹ",
            navLiveCoding = "لائیو کوڈنگ",
            navApkTool = "APK ٹول",
            navProfile = "پروفائل",
            newProject = "نیا پروجیکٹ",
            importZip = "ZIP امپورٹ",
            quickActions = "فوری عمل",
            recentProjects = "حالیہ پروجیکٹس",
            settings = "ترتیبات",
            buildCenter = "بلڈ سینٹر",
            toolchain = "ٹول چین",
            tutorials = "سبق",
            assetStudio = "ایسٹ اسٹوڈیو",
            utilities = "یوٹیلٹیز",
            testCenter = "ٹیسٹ سینٹر",
            security = "سیکیورٹی اور رازداری",
            backupRestore = "بیک اپ اور بحالی",
            myApplications = "میری ایپس",
            signingKeys = "سائننگ کیز",
            gitAndCi = "Git اور GitHub",
            aboutLabel = "تعارف",
            searchPlaceholder = "تلاش کریں...",
            backLabel = "واپس",
            cancelLabel = "منسوخ",
            confirmLabel = "تصدیق",
            saveLabel = "محفوظ کریں",
            deleteLabel = "حذف کریں",
            resetLabel = "ری سیٹ",
            applyLabel = "لاگو کریں",
            openLabel = "کھولیں",
            enabledLabel = "فعال",
            disabledLabel = "غیر فعال",
            noProjectsTitle = "ابھی کوئی پروجیکٹ نہیں",
            noProjectsDesc = "کوڈنگ اور بلڈ شروع کرنے کے لیے نیا پروجیکٹ بنائیں یا ZIP آرکائیو امپورٹ کریں۔",
            noBackupsTitle = "کوئی اسنیپ شاٹ نہیں",
            noBackupsDesc = "ڈف، SHA-256 تصدیق اور رول بیک بحالی کے لیے ورک اسپیس اسنیپ شاٹ بنائیں۔",
            noBuildsMessage = "ابھی کوئی بلڈ ریکارڈ نہیں ہے۔ پروجیکٹ بنانے کے بعد بلڈ سینٹر کھولیں۔",
            errorRetry = "دوبارہ کوشش",
            errorDiagnostics = "تشخیص",
            stagePreparing = "تیاری",
            stageScanning = "اسکیننگ",
            stageValidating = "تصدیق",
            stageCompiling = "کمپائلنگ",
            stagePackaging = "پیکیجنگ",
            stageSigning = "سائننگ",
            stageVerifying = "جائزہ",
            stageCompleted = "مکمل",
            stageFailed = "ناکام",
            stageCancelled = "منسوخ",
            tutorialsHeader = "سبق اور لیبز",
            tutorialsCheckpoints = "چیک پوائنٹس مکمل",
            tutorialsResumeNext = "اگلا سبق جاری رکھیں",
            tutorialsOpenModule = "ماڈیول کھولیں",
            tutorialsExpectedResult = "متوقع نتیجہ",
            tutorialsCommonError = "عام غلطی",
            tutorialsCheckpointQuestion = "فوری جانچ",
            tutorialsSeriesAll = "تمام 23 سیریز",
            tutorialsBookmark = "بک مارک",
            tutorialsSaved = "محفوظ شدہ",
            tutorialsPrerequisites = "پیشگی شرائط",
            tutorialsScopeNote = "انٹرفیس اور 23 سیریز کے عنوانات 5 زبانوں میں ہیں؛ تفصیلی تکنیکی اسباق انگریزی میں تحریر ہیں۔",
            exitConfirmTitle = "TK Studio بند کریں؟",
            exitConfirmBody = "آپ کی ترتیبات اور ورک اسپیس تبدیلیاں مقامی طور پر محفوظ ہیں۔",
            exitConfirmAction = "خارج ہوں"
        )
    }

    fun seriesTitle(seriesKey: String, languageCode: String): String {
        val lang = StudioLanguage.fromCode(languageCode)
        return when (lang) {
            StudioLanguage.ENGLISH -> seriesKey
            StudioLanguage.BENGALI -> when (seriesKey) {
                "Intro & Architecture" -> "পরিচিতি ও আর্কিটেকচার"
                "Home & Navigation" -> "হোম ও নেভিগেশন"
                "Themes & Motion" -> "থিম ও মোশন"
                "Workspace & Explorer" -> "ওয়ার্কস্পেস ও এক্সপ্লোরার"
                "ZIP Safety & Detection" -> "ZIP নিরাপত্তা ও সনাক্তকরণ"
                "Code Editor Core" -> "কোড এডিটর মূল"
                "Live Coding & Console" -> "লাইভ কোডিং ও কনসোল"
                "HTML5, CSS3 & JS" -> "HTML5, CSS3 ও JS"
                "Web-to-APK Config" -> "Web-to-APK কনফিগ"
                "Native Kotlin & Gradle" -> "নেটিভ Kotlin ও Gradle"
                "Build Center & Cache" -> "বিল্ড সেন্টার ও ক্যাশ"
                "Toolchain & Health" -> "টুলচেইন ও স্বাস্থ্য"
                "APK Inspector & Diff" -> "APK ইন্সপেক্টর ও তুলনা"
                "Signing & Key Vault" -> "সাইনিং ও কী ভল্ট"
                "My Apps & Upgrades" -> "আমার অ্যাপস ও আপগ্রেড"
                "Local Git & Merge" -> "লোকাল Git ও মার্জ"
                "GitHub OAuth & Sync" -> "GitHub অনুমোদন ও সিঙ্ক"
                "GitHub Actions CI" -> "GitHub Actions CI"
                "Asset Studio & Audit" -> "অ্যাসেট স্টুডিও ও অডিট"
                "Dev Utilities & ZIP" -> "ডেভ ইউটিলিটি ও ZIP"
                "Security & PIN Vault" -> "নিরাপত্তা ও PIN ভল্ট"
                "Backup & AES-256" -> "ব্যাকআপ ও AES-256"
                else -> "অ্যাক্সেসিবিলিটি, ভাষা ও QA"
            }
            StudioLanguage.ARABIC -> when (seriesKey) {
                "Intro & Architecture" -> "المقدمة والبنية"
                "Home & Navigation" -> "الرئيسية والتنقل"
                "Themes & Motion" -> "السمات والحركة"
                "Workspace & Explorer" -> "مساحة العمل والمستكشف"
                "ZIP Safety & Detection" -> "أمان ZIP والاكتشاف"
                "Code Editor Core" -> "أساسيات محرر الأكواد"
                "Live Coding & Console" -> "البرمجة الحية ووحدة التحكم"
                "HTML5, CSS3 & JS" -> "HTML5 و CSS3 و JS"
                "Web-to-APK Config" -> "إعدادات Web-to-APK"
                "Native Kotlin & Gradle" -> "Kotlin الأصلي و Gradle"
                "Build Center & Cache" -> "مركز البناء والذاكرة المؤقتة"
                "Toolchain & Health" -> "سلسلة الأدوات وصحة الجهاز"
                "APK Inspector & Diff" -> "فاحص APK والمقارنة"
                "Signing & Key Vault" -> "التوقيع وخزنة المفاتيح"
                "My Apps & Upgrades" -> "تطبيقاتي والترقيات"
                "Local Git & Merge" -> "Git المحلي والدمج"
                "GitHub OAuth & Sync" -> "تفويض GitHub والمزامنة"
                "GitHub Actions CI" -> "تكامل GitHub Actions CI"
                "Asset Studio & Audit" -> "استوديو الأصول والتدقيق"
                "Dev Utilities & ZIP" -> "أدوات المطور و ZIP"
                "Security & PIN Vault" -> "الأمان وخزنة PIN"
                "Backup & AES-256" -> "النسخ الاحتياطي و AES-256"
                else -> "إمكانية الوصول واللغات والاختبار"
            }
            StudioLanguage.CHINESE -> when (seriesKey) {
                "Intro & Architecture" -> "简介与架构"
                "Home & Navigation" -> "主页与导航"
                "Themes & Motion" -> "主题与动效"
                "Workspace & Explorer" -> "工作区与文件浏览"
                "ZIP Safety & Detection" -> "ZIP 安全与类型识别"
                "Code Editor Core" -> "代码编辑器核心"
                "Live Coding & Console" -> "实时编码与控制台"
                "HTML5, CSS3 & JS" -> "HTML5、CSS3 与 JS"
                "Web-to-APK Config" -> "Web-to-APK 包装配置"
                "Native Kotlin & Gradle" -> "原生 Kotlin 与 Gradle"
                "Build Center & Cache" -> "构建中心与缓存"
                "Toolchain & Health" -> "工具链与环境健康"
                "APK Inspector & Diff" -> "APK 解析与对比"
                "Signing & Key Vault" -> "签名与密钥库"
                "My Apps & Upgrades" -> "我的应用与升级"
                "Local Git & Merge" -> "本地 Git 与分支合并"
                "GitHub OAuth & Sync" -> "GitHub 授权与同步"
                "GitHub Actions CI" -> "GitHub Actions CI"
                "Asset Studio & Audit" -> "资源工坊与扫描"
                "Dev Utilities & ZIP" -> "开发工具与 ZIP 浏览"
                "Security & PIN Vault" -> "安全审计与 PIN 锁"
                "Backup & AES-256" -> "备份与 AES-256"
                else -> "无障碍、多语言与 QA"
            }
            StudioLanguage.URDU -> when (seriesKey) {
                "Intro & Architecture" -> "تعارف اور آرکیٹیکچر"
                "Home & Navigation" -> "ہوم اور نیویگیشن"
                "Themes & Motion" -> "تھیمز اور موشن"
                "Workspace & Explorer" -> "ورک اسپیس اور ایکسپلورر"
                "ZIP Safety & Detection" -> "ZIP حفاظت اور شناخت"
                "Code Editor Core" -> "کوڈ ایڈیٹر بنیادیات"
                "Live Coding & Console" -> "لائیو کوڈنگ اور کنسول"
                "HTML5, CSS3 & JS" -> "HTML5، CSS3 اور JS"
                "Web-to-APK Config" -> "Web-to-APK ترتیب"
                "Native Kotlin & Gradle" -> "نیٹو Kotlin اور Gradle"
                "Build Center & Cache" -> "بلڈ سینٹر اور کیش"
                "Toolchain & Health" -> "ٹول چین اور صحت"
                "APK Inspector & Diff" -> "APK انسپکٹر اور موازنہ"
                "Signing & Key Vault" -> "سائننگ اور کی والٹ"
                "My Apps & Upgrades" -> "میری ایپس اور اپ گریڈ"
                "Local Git & Merge" -> "مقامی Git اور مرج"
                "GitHub OAuth & Sync" -> "GitHub اجازت اور سنک"
                "GitHub Actions CI" -> "GitHub Actions CI"
                "Asset Studio & Audit" -> "ایسٹ اسٹوڈیو اور آڈٹ"
                "Dev Utilities & ZIP" -> "یوٹیلٹیز اور ZIP"
                "Security & PIN Vault" -> "سیکیورٹی اور PIN والٹ"
                "Backup & AES-256" -> "بیک اپ اور AES-256"
                else -> "رسائی، زبانیں اور QA"
            }
        }
    }

    fun auditCompleteness(): Map<StudioLanguage, LocalizationAuditReport> {
        val result = linkedMapOf<StudioLanguage, LocalizationAuditReport>()
        StudioLanguage.entries.forEach { lang ->
            val map = stringsFor(lang.code).asMap()
            val missing = map.filterValues { it.isBlank() }.keys.toList()
            result[lang] = LocalizationAuditReport(
                language = lang,
                totalKeys = map.size,
                translatedKeys = map.size - missing.size,
                missingOrBlankKeys = missing
            )
        }
        return result
    }

    fun formatNumber(languageCode: String, number: Long): String {
        val loc = StudioLanguage.fromCode(languageCode).locale
        return NumberFormat.getIntegerInstance(loc).format(number)
    }

    fun formatShortDateTime(languageCode: String, timestampMs: Long): String {
        val loc = StudioLanguage.fromCode(languageCode).locale
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, loc).format(Date(timestampMs))
    }
}

/**
 * Ensures technical spans (file paths, hashes, code, package IDs) render LTR even inside RTL locales.
 */
@Composable
fun TechnicalLtrText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            style = style,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

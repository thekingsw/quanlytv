package vn.tvlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Giữ kiến trúc "một cửa": test đọc chính mã nguồn và báo ĐỎ nếu ai đó (người hay AI
 * agent) lại rải logic khóa ra nhiều chỗ — kiểu thay đổi từng gây ra chuỗi lỗi
 * "vá chỗ này hỏng chỗ kia" ở đợt 05–08/11. Nếu test này đỏ, đừng sửa test cho xanh:
 * hãy đưa logic về LockEngine / LockPolicy (xem ARCHITECTURE.md).
 */
class ArchitectureTest {

    private fun srcDir(): File {
        val candidates = listOf(
            "src/main/java/vn/tvlock",                  // Gradle chạy test tại thư mục module
            "app/src/main/java/vn/tvlock",
            "android/app/src/main/java/vn/tvlock"
        )
        for (c in candidates) {
            val f = File(c)
            if (f.isDirectory) return f
        }
        throw AssertionError("Không tìm thấy thư mục mã nguồn từ ${File(".").absolutePath}")
    }

    private fun sources(): List<File> = srcDir().listFiles { f -> f.name.endsWith(".kt") }!!.toList()

    private fun filesContaining(regex: Regex): List<String> =
        sources().filter { regex.containsMatchIn(it.readText()) }.map { it.name }.sorted()

    @Test fun onlyLockEngineBuildsTheLockIntent() {
        // Mọi chỗ mở màn khóa mode "lock" phải đi qua LockEngine.
        val offenders = filesContaining(Regex("""putExtra\(\s*"mode"\s*,\s*"lock"\s*\)"""))
        assertEquals("Chỉ LockEngine được dựng Intent khóa", listOf("LockEngine.kt"), offenders)
    }

    @Test fun onlyStoreWritesForegroundState() {
        val offenders = filesContaining(Regex("""\blastPkg(StartMs)?\s*=[^=]""")).filter { it != "Store.kt" && it != "LockPolicy.kt" }
        // LockPolicy có tham số cùng tên (lastPkg = ...) trong Input/CountInput — được phép.
        // Các file khác chỉ được ĐỌC lastPkg; ghi phải qua LockEngine -> Store.markForeground.
        val real = offenders.filter { name ->
            Regex("""\.\s*lastPkg(StartMs)?\s*=[^=]""").containsMatchIn(File(srcDir(), name).readText())
        }
        assertTrue("File ghi lastPkg trực tiếp: $real", real.isEmpty())
    }

    @Test fun policyFilesStayPure() {
        // Luật nằm trong các file thuần Kotlin để test được trên JVM.
        for (name in listOf("LockPolicy.kt", "SchedulePolicy.kt", "ForegroundTracker.kt", "PinGuard.kt")) {
            val text = File(srcDir(), name).readText()
            assertTrue("$name không được import android.*", !Regex("""^import\s+android\.""", RegexOption.MULTILINE).containsMatchIn(text))
            assertTrue("$name không được gọi getSharedPreferences", !text.contains("getSharedPreferences("))
            assertTrue("$name không được dùng java.io", !Regex("""^import\s+java\.io\.""", RegexOption.MULTILINE).containsMatchIn(text))
        }
    }

    @Test fun nobodyReimplementsTheLockDecision() {
        // Quyết định hiện màn khóa chỉ do LockPolicy.evaluate đưa ra. Các file ngoài
        // (trừ Store/LockEngine/LockPolicy) không được tự gọi blockReason / evaluate.
        val allowed = setOf("LockPolicy.kt", "Store.kt", "LockEngine.kt")
        val offenders = filesContaining(Regex("""\bblockReason\(|LockPolicy\.evaluate\(""")).filter { it !in allowed }
        assertTrue("Tự quyết định khóa ngoài LockEngine: $offenders", offenders.isEmpty())
    }
}

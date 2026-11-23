package com.hak.hakbanggul.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

// CLAUDE.md 7. 코드 규칙 > 경계 검사의 네 가지를 검사한다.
class LayerBoundaryTest {

    private val root = "com.hak.hakbanggul"

    private val files: List<KoFileDeclaration> = Konsist.scopeFromProduction().files

    private fun KoFileDeclaration.packageName(): String = packagee?.name.orEmpty()

    private fun KoFileDeclaration.importNames(): List<String> = imports.map { it.name }

    private fun segments(name: String): List<String> = name.split('.')

    // 우리 코드의 패키지 이름에서 기능 이름을 꺼낸다. 기능이 아니면 null.
    private fun featureOf(name: String): String? {
        val parts = segments(name.removePrefix("$root."))
        if (!name.startsWith("$root.feature.") || parts.size < 2) return null
        return parts[1]
    }

    private fun isOwnCode(name: String) = name.startsWith("$root.")

    private fun hasLayer(name: String, layer: String) = isOwnCode(name) && layer in segments(name)

    private fun assertNoViolations(rule: String, violations: List<String>) {
        assertTrue("$rule 위반:\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    // 검사 대상을 하나도 못 읽으면 나머지 검사가 늘 통과하므로 따로 확인한다.
    @Test
    fun `검사 대상 코드를 읽는다`() {
        assertTrue(files.any { it.packageName() == "$root.core.model" })
    }

    @Test
    fun `domain은 Android·네트워크·DB 라이브러리와 data·presentation을 가져오지 않는다`() {
        val forbiddenPrefixes = listOf(
            "android.", "androidx.", // Android (androidx.room., androidx.datastore. 포함)
            "okhttp3.", "retrofit2.", // 네트워크
        )
        val violations = files
            .filter { hasLayer(it.packageName(), "domain") }
            .flatMap { file ->
                file.importNames()
                    .filter { imp ->
                        forbiddenPrefixes.any { imp.startsWith(it) } ||
                            hasLayer(imp, "data") || hasLayer(imp, "presentation")
                    }
                    .map { "${file.path}: $it" }
            }
        assertNoViolations("domain 경계", violations)
    }

    @Test
    fun `presentation은 data를 가져오지 않는다`() {
        val violations = files
            .filter { hasLayer(it.packageName(), "presentation") }
            .flatMap { file ->
                file.importNames().filter { hasLayer(it, "data") }.map { "${file.path}: $it" }
            }
        assertNoViolations("presentation 경계", violations)
    }

    @Test
    fun `기능은 다른 기능의 data·presentation·di를 가져오지 않는다`() {
        val violations = files
            .filter { featureOf(it.packageName()) != null }
            .flatMap { file ->
                val own = featureOf(file.packageName())
                file.importNames()
                    .filter { imp ->
                        val other = featureOf(imp)
                        other != null && other != own &&
                            listOf("data", "presentation", "di").any { hasLayer(imp, it) }
                    }
                    .map { "${file.path}: $it" }
            }
        assertNoViolations("기능 사이 경계", violations)
    }

    @Test
    fun `core는 feature를 가져오지 않는다`() {
        val violations = files
            .filter { it.packageName().startsWith("$root.core") }
            .flatMap { file ->
                file.importNames().filter { it.startsWith("$root.feature") }.map { "${file.path}: $it" }
            }
        assertNoViolations("core 경계", violations)
    }
}

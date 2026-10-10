package com.captainduckay.dictator.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Ported from DictationCore's WhatsNewTests.swift.
class AppVersionTest {
    @Test fun parsesCommonForms() {
        val v = AppVersion.parse("v1.2.3")!!
        assertEquals(Triple(1, 2, 3), Triple(v.major, v.minor, v.patch))
        assertEquals("0.1.0", AppVersion.parse("0.1").toString())
        assertEquals("0.2.0-beta.1", AppVersion.parse("0.2.0-beta.1+abc").toString())
    }

    @Test fun rejectsGarbage() {
        listOf("", "v", "1.2.3.4", "1..2", "1.x", "1.2.3-", "1.2.3-a..b", "latest").forEach {
            assertNull(it, AppVersion.parse(it))
        }
    }

    @Test fun comparesBySemver() {
        val ordered =
            listOf(
                "0.0.1",
                "0.1.0-alpha",
                "0.1.0-alpha.1",
                "0.1.0-alpha.beta",
                "0.1.0-beta.2",
                "0.1.0-beta.11",
                "0.1.0",
                "0.1.1",
                "0.10.0",
                "1.0.0",
            ).map { AppVersion.parse(it)!! }
        ordered.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b", a < b && !(b < a)) }
        assertEquals(AppVersion.parse("v0.1.0"), AppVersion.parse("0.1.0+build.7"))
    }
}

class ChangelogTest {
    private val markdown =
        """
        # Changelog

        ## [Unreleased]
        - Not out yet.

        ## [0.2.0] - 2026-11-01
        ### Added
        - Thing two.

        ## [0.1.0] - 2026-10-09
        ### Added
        - Thing one.

        ## 0.0.1
        First build.
        """.trimIndent()

    private val entries = Changelog.parse(markdown)

    private fun v(text: String) = AppVersion.parse(text)!!

    @Test fun parsesVersionSections() {
        assertEquals(listOf("0.2.0", "0.1.0", "0.0.1"), entries.map { it.version.toString() })
        assertEquals("2026-11-01", entries[0].note)
        assertEquals("### Added\n- Thing two.", entries[0].body)
        assertEquals("", entries[2].note)
        assertEquals("First build.", entries[2].body)
    }

    @Test fun sameVersionShowsNothing() =
        assertEquals(
            WhatsNew.Decision.None,
            WhatsNew.decide(v("0.2.0"), "0.2.0", entries),
        )

    @Test fun downgradeShowsNothing() =
        assertEquals(
            WhatsNew.Decision.None,
            WhatsNew.decide(v("0.1.0"), "0.2.0", entries),
        )

    @Test fun updateShowsEveryVersionSinceLastSeen() {
        val decision = WhatsNew.decide(v("0.2.0"), "0.0.1", entries) as WhatsNew.Decision.Updated
        assertEquals(listOf("0.2.0", "0.1.0"), decision.entries.map { it.version.toString() })
    }

    @Test fun freshInstallGetsAWelcomeWithThisVersion() {
        val decision = WhatsNew.decide(v("0.1.0"), null, entries) as WhatsNew.Decision.Welcome
        assertEquals(listOf("0.1.0"), decision.entries.map { it.version.toString() })
    }

    @Test fun unreadableLastSeenShowsThisVersion() =
        assertEquals(WhatsNew.Decision.Updated(listOf(entries[0])), WhatsNew.decide(v("0.2.0"), "garbage", entries))
}

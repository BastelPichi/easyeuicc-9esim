package im.angry.openeuicc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationCodeTest {
    /**
     * @see {https://www.gsma.com/esim/wp-content/uploads/2020/06/SGP.22-v2.2.2.pdf#page=112}
     */
    @Suppress("SpellCheckingInspection")
    private val expectedFixtures = mapOf(
        // if SM-DP+ OID and Confirmation Code Required Flag are not present
        Pair(
            "1\$SMDP.GSMA.COM\$04386-AGYFT-A74Y8-3F815",
            ActivationCode("SMDP.GSMA.COM", "04386-AGYFT-A74Y8-3F815", null, false)
        ),
        // if SM-DP+ OID is not present and Confirmation Code Required Flag is present
        Pair(
            "1\$SMDP.GSMA.COM\$04386-AGYFT-A74Y8-3F815\$\$1",
            ActivationCode("SMDP.GSMA.COM", "04386-AGYFT-A74Y8-3F815", null, true)
        ),
        // if SM-DP+ OID and Confirmation Code Required flag are present
        Pair(
            "1\$SMDP.GSMA.COM\$04386-AGYFT-A74Y8-3F815\$1.3.6.1.4.1.31746\$1",
            ActivationCode("SMDP.GSMA.COM", "04386-AGYFT-A74Y8-3F815", "1.3.6.1.4.1.31746", true)
        ),
        // if SM-DP+ OID is present and Confirmation Code Required Flag is not present
        Pair(
            "1\$SMDP.GSMA.COM\$04386-AGYFT-A74Y8-3F815\$1.3.6.1.4.1.31746",
            ActivationCode("SMDP.GSMA.COM", "04386-AGYFT-A74Y8-3F815", "1.3.6.1.4.1.31746", false)
        ),
        // if SM-DP+ OID is present, Activation token is left blank and Confirmation Code Required Flag is not present
        Pair(
            "1\$SMDP.GSMA.COM\$\$1.3.6.1.4.1.31746",
            ActivationCode("SMDP.GSMA.COM", null, "1.3.6.1.4.1.31746", false)
        ),
    )

    @Suppress("SpellCheckingInspection")
    private val unexpectedFixtures = listOf(
        "", "LPA:",
        "1", "LPA:1",
        "1$", "LPA:1$",
        "1$$", "LPA:1$$",
        "2\$SMDP.GSMA.COM", "LPA:2\$SMDP.GSMA.COM",
    )

    @Test
    fun testParsing() {
        for ((input, expected) in expectedFixtures) {
            val actual = ActivationCode.fromString(input)
            assertEquals(expected.address, actual.address)
            assertEquals(expected.matchingId, actual.matchingId)
            assertEquals(expected.oid, actual.oid)
            assertEquals(expected.confirmationCodeRequired, actual.confirmationCodeRequired)
            assertEquals(input, expected.toString())
            assertTrue(expected == actual)
        }
    }

    @Test
    fun testUnexpected() {
        for (fixture in unexpectedFixtures) {
            assertThrows(IllegalArgumentException::class.java) {
                ActivationCode.fromString(fixture)
            }
        }
    }
}
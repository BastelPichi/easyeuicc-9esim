package im.angry.openeuicc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActivationCodeTest {
    /**
     * @see {https://www.gsma.com/esim/wp-content/uploads/2020/06/SGP.22-v2.2.2.pdf#page=112}
     */
    @Suppress("SpellCheckingInspection")
    private val expectedFixtures = buildMap {
        val ac = ActivationCode()
        ac.address = "SMDP.GSMA.COM"
        ac.matchingId = "04386-AGYFT-A74Y8-3F815"
        // if SM-DP+ OID and Confirmation Code Required Flag are not present
        put("1\$SMDP.GSMA.COM\$04386-AGYFT-A74Y8-3F815", ac.clone())
        // if SM-DP+ OID is not present and Confirmation Code Required Flag is present
        ac.confirmationCodeRequired = true
        put("1\$SMDP.GSMA.COM\$04386-AGYFT-A74Y8-3F815\$\$1", ac.clone())
        // if SM-DP+ OID and Confirmation Code Required flag are present
        ac.oid = "1.3.6.1.4.1.31746"
        put("1\$SMDP.GSMA.COM\$04386-AGYFT-A74Y8-3F815\$1.3.6.1.4.1.31746\$1", ac.clone())
        // if SM-DP+ OID is present and Confirmation Code Required Flag is not present
        ac.confirmationCodeRequired = false
        put("1\$SMDP.GSMA.COM\$04386-AGYFT-A74Y8-3F815\$1.3.6.1.4.1.31746", ac.clone())
        // if SM-DP+ OID is present, Activation token is left blank and Confirmation Code Required Flag is not present
        ac.matchingId = ""
        put("1\$SMDP.GSMA.COM\$\$1.3.6.1.4.1.31746", ac.clone())
    }

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
        val actual = ActivationCode()
        for ((input, expected) in expectedFixtures) {
            actual.fromToken(input)
            assertEquals(expected.address, actual.address)
            assertEquals(expected.matchingId, actual.matchingId)
            assertEquals(expected.oid, actual.oid)
            assertEquals(expected.confirmationCodeRequired, actual.confirmationCodeRequired)
            assertEquals(expected, actual)
            assertEquals(expected.toString(), input)
            assertEquals(expected.hashCode(), actual.hashCode())
        }
    }

    @Test
    fun testUnexpected() {
        for (fixture in unexpectedFixtures) {
            assertThrows(IllegalArgumentException::class.java) {
                val activationCode = ActivationCode()
                activationCode.fromToken(fixture)
            }
        }
    }
}
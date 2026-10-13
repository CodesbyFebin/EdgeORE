package com.edgeore.app.edge

import com.edgeore.app.crypto.Base58
import com.edgeore.app.receipts.SoftwareReceiptSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CrewTest {
    private fun key(b: Int) = Base58.encode(ByteArray(32) { b.toByte() })

    private val a = key(1)
    private val b = key(2)

    private fun plan(creator: String = a) =
        Crew.Plan("c1", "Night crew", 439200, creator, listOf(Crew.Member(a, 0b111, 1_000_000), Crew.Member(b, 0b111000, 2_000_000)))

    @Test fun keystoreSignedPlanRoundTripsAndDetectsTamper() {
        val s = Crew.signWithKeystore(plan(), SoftwareReceiptSigner())
        assertEquals(Crew.Check.VALID_KEYSTORE, Crew.verify(s))
        val back = Crew.decode(Crew.encode(s))!!
        assertEquals(Crew.Check.VALID_KEYSTORE, Crew.verify(back))
        val bumped =
            back.copy(
                plan =
                    back.plan.copy(
                        members =
                            back.plan.members.map {
                                if (it.wallet ==
                                    b
                                ) {
                                    it.copy(lamportsPerSquare = 9_000_000)
                                } else {
                                    it
                                }
                            },
                    ),
            )
        assertEquals(Crew.Check.TAMPERED, Crew.verify(bumped))
        assertEquals(6, Integer.bitCount(s.plan.coverageMask))
        assertNull(Crew.decode("https://evil.example/crew"))
    }

    @Test fun walletSignedPlanVerifies() {
        val gen =
            org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator().apply {
                init(
                    org.bouncycastle.crypto.params
                        .Ed25519KeyGenerationParameters(java.security.SecureRandom()),
                )
            }
        val kp = gen.generateKeyPair()
        val creator = Base58.encode((kp.public as org.bouncycastle.crypto.params.Ed25519PublicKeyParameters).encoded)
        val p = plan(creator)
        val msg = Crew.canonical(p).toByteArray()
        val sig =
            org.bouncycastle.crypto.signers.Ed25519Signer().run {
                init(true, kp.private)
                update(msg, 0, msg.size)
                generateSignature()
            }
        val s = Crew.Signed(p, "ed25519-wallet", Base64.getEncoder().encodeToString(sig), null)
        assertEquals(Crew.Check.VALID_WALLET, Crew.verify(s))
        assertEquals(Crew.Check.TAMPERED, Crew.verify(s.copy(plan = p.copy(roundId = 1))))
    }

    @Test fun invalidPlansRefused() {
        assertTrue(Crew.problems(plan().copy(members = listOf(Crew.Member(a, 0, 1)))).isNotEmpty())
        assertTrue(Crew.problems(plan().copy(members = listOf(Crew.Member(a, 1, 1), Crew.Member(a, 2, 1)))).isNotEmpty())
        assertTrue(Crew.problems(plan()).isEmpty())
    }

    @Test fun evenSplitBalancesNetByStakeAndIgnoresUnverified() {
        // a deployed 3M got 0 back; b deployed 6M got 9M back. Net +0 overall -> fair net 0 each... use concrete numbers.
        val r =
            listOf(
                Crew.MemberResult(a, 3_000_000, 0, 0, true),
                Crew.MemberResult(b, 6_000_000, 9_000_000, 0, true),
                Crew.MemberResult(key(3), 5_000_000, 50_000_000, 0, false),
            )
        val t = Crew.evenSplit(r)
        // total net 0; a fair 0 (has -3M), b fair 0 (has +3M) -> b pays a 3M
        assertEquals(listOf(Crew.Transfer(b, a, 3_000_000)), t)
        assertTrue(Crew.evenSplit(emptyList()).isEmpty())
    }

    @Test fun statsFromPlansAndProofs() {
        val s = Crew.stats(listOf(plan()), mapOf(439200L to 4))
        assertEquals(1, s.wins)
        assertEquals(6, s.coveredSquares)
        assertEquals(0, Crew.stats(listOf(plan()), mapOf(439200L to 20)).wins)
    }

    @Test fun custodyCopyHasNoIncomeClaims() {
        val t = Crew.CUSTODY_NOTE
        assertTrue(t.contains("never holds funds"))
        for (bad in listOf("APR", "yield", "profit", "guarantee", "earn")) assertFalse(t.contains(bad, true))
    }

    @Test fun reassignmentCoversAll25WithoutOverlapForAnyN() {
        for (n in 1..25) {
            val members = (1..n).map { Crew.Member(key(it), 1 shl (it - 1), 2_500_000) }
            val p = Crew.Plan("c", "x", 1, key(1), members)
            val r = Crew.reassign(p, members.map { it.wallet }.toSet())!!
            assertEquals(n, r.members.size)
            var union = 0
            r.members.forEach { m ->
                assertEquals(0, union and m.mask)
                union = union or m.mask
                assertTrue(m.totalLamports <= 2_500_000)
            }
            assertEquals((1 shl 25) - 1, union)
            val counts = r.members.map { Integer.bitCount(it.mask) }
            assertTrue(counts.max() - counts.min() <= 1)
            assertEquals(r, Crew.reassign(p, members.map { it.wallet }.toSet()))
        }
    }

    @Test fun dropoutsShiftSquaresToRemainingMembers() {
        val r = Crew.reassign(plan(), setOf(b))!!
        assertEquals(listOf(b), r.members.map { it.wallet })
        assertEquals((1 shl 25) - 1, r.members[0].mask)
        assertEquals(6_000_000L / 25, r.members[0].lamportsPerSquare)
        assertNull(Crew.reassign(plan(), emptySet()))
    }
}

package com.edgeore.app
import org.junit.Assert.*
import org.junit.Test
class TrustTest {
 @Test fun mutationRefused(){assertFalse(MessageBinding.unchanged(byteArrayOf(1),byteArrayOf(2)))}
 @Test fun sameBytes(){assertTrue(MessageBinding.unchanged(byteArrayOf(1),byteArrayOf(1)))}
 @Test fun overflow(){assertEquals("OVERFLOW",SpendGuard.check(Long.MAX_VALUE,25,Long.MAX_VALUE,0,Long.MAX_VALUE))}
 @Test fun squareCost(){assertEquals("ROUND_LIMIT",SpendGuard.check(10,25,100,0,1000))}
 @Test fun dailyLimit(){assertEquals("DAILY_LIMIT",SpendGuard.check(10,1,100,Long.MAX_VALUE,100))}
 @Test fun valid(){assertNull(SpendGuard.check(10,2,100,5,100))}
}

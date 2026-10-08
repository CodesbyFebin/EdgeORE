import java.math.BigInteger
import java.security.MessageDigest

object EdgeOreCore {
 fun lamports(input: String): Long {
  require(Regex("""^[0-9]+(\.[0-9]{1,9})?$""").matches(input)) { "INVALID_AMOUNT" }
  val parts=input.split('.')
  val units=BigInteger(parts[0])*BigInteger("1000000000") + BigInteger((parts.getOrNull(1) ?: "").padEnd(9,'0'))
  return units.longValueExact()
 }
 fun exposure(perSquare: Long, mask: Int): Long {
  require(perSquare>0 && mask>0 && mask ushr 25==0) { "INVALID_DEPLOY" }
  return Math.multiplyExact(perSquare,Integer.bitCount(mask).toLong())
 }
 fun sameMessage(a:ByteArray,b:ByteArray)=MessageDigest.isEqual(a,b)
 fun eligible(total:Long,spent:Long,limit:Long)=total>0 && spent>=0 && limit>=spent && total<=limit-spent
}
fun main() {
 var passed=0
 fun test(name:String,body:()->Unit) { body();passed++;println("PASS: $name") }
 fun refuses(body:()->Unit) { check(runCatching(body).isFailure) }
 test("Exact decimal units") { check(EdgeOreCore.lamports("0.000000001")==1L) }
 test("Whole SOL") { check(EdgeOreCore.lamports("2")==2000000000L) }
 test("Excess precision refused") { refuses { EdgeOreCore.lamports("0.0000000001") } }
 test("Negative input refused") { refuses { EdgeOreCore.lamports("-1") } }
 test("Overflow refused") { refuses { EdgeOreCore.lamports("999999999999999999999999") } }
 test("All selected squares charged") { check(EdgeOreCore.exposure(10,7)==30L) }
 test("High mask bit refused") { refuses { EdgeOreCore.exposure(10,1 shl 25) } }
 test("Exposure overflow refused") { refuses { EdgeOreCore.exposure(Long.MAX_VALUE,3) } }
 test("Mutation refused") { check(!EdgeOreCore.sameMessage(byteArrayOf(1,2),byteArrayOf(1,3))) }
 test("Daily budget enforced") { check(!EdgeOreCore.eligible(10,95,100));check(EdgeOreCore.eligible(5,95,100)) }
 println("EdgeORE: $passed/10 core checks passed. Android, wallets, ORE execution: NOT_RUN.")
}
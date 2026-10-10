import com.edgeore.app.io.BoundedInput
import com.edgeore.app.receipts.ReceiptVerifier
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/** Export fields outside every receipt-body and checkpoint signature; only the optional envelope covers them (docs/hardening-backlog.md H4). */
const val UNSIGNED_EXPORT_FIELDS = "top-level note, exclusions, payment, location, exportedAt; deviceKeys[].protection, deviceKeys[].firstUsedAt"

/** Exit 0: integrity accepted; 1: rejected; 2: invocation/read/runtime error. */
fun checkReceipt(args: Array<String>, out: PrintStream = System.out, err: PrintStream = System.err): Int {
    if (args.size !in setOf(1, 3) || (args.size == 3 && args[1] != "--trusted-key")) {
        err.println("Usage: verify-receipt.sh EXPORT.json [--trusted-key DEVICE-SPKI.der]")
        return 2
    }
    return try {
        val input = File(args[0]).inputStream().use {
            BoundedInput.readUtf8Text(it, 64L * 1024 * 1024, 64 * 1024 * 1024)
        }
        val text = when (input) {
            is BoundedInput.TextResult.Ok -> input.text
            is BoundedInput.TextResult.Refused -> {
                out.println("VERIFIED: FAIL: ${input.reason}")
                return 1
            }
        }
        val key = if (args.size == 3) File(args[2]).inputStream().use { BoundedInput.readAtMost(it, 16 * 1024) } else null
        val report = ReceiptVerifier.verify(text, trustedDeviceKeySpki = key)
        out.println(if (report.accepted) "VERIFIED: PASS" else "VERIFIED: FAIL")
        out.println(report.summary)
        report.findings.forEach { out.println("Finding: $it") }
        // Hardening backlog H4: exports since integration 0.2.9 sign these fields in an "envelope"; older (or stripped) ones do not.
        if (report.descriptiveFieldsSigned) out.println("Descriptive export fields covered by the signed envelope: $UNSIGNED_EXPORT_FIELDS")
        else out.println("Not covered by any signature (descriptive only, no signed envelope): $UNSIGNED_EXPORT_FIELDS")
        if (report.accepted) 0 else 1
    } catch (e: java.io.IOException) {
        err.println("CHECKER ERROR: cannot read input (${e.javaClass.simpleName})")
        2
    } catch (e: Exception) {
        // Malformed exports can throw inside the existing verifier; fail closed.
        out.println("VERIFIED: FAIL: verifier refused input (${e.javaClass.simpleName})")
        1
    }
}

fun main(args: Array<String>) { exitProcess(checkReceipt(args)) }

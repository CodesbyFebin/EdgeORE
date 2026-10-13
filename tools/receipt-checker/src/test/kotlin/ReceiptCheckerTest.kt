import com.edgeore.app.receipts.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ReceiptCheckerTest {
    @get:Rule val temp = TemporaryFolder()

    private fun run(vararg args: String): Pair<Int, String> {
        val bytes = ByteArrayOutputStream()
        val stream = PrintStream(bytes)
        return checkReceipt(arrayOf(*args), stream, stream) to bytes.toString("UTF-8")
    }

    private fun export(): Pair<java.io.File, SoftwareReceiptSigner> {
        val signer = SoftwareReceiptSigner()
        val log = ReceiptLog(java.io.File(temp.root, "log.jsonl"), signer)
        log.append(ReceiptDraft(ReceiptKind.POLICY, "REFUSED", "Fixture", "Synthetic test only", "unit test"))
        val file = temp.newFile("export.json").apply { writeText(log.export()) }
        return file to signer
    }

    @Test fun acceptsExportAndReportsUnpinnedOfflineLimits() {
        val (file, _) = export()
        val (code, output) = run(file.path)
        assertEquals(0, code)
        assertTrue(output.contains("VERIFIED: PASS"))
        assertTrue(output.contains("not pinned"))
        assertTrue(output.contains("chain status not checked offline"))
    }

    @Test fun rejectsWellFormedBodyTamperAndPreservesOriginal() {
        val (original, _) = export()
        val before = original.readBytes()
        val doc = JSONObject(original.readText())
        val record = doc.getJSONArray("receipts").getJSONObject(0)
        val body = JSONObject(record.getString("body")).put("title", "Changed")
        record.put("body", body.toString())
        val copy = temp.newFile("tampered.json").apply { writeText(doc.toString()) }
        assertEquals(1, run(copy.path).first)
        assertArrayEquals(before, original.readBytes())
        assertEquals(0, run(original.path).first)
    }

    @Test fun acceptsPinnedKeyAndRejectsDifferentKey() {
        val (file, signer) = export()
        val key = temp.newFile("key.der").apply { writeBytes(signer.publicKeySpki()) }
        assertEquals(0, run(file.path, "--trusted-key", key.path).first)
        key.writeBytes(SoftwareReceiptSigner().publicKeySpki())
        assertEquals(1, run(file.path, "--trusted-key", key.path).first)
    }

    @Test fun rejectsMalformedJson() {
        val file = temp.newFile("bad.json").apply { writeText("{") }
        assertEquals(1, run(file.path).first)
    }

    @Test fun rejectsMalformedUtf8() {
        val file = temp.newFile("bad-utf8.json").apply { writeBytes(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertEquals(1, run(file.path).first)
    }

    @Test fun acceptsStorageReceiptAndRejectsItsTamperedCopy() {
        // STORAGE was added by feature/storage-vault; the checker compiles the app's Receipts.kt, so it must accept the kind.
        val log = ReceiptLog(java.io.File(temp.root, "storage.jsonl"), SoftwareReceiptSigner())
        log.append(
            ReceiptDraft(
                ReceiptKind.STORAGE,
                "IMPORTED",
                "Imported",
                "Fixture. App-record integrity evidence; not proof of provider storage or physical deletion.",
                "EdgeORE local vault",
                operationId = "op-fixture",
                digests =
                    mapOf(
                        "encryptedObjectSha256" to "00".repeat(32),
                        "vaultObjectId" to "fixture",
                    ),
                localObservation = Evidence("OBSERVED", "Synthetic test only"),
            ),
        )
        val file = temp.newFile("storage-export.json").apply { writeText(log.export()) }
        val (code, output) = run(file.path)
        assertEquals(output, 0, code)
        assertTrue(output.contains("VERIFIED: PASS"))
        val doc = JSONObject(file.readText())
        val record = doc.getJSONArray("receipts").getJSONObject(0)
        record.put("body", JSONObject(record.getString("body")).put("outcome", "DELETED").toString())
        val copy = temp.newFile("storage-tampered.json").apply { writeText(doc.toString()) }
        assertEquals(1, run(copy.path).first)
    }

    @Test fun rejectsSignedStorageReceiptThatClaimsPayment() {
        // Correctly signed, so only the schema rule can reject it: a storage/backup record is never a payment observation.
        val log = ReceiptLog(java.io.File(temp.root, "storage-pay.jsonl"), SoftwareReceiptSigner())
        log.append(ReceiptDraft(ReceiptKind.STORAGE, "PINNED", "Pinned", "Fixture", "EdgeORE local vault", payment = "OBSERVED fixture"))
        val file = temp.newFile("storage-pay.json").apply { writeText(log.export()) }
        val (code, output) = run(file.path)
        assertEquals(1, code)
        assertTrue(output, output.contains("only wallet reviews can carry a payment observation"))
    }

    @Test fun signedEnvelopeDetectsEditedDescriptiveFields() {
        // H4: exports now carry a signed envelope, so editing note or a top-level payment string is rejected.
        val (file, _) = export()
        val (okCode, okOut) = run(file.path)
        assertEquals(okOut, 0, okCode)
        assertTrue(okOut, okOut.contains("Descriptive export fields covered by the signed envelope"))
        val doc = JSONObject(file.readText()).put("note", "Edited after export").put("payment", "OBSERVED 9 SOL")
        val copy = temp.newFile("edited-note.json").apply { writeText(doc.toString()) }
        val (code, output) = run(copy.path)
        assertEquals(output, 1, code)
        assertTrue(output, output.contains("Envelope: descriptive fields changed after export"))
    }

    @Test fun newExportWithEnvelopeRemovedIsRejected() {
        val (file, _) = export()
        val doc = JSONObject(file.readText()).also { it.remove("envelope") }.put("note", "Edited after export")
        val copy = temp.newFile("no-envelope.json").apply { writeText(doc.toString()) }
        val (code, output) = run(copy.path)
        assertEquals(output, 1, code)
        assertTrue(output, output.contains("Envelope: required by this export but missing (removed after export)"))
    }

    @Test fun legacyExportWithoutEnvelopeIsLabelledUnsigned() {
        // A pre-envelope export has neither envelope nor envelopeRequired: it still verifies, labelled legacy and unsigned.
        val (file, _) = export()
        val doc =
            JSONObject(file.readText()).also {
                it.remove("envelope")
                it.remove("envelopeRequired")
            }
        val copy = temp.newFile("legacy.json").apply { writeText(doc.toString()) }
        val (code, output) = run(copy.path)
        assertEquals(output, 0, code)
        assertTrue(
            output,
            output.contains(
                "Not covered by any signature (legacy export without envelope, descriptive only): top-level note, exclusions, payment, location, exportedAt",
            ),
        )
    }

    @Test fun nonBooleanEnvelopeRequiredIsRejected() {
        val (file, _) = export()
        val doc = JSONObject(file.readText()).put("envelopeRequired", "true")
        val copy = temp.newFile("bad-flag.json").apply { writeText(doc.toString()) }
        val (code, output) = run(copy.path)
        assertEquals(output, 1, code)
        assertTrue(output, output.contains("envelopeRequired is not true or false"))
    }

    @Test fun realPreEnvelopeExportFromBuildD675002StillVerifies() {
        // Committed evidence from the submitted build d675002bd701, written before envelopes existed.
        val dir = java.io.File("../../evidence/build-d675002bd701/receipt-checker-demo")
        val (code, output) = run(java.io.File(dir, "receipt.json").path)
        assertEquals(output, 0, code)
        assertTrue(output, output.contains("legacy export without envelope"))
        assertEquals(1, run(java.io.File(dir, "receipt-tampered.json").path).first)
    }

    @Test fun newReceiptsUseV3SignatureOverDescriptiveFields() {
        val (file, signer) = export()
        val rec = JSONObject(file.readText()).getJSONArray("receipts").getJSONObject(0)
        val body = JSONObject(rec.getString("body"))
        assertEquals(ReceiptLog.SCHEMA_V3, body.getString("schema"))
        val payload = String(receiptSignaturePayload(body, rec.getString("sha256")), Charsets.UTF_8)
        assertTrue(payload.startsWith(ReceiptLog.SIGNATURE_DOMAIN_V3))
        assertTrue(payload.contains("title=\"Fixture\"") && payload.contains("detail=\"Synthetic test only\""))
        val ok =
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initVerify(
                    java.security.KeyFactory
                        .getInstance("EC")
                        .generatePublic(java.security.spec.X509EncodedKeySpec(signer.publicKeySpki())),
                )
                update(payload.toByteArray(Charsets.UTF_8))
                verify(
                    java.util.Base64
                        .getDecoder()
                        .decode(rec.getString("deviceSignature")),
                )
            }
        assertTrue(ok)
    }

    @Test fun v3ReceiptWithDigestOnlySignatureIsRejected() {
        val signer = SoftwareReceiptSigner()
        val logFile = java.io.File(temp.root, "v3.jsonl")
        val log = ReceiptLog(logFile, signer)
        log.append(ReceiptDraft(ReceiptKind.POLICY, "REFUSED", "Fixture", "Synthetic test only", "unit test"))
        val line = JSONObject(logFile.readText().trim())
        line.put(
            "deviceSignature",
            java.util.Base64
                .getEncoder()
                .encodeToString(signer.sign(line.getString("sha256").toByteArray(Charsets.US_ASCII))),
        )
        logFile.writeText(line.toString() + "\n")
        val copy = temp.newFile("v3-downgraded.json").apply { writeText(ReceiptLog(logFile, signer).export()) }
        val (code, output) = run(copy.path)
        assertEquals(output, 1, code)
        assertTrue(output, output.contains("device signature invalid"))
    }

    @Test fun legacyV2ReceiptLineStillVerifies() {
        val signer = SoftwareReceiptSigner()
        val logFile = java.io.File(temp.root, "legacy.jsonl")
        ReceiptLog(logFile, signer).append(ReceiptDraft(ReceiptKind.POLICY, "REFUSED", "Fixture", "Synthetic test only", "unit test"))
        val body = JSONObject(JSONObject(logFile.readText().trim()).getString("body")).put("schema", ReceiptLog.SCHEMA_V2).toString()
        val sha =
            com.edgeore.app.crypto.Sha256
                .hex(body)
        val sig =
            java.util.Base64
                .getEncoder()
                .encodeToString(signer.sign(sha.toByteArray(Charsets.US_ASCII)))
        logFile.writeText(StoredReceipt(body, sha, sig).toLine().toString() + "\n")
        val copy = temp.newFile("legacy.json").apply { writeText(ReceiptLog(logFile, signer).export()) }
        val (code, output) = run(copy.path)
        assertEquals(output, 0, code)
        assertTrue(output, output.contains("legacy digest-only"))
    }

    @Test fun reportsMissingInputAsToolError() {
        assertEquals(2, run(java.io.File(temp.root, "missing").path).first)
    }

    @Test fun rejectsWrongArguments() {
        assertEquals(2, run().first)
        assertEquals(2, run("file", "--wrong", "key").first)
    }
}

class RoundProofCheckerTest {
    @get:Rule val temp = TemporaryFolder()

    private fun run(vararg args: String): Pair<Int, String> {
        val bytes = ByteArrayOutputStream()
        val stream = PrintStream(bytes)
        return checkReceipt(arrayOf(*args), stream, stream) to bytes.toString("UTF-8")
    }

    private fun roundBytes(): ByteArray {
        val b = java.nio.ByteBuffer.allocate(com.edgeore.app.edge.RoundRules.ROUND_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put(109.toByte()).put(ByteArray(7)).putLong(42)
        repeat(25) { b.putLong(if (it == 7) 1_000_000_000L else 0L) }
        repeat(50) { b.putLong(0) }
        b.putLong(7).put(ByteArray(24)) // slot_hash -> rng 7 -> square 8
        b.putLong(1).putLong(0).put(ByteArray(32))
        b.putLong(100_000_000_000L)
        repeat(24) { b.putLong(0) }
        b.putLong(0).putLong(1_000_000_000L - 10_000_000L).putLong(1) // vaulted, returned, miners
        val solo = com.edgeore.app.edge.RoundRules.isSolo(42, 7)
        b.put(if (solo) ByteArray(32) else com.edgeore.app.crypto.Base58.decodePublicKey(com.edgeore.app.edge.RoundRules.SPLIT_ADDRESS))
        return b.array()
    }

    private fun bundle(tamperProof: Boolean = false): java.io.File {
        val proof = com.edgeore.app.edge.RoundProof.build(com.edgeore.app.ore.OreV3.roundAddress(42), roundBytes(), 1, "test", null, null)
        val log = ReceiptLog(java.io.File(temp.root, "log.jsonl"), SoftwareReceiptSigner())
        log.append(
            ReceiptDraft(
                ReceiptKind.POLICY, "RECORDED", "Round Proof #42", "Synthetic test only", "unit test",
                digests = mapOf(com.edgeore.app.edge.RoundProof.DIGEST_KEY to com.edgeore.app.edge.RoundProof.digest(proof)),
            ),
        )
        val p = if (tamperProof) JSONObject(proof).put("readSlot", 2).toString() else proof
        return temp.newFile().apply { writeText(com.edgeore.app.edge.RoundProof.bundle(log.export(), p)) }
    }

    @Test fun acceptsBundleAndListsUnverified() {
        val (code, out) = run("--round-proof", bundle().path)
        assertEquals(out, 0, code)
        assertTrue(out.contains("ROUND PROOF: PASS"))
        assertTrue(out.contains("Winning square (re-derived): 8"))
        assertTrue(out.contains("Not verified:"))
    }

    @Test fun rejectsProofNotCoveredBySignedReceipt() {
        val (code, out) = run("--round-proof", bundle(tamperProof = true).path)
        assertEquals(1, code)
        assertTrue(out.contains("not in any verified receipt"))
    }

    @Test fun rejectsTamperedReceiptInBundle() {
        val f = bundle()
        val doc = JSONObject(f.readText())
        val ex = JSONObject(doc.getString("receiptExport"))
        val rec = ex.getJSONArray("receipts").getJSONObject(0)
        rec.put("body", JSONObject(rec.getString("body")).put("title", "Changed").toString())
        f.writeText(doc.put("receiptExport", ex.toString()).toString())
        assertEquals(1, run("--round-proof", f.path).first)
    }
}

class OreProofV0CheckerTest {
    @get:Rule val temp = TemporaryFolder()

    private fun run(path: String): Pair<Int, String> {
        val bytes = ByteArrayOutputStream()
        val stream = PrintStream(bytes)
        return checkReceipt(arrayOf(path), stream, stream) to bytes.toString("UTF-8")
    }

    private fun le() = java.nio.ByteBuffer.allocate(com.edgeore.app.edge.RoundRules.ROUND_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)

    private fun round(): ByteArray {
        val dep = LongArray(25).also { it[7] = 1_000_000_000L; it[2] = 500_000_000L }
        val (adm, prot) = com.edgeore.app.edge.RoundRules.fees(dep.toList(), 7)
        val b = le()
        b.put(109.toByte()).put(ByteArray(7)).putLong(42)
        dep.forEach { b.putLong(it) }
        dep.forEach { b.putLong(it) }
        dep.forEach { b.putLong(if (it > 0) 1 else 0) }
        b.putLong(7).put(ByteArray(24))
        b.putLong(1).putLong(0).put(ByteArray(32))
        b.putLong(100_000_000_000L)
        repeat(24) { b.putLong(0) }
        b.putLong(prot).putLong(dep.sum() - adm - prot).putLong(2)
        val solo = com.edgeore.app.edge.RoundRules.isSolo(42, 7)
        b.put(if (solo) ByteArray(32) else com.edgeore.app.crypto.Base58.decodePublicKey(com.edgeore.app.edge.RoundRules.SPLIT_ADDRESS))
        return b.array()
    }

    private fun miner(auth: ByteArray): ByteArray {
        val b = java.nio.ByteBuffer.allocate(com.edgeore.app.edge.RoundRules.MINER_PREFIX).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put(103.toByte()).put(ByteArray(7)).put(auth).putLong(0).putLong(0).putLong(0)
        repeat(25) { b.putLong(if (it == 7) 100_000_000L else 0L) }
        repeat(25) { b.putLong(if (it == 7) 100_000_000L else 0L) }
        repeat(25) { b.putLong(0) }
        b.putLong(42)
        return b.array()
    }

    private fun config(admin: Long = 100, protocol: Long = 1000): ByteArray {
        val b = java.nio.ByteBuffer.allocate(232).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put(101.toByte()).put(ByteArray(7)).put(ByteArray(64)).putLong(admin).put(ByteArray(64)).putLong(protocol)
        return b.array()
    }

    private fun file(
        cfg: ByteArray? = config(),
        withMiner: Boolean = true,
        edit: (String) -> String = { it },
    ): java.io.File {
        val gen = org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator().apply { init(org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters(java.security.SecureRandom())) }
        val kp = gen.generateKeyPair()
        val pub = (kp.public as org.bouncycastle.crypto.params.Ed25519PublicKeyParameters).encoded
        val msg = com.edgeore.app.solana.OreDeployMessage.build(com.edgeore.app.solana.OreDeployMessage.Deploy(pub, 42, 100_000_000L, 1 shl 7, ByteArray(32) { 9 }))
        val sig = org.bouncycastle.crypto.signers.Ed25519Signer().run { init(true, kp.private); update(msg, 0, msg.size); generateSignature() }
        val log = ReceiptLog(java.io.File(temp.root, "log-${System.nanoTime()}.jsonl"), SoftwareReceiptSigner())
        val deploy =
            log.append(
                ReceiptDraft(
                    ReceiptKind.REVIEW, "SUBMITTED", "ORE deploy", "Synthetic test only", "unit test", "mainnet-beta",
                    solanaSignature = com.edgeore.app.crypto.Base58.encode(sig),
                    digests = mapOf("messageSha256" to com.edgeore.app.crypto.Sha256.hex(msg)),
                    lamports = 100_000_000L,
                    wallet = WalletEvidence(java.util.Base64.getEncoder().encodeToString(msg), com.edgeore.app.crypto.Base58.encode(pub), com.edgeore.app.crypto.Base58.encode(sig)),
                ),
            )
        val proof =
            com.edgeore.app.edge.RoundProof.build(
                com.edgeore.app.ore.OreV3.roundAddress(42), round(), 1, "test",
                if (withMiner) "m" else null, if (withMiner) miner(pub) else null, cfg,
            )
        log.append(
            ReceiptDraft(
                ReceiptKind.POLICY, "ROUND_PROOF_RECORDED", "Round Proof #42", "Synthetic test only", "unit test",
                digests = mapOf(com.edgeore.app.edge.RoundProof.DIGEST_KEY to com.edgeore.app.edge.RoundProof.digest(proof)),
                relatesTo = deploy.id,
            ),
        )
        val text = com.edgeore.app.edge.OreProofV0.build(deploy.id, deploy.body, log.export(), proof, mapOf("note" to "ignored"))
        return temp.newFile("r${System.nanoTime()}.oreproof").apply { writeText(edit(text)) }
    }

    @Test fun allClaimsPassAndFileIsCanonical() {
        val f = file()
        val t = f.readText()
        assertEquals(com.edgeore.app.edge.OreProofV0.canonical(JSONObject(t)), t)
        assertFalse(Regex("\\d\\.\\d").containsMatchIn(JSONObject(t).getJSONObject("chain").toString().substringBefore("round_proof_json")))
        val (code, out) = run(f.path)
        assertEquals(out, 0, code)
        for (p in listOf("P1 intent: PASS", "P2 winner: PASS", "P3 split/solo: PASS", "P4 SOL accounting: PASS", "P5 ORE share: PASS")) assertTrue(out, out.contains(p))
    }

    @Test fun missingMinerAndConfigAreUnverifiedNotPass() {
        val (code, out) = run(file(cfg = null, withMiner = false).path)
        assertEquals(out, 0, code)
        assertTrue(out.contains("P4 SOL accounting: UNVERIFIED"))
        assertTrue(out.contains("P5 ORE share: UNVERIFIED"))
    }

    @Test fun configRatesDifferentFromProgramFail() {
        val (code, out) = run(file(cfg = config(admin = 50)).path)
        assertEquals(1, code)
        assertTrue(out.contains("P4 SOL accounting: FAIL"))
    }

    @Test fun editedWinnerFails() {
        val f = file()
        val o = JSONObject(f.readText())
        o.getJSONObject("chain").put("winner_square", 3)
        f.writeText(com.edgeore.app.edge.OreProofV0.canonical(o))
        val (code, out) = run(f.path)
        assertEquals(1, code)
        assertTrue(out.contains("P2 winner: FAIL"))
    }

    @Test fun nonCanonicalFileFails() {
        val f = file()
        f.writeText(JSONObject(f.readText()).toString(2))
        assertEquals(1, run(f.path).first)
    }
}

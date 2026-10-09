package com.edgeore.verifier

import java.io.File

/** Writes a generated sample export for the end-to-end check (`./gradlew :verifier-cli:demoReceipt`). */
fun main(args: Array<String>) {
    val out = File(args.single())
    val work = File(out.parentFile, "receipt-log").apply { deleteRecursively() }
    out.parentFile.mkdirs()
    out.writeText(Fixtures.export(work).json)
    println("wrote ${out.path} (generated fixture: software test key, never-funded wallet key; not a real transfer)")
}

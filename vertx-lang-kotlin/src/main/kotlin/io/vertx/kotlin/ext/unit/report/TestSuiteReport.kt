package io.vertx.kotlin.ext.unit.report

import io.vertx.core.streams.WriteStream
import io.vertx.ext.unit.report.TestCaseReport
import io.vertx.ext.unit.report.TestSuiteReport
import io.vertx.kotlin.coroutines.awaitResult

/**
 * Pipe this <code>ReadStream</code> to the <code>WriteStream</code>.
 * <p>
 * Elements emitted by this stream will be written to the write stream until this stream ends or fails.
 * <p>
 * Once this stream has ended or failed, the write stream will be ended and the <code>handler</code> will be
 * called with the result.
 *
 * @param dst the destination write stream
 *
 * <p/>
 * NOTE: This function has been automatically generated from the [io.vertx.ext.unit.report.TestSuiteReport original] using Vert.x codegen.
 */
suspend fun TestSuiteReport.pipeToAwait(dst : WriteStream<TestCaseReport>) : Unit {
  return awaitResult{
    this.pipeTo(dst, { ar -> it.handle(ar.mapEmpty()) })}
}

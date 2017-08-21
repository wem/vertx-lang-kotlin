package io.vertx.kotlin.coroutines

import io.vertx.core.Vertx
import io.vertx.core.VertxException
import io.vertx.core.eventbus.Message
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.streams.ReadStream
import io.vertx.core.streams.WriteStream
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.RunTestOnContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.hamcrest.core.Is
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by stream.
 */
@RunWith(VertxUnitRunner::class)
class VertxCoroutineTest {

  @Rule
  @JvmField val rule = RunTestOnContext()
  private val ADDRESS1 = "address1"
  private val ADDRESS2 = "address2"
  private val ADDRESS3 = "address3"

  private lateinit var vertx: Vertx
  private lateinit var ai: AsyncInterface

  @Before
  fun before() {
    vertx = rule.vertx()
    attachVertxToCoroutine(vertx)
    ai = AsyncInterfaceImpl(vertx)
  }

  @Test
  fun testContext(testContext: TestContext) {
    val async = testContext.async()
    val ctx = vertx.orCreateContext
    Assert.assertTrue(ctx.isEventLoopContext)
    async.complete()
  }

  @Test
  fun testSleep(testContext: TestContext) {
    val async = testContext.async()
    runVertxCoroutine {
      val th = Thread.currentThread()
      val cnt = AtomicInteger()
      val periodicTimer = vertx.periodicStream(1L).handler {
        Assert.assertSame(Thread.currentThread(), th)
        cnt.incrementAndGet()
      }
      Assert.assertSame(Thread.currentThread(), th)
      asyncEvent<Long> { h -> vertx.setTimer(1000L, h) }
      Assert.assertTrue(cnt.get() > 900)
      Assert.assertSame(Thread.currentThread(), th)
      periodicTimer.cancel()
      async.complete()
    }
  }

  @Test
  fun testFiberHandler(testContext: TestContext) {
    val async = testContext.async()
    val server = vertx.createHttpServer(HttpServerOptions().setPort(8080))
    server.requestHandler({ req ->
      runVertxCoroutine {
        val res = asyncResult<String> { h -> ai.methodWithParamsAndHandlerNoReturn("oranges", 23, h) }
        Assert.assertEquals("oranges23", res)
        req.response().end()
      }
    })
    server.listen { res ->
      Assert.assertTrue(res.succeeded())
      val client = vertx.createHttpClient(HttpClientOptions().setDefaultPort(8080))
      client.getNow("/somepath") { resp ->
        Assert.assertTrue(resp.statusCode() == 200)
        client.close()
        server.close { _ -> async.complete() }
      }
    }
  }

  @Test
  fun testExecSyncMethodWithParamsAndHandlerNoReturn(testContext: TestContext) {
    val async = testContext.async()
    val th = Thread.currentThread()
    runVertxCoroutine {
      val res = asyncResult<String> { h -> ai.methodWithParamsAndHandlerNoReturn("oranges", 23, h) }
      Assert.assertEquals("oranges23", res)
      Assert.assertSame(Thread.currentThread(), th)
      async.complete()
    }
  }

  @Test
  fun testExecSyncMethodWithNoParamsAndHandlerNoReturn(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    val res = asyncResult<String> { h -> ai.methodWithNoParamsAndHandlerNoReturn(h) }
    Assert.assertEquals("wibble", res)
    async.complete()
  }

  @Test
  fun testExecSyncMethodWithParamsAndHandlerWithReturn(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    val res = asyncResult<String> { h -> ai.methodWithParamsAndHandlerWithReturn("oranges", 23, h) }
    Assert.assertEquals("oranges23", res)
    async.complete()
  }

  @Test
  fun testExecSyncMethodWithNoParamsAndHandlerWithReturn(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    val res = asyncResult<String> { h -> ai.methodWithNoParamsAndHandlerWithReturn(h) }
    Assert.assertEquals("wibble", res)
    async.complete()
  }

  @Test
  fun testExecSyncMethodWithNoParamsAndHandlerWithReturnNoTimeout(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    val res = asyncResult<String>(2000) { h -> ai.methodWithNoParamsAndHandlerWithReturnTimeout(h, 1000) }
    testContext.assertEquals("wibble", res)
    async.complete()
  }

  @Test
  fun testExecSyncMethodWithNoParamsAndHandlerWithReturnTimeout(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    try {
      asyncResult<String>(500) { h -> ai.methodWithNoParamsAndHandlerWithReturnTimeout(h, 1000) }
      testContext.fail()
    } catch(e: TimeoutException) {
      async.complete()
    }
  }

  @Test
  fun testExecSyncMethodWithParamsAndHandlerInterface(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    val returned = asyncResult<ReturnedInterface> { h -> ai.methodWithParamsAndHandlerInterface("apples", 123, h) }
    Assert.assertNotNull(returned)
    val res = asyncResult<String> { h -> returned.methodWithParamsAndHandlerNoReturn("bananas", 100, h) }
    testContext.assertEquals(res, "bananas100")
    async.complete()
  }

  @Test
  fun testExecSyncMethodThatFails(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    try {
      asyncResult<String> { h -> ai.methodThatFails("oranges", h) }
      testContext.fail("Should throw exception")
    } catch (e: Exception) {
      testContext.assertEquals("oranges", e?.message)
      async.complete()
    }
  }

  @Test
  fun testReceiveEvent(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    val start = System.currentTimeMillis()
    val tid = asyncEvent<Long> { h -> vertx.setTimer(500, h) }
    val end = System.currentTimeMillis()
    Assert.assertTrue(end - start >= 500)
    Assert.assertTrue(tid >= 0)
    async.complete()
  }

  @Test
  fun testReceiveEventTimeout(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    try {
      asyncEvent<Long>(250) { h -> vertx.setTimer(500, h) }
    } catch (npe: NullPointerException) {
      Assert.assertThat<NullPointerException>(npe, Is.isA<NullPointerException>(NullPointerException::class.java))
    } catch (e: Exception) {
      Assert.assertTrue(false)
    } finally {
      async.complete()
    }
  }

  @Test
  fun testReceiveEventNoTimeout(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    val start = System.currentTimeMillis()
    val tid = asyncEvent<Long>(1000L) { h -> vertx.setTimer(500, h) }
    val end = System.currentTimeMillis()
    Assert.assertTrue(end - start >= 500)
    if (tid is Long) Assert.assertTrue(tid >= 0)
    else Assert.fail("can not cast tid type")
    async.complete()
  }

  @Test
  fun testEventMethodFailure(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    val cause = RuntimeException()
    try {
      asyncEvent<Any> { h -> throw cause }
      testContext.fail()
    } catch(e: Exception) {
      testContext.assertEquals(cause, e)
    }
    async.complete()
  }

  @Test
  fun testEventMethodFailureNoTimeout(testContext: TestContext) = runVertxCoroutine {
    val async = testContext.async()
    val cause = RuntimeException()
    try {
      asyncEvent<Any>(1000L) { h -> throw cause }
      testContext.fail()
    } catch(e: Exception) {
      testContext.assertEquals(cause, e)
    }
    async.complete()
  }

  @Test
  fun testReadStreamToChannel(testContext: TestContext) {
    val stream = TestStream<Int>()
    val capacity = 3;
    val expected = LinkedList<Int>()
    for (i in 0 until capacity) {
      expected.add(i);
    }
    val readStream : ReadStream<Int> = stream;
    val channel = toChannel(readStream, capacity)
    for (i in expected) {
      testContext.assertFalse(stream.writeQueueFull())
      stream.write(i)
    }
    assertTrue(stream.writeQueueFull());
    val list = LinkedList<Int>()
    runVertxCoroutine {
      for (item in channel) {
        list.add(item)
        testContext.assertEquals((capacity - list.size) >= capacity / 2, stream.writeQueueFull())
        if (list.size == expected.size) {
          break
        }
      }
    }
    assertEquals(expected, list)
    stream.write(-1)
    stream.write(-2)
    stream.end()
    val ended = AtomicBoolean()
    runVertxCoroutine {
      var count = -1
      for (item in channel) {
        assertEquals(count--, item);
      }
      testContext.assertEquals(-3, count)
      ended.set(true)
    }
    testContext.assertTrue(ended.get())
  }

  @Test
  fun testWriteStreamToChannel(testContext: TestContext) {
    val stream = TestStream<Int>()
    val capacity = 3;
    val expected = LinkedList<Int>()
    for (i in 0 until capacity) {
      expected.add(i);
    }
    val writeStream : WriteStream<Int> = stream;
    val channel = toChannel(writeStream, capacity)
    val received = LinkedList<Int>()
    stream.handler { elt -> received.add(elt) }
    runVertxCoroutine {
      for (elt in expected) {
        channel.send(elt)
        assertFalse(channel.isFull)
      }
    }
    testContext.assertEquals(expected, received)
    received.clear()
    stream.pause()
    val async = testContext.async()
    runVertxCoroutine {
      for (elt in expected) {
        channel.send(elt)
        testContext.assertFalse(channel.isFull)
      }
      channel.send(capacity) // Need an extra element for the inflight
      testContext.assertTrue(channel.isFull)
    }
    testContext.assertEquals(emptyList<Int>(), received)
    stream.resume()
    testContext.assertEquals(listOf(0, 1, 2, 3), received)
    runVertxCoroutine {
      channel.send(4)
    }
    testContext.assertEquals(listOf(0, 1, 2, 3, 4), received)
    testContext.assertFalse(stream.isEnded)
    channel.close()
    testContext.assertTrue(stream.isEnded)
    async.complete()
  }

  @Test
  fun testHandlerAdaptor(testContext: TestContext) {
    val async = testContext.async()
    val eb = vertx.eventBus()
    // Create a couple of consumers on different addresses
    // The adaptor allows handler to be used as a Channel
    val adaptor1 = streamAdaptor<Message<String>>()
    eb.consumer<String>(ADDRESS1).handler(adaptor1)

    val adaptor2 = streamAdaptor<Message<String>>()
    eb.consumer<String>(ADDRESS2).handler(adaptor2)

    // Set up a periodic timer to send messages to these addresses
    val start = System.currentTimeMillis()
    vertx.setPeriodic(10) { _ ->
      eb.send(ADDRESS1, "wibble")
      eb.send(ADDRESS2, "flibble")
    }
    runVertxCoroutine {
      for (i in 0..9) {
        val received1 = adaptor1.receive()
        Assert.assertEquals("wibble", received1.body())
        val received2 = adaptor2.receive()
        Assert.assertEquals("flibble", received2.body())
      }

      val end = System.currentTimeMillis()
      Assert.assertTrue(end - start >= 100)

      // Try a receive with timeout
      var received1 = adaptor1.receive(1000)

      if (received1 is Message<*>) Assert.assertEquals("wibble", received1.body())
      else Assert.fail("received1 cast type failed.")

      // And timing out
      val adaptor3 = streamAdaptor<Message<String>>()
      eb.consumer<String>(ADDRESS3).handler(adaptor3)
      val received3 = adaptor3.receive(100)
      Assert.assertNull(received3)

      // Try underlying channel
      val channel = adaptor1.channel
      Assert.assertNotNull(channel)
      received1 = channel.receive()
      Assert.assertEquals("wibble", received1.body())

      async.complete()
    }
  }

}

import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis

val threadName: String get() = Thread.currentThread().name
val println = { msg: String -> kotlin.io.println("[$threadName] $msg") }

fun main() {
    System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)

    coroutineBasics()
    print("----------\n")

    concurrentCoroutines()
    print("----------\n")

    coroutineContext()
    print("----------\n")

    lazyCoroutines()
    print("----------\n")

    runBlocking { suspendFunctionWithCoroutineScope() }
    print("----------\n")

    coroutineCancellations()
    print("----------\n")

    customCoroutineScope()
    print("----------\n")

    val result = globalScopeCoroutineAsync()
    runBlocking { println("globalScopeCoroutineAsync returned: ${result.await()}") }
    print("----------\n")

    coroutineThreadLocalData()
    print("----------\n")

    completableFutureInteroperability()
    print("----------\n")
}

fun coroutineBasics() {
    // coroutines must be started using a launcher function
    // runBlocking() is a coroutine launcher that allows the non-coroutine world to interface with the coroutine world
    // as the name implies, runBlocking starts a coroutine and blocks the calling thread until all the coroutines
    // inside have finished executing
    println("going to launch a coroutine and wait for it to complete")
    runBlocking {
        // delay() works like Thread.sleep() except it does not block the thread running the coroutine
        // instead, the thread is released to do other work, and the continuation of the coroutine is scheduled
        // for execution after the specified time elapses
        println("runBlocking is going to delay for 1s")
        delay(1000)
        println("runBlocking delay is done")

        // coroutines are suspend functions, which means they can invoke other suspend functions
        suspendFunctionBasics()
    }
    println("runBlocking completed execution")
}

suspend fun suspendFunctionBasics() {
    // suspend functions look like normal functions
    // the main difference is that they can invoke other suspend functions and when suspended,
    // they don't block the calling thread
    println("suspend function is going to delay for 1s")
    delay(1000)
    println("suspend function delay is done")
}

fun concurrentCoroutines() {
    val time = measureTimeMillis {
        // coroutines can be executed sequentially to take advantage of their non-blocking suspend capabilities
        // however, coroutines can also be executed concurrently to improve execution time
        runBlocking {
            // a coroutine can start other coroutines by using coroutine launchers
            // the launch() method starts a coroutine that doesn't return a value
            launch {
                launch {
                    println("launch#1.1 is going to delay for 2s")
                    delay(2000)
                    println("launch#1.1 delay is done")
                }

                println("launch#1 is going to delay for 1.75s")
                delay(1750)
                println("launch#1 delay is done")
            }

            // every launched coroutine has a corresponding `Job` which allows to track the coroutines state as well as
            // allow other coroutines to wait for their completion in a non-blocking manner
            val job = launch {
                println("launch#2 is going to delay for 1.5s")
                delay(1500)
                println("launch#2 delay is done")
            }
            println("launch#2 is active? ${job.isActive}")

            // on the other hand, coroutines that return a value are launched using the async() method
            // async() returns a `Deferred` object which is a `Job` that allows a coroutine to suspend waiting for its value
            val deferred = async {
                println("async is going to delay for 1s")
                delay(1000)
                println("async delay is done")
                123
            }

            // the Deferred.await() method suspends the calling coroutine until its value is ready
            val result = deferred.await()
            println("async returned: $result")

            // the Job.join() method suspends the calling coroutine until it has finished executing
            job.join()
            println("launch#2 is done")
        }
    }
    // coroutines use a structured concurrency model which means that each coroutine keeps track of their children
    // in other words, waiting for a coroutine implicitly waits for all their children coroutines (and descendants)
    println("runBlocking completed execution in ${time}ms, therefore all child coroutines finished executing as well")
}

fun coroutineContext() {
    // every coroutine is launched within the context of a `CoroutineScope`
    // all the lambda arguments of coroutine launchers are extension functions of `CoroutineScope` which is what allows
    // to invoke the CoroutineScope.launch() and CoroutineScope.async() extension functions within them
    //
    // a coroutine scope's main purpose is to store a `CoroutineContext` which is an immutable object that contains
    // the coroutine's Job, the coroutine's dispatcher, and other optional context elements
    //
    // a coroutine dispatcher specifies the thread(s) where coroutines will be executed
    // so far, all coroutines launched so far have not specified an explicit context
    // as a result, they have inherited their parent's context elements
    // the top-level runBlocking coroutine launcher utilizes a blocking event loop that runs on the invoking thread
    // as a result, even coroutines may run concurrently, they only one of them runs at a time, since they are all
    // invoked by the single, calling thread
    //
    // when using multi-core processors, execution time can be improved by utilizing a multi-threaded dispatcher
    // Dispatchers.Default is a multi-threaded dispatcher that is proportional to the number of processors (CPU cores)
    runBlocking(Dispatchers.Default) {
        launch {
            launch {
                println("launch#1.1 is going to delay for 1s")
                delay(1000)
                println("launch#1.1 delay is done")
            }
            println("launch#1 is going to delay for 1s")
            delay(1000)
            println("launch#1 delay is done")
        }

        // `CoroutineName` provides a custom name for debugging purposes
        // since a coroutine always inherits its parent context, it will be executed in `Dispatches.Default`
        launch(CoroutineName("MyCoroutineName")) {
            println("launch#2 is going to delay for 1s")
            delay(1000)
            println("launch#2 delay is done")
        }

        // a coroutine can change its context at any time by using withContext()
        // unlike launch() and async() which schedule new coroutines for concurrent execution
        // withContext() suspends the calling coroutine
        // this method is commonly used to switch between threads, most commonly the main UI thread
        //
        // additionally, since `CoroutineContext` instances are immutable, in order to specify more than one value
        // for a coroutine context, they must be composed using the add operator
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use {
            println("before SingleThread dispatcher execution")
            val result = withContext(it + CoroutineName("MySpecialCoroutine")) {
                println("running in SingleThread dispatcher")
                123
            }
            println("after SingleThread dispatcher execution which returned: $result")
        }
    }
    println("runBlocking completed execution")
}

fun lazyCoroutines() {
    println("before")
    runBlocking {
        // coroutines are normally scheduled for immediate execution
        // however, coroutines can also be lazily executed
        val job = launch(start = CoroutineStart.LAZY) {
            println("I have been lazily executed")
        }

        println("delaying for 1s before executing child coroutine")
        delay(1000)
        // note that if the child coroutine is never started
        // the parent coroutine (runBlocking) will never be considered complete and it would result in a deadlock
        job.start()
    }
    println("after")
}

suspend fun suspendFunctionWithCoroutineScope() = coroutineScope {
    // suspend functions do not have a `CoroutineScope` by default
    // but it's as easy as invoking coroutineScope() to get one
    val jobs = mutableListOf<Job>()
    jobs += launch { println("Delaying for 1s") }
    jobs += launch { println("Delaying for 1s") }
    jobs += launch { println("Delaying for 1s") }

    // a collection of Job objects can be waited with joinAll()
    jobs.joinAll()
    println("all launched jobs have completed")
}

@OptIn(DelicateCoroutinesApi::class)
fun coroutineCancellations() {
    runBlocking {
        // coroutines can be cancelled by invoking Job.cancel()
        var job = launch {
            delay(1000)
            println("this will never be printed")
        }
        delay(1)
        job.cancel()

        // cancelled coroutines throw a CancellationException when they are cancelled
        job = launch {
            try {
                delay(1000)
                println("this will never be printed")
            } catch (ex: CancellationException) {
                println("I got cancelled with a: ${ex::class.qualifiedName}")
            }
        }
        delay(1)
        job.cancel()

        // when an unhandled exception occurs in a coroutine initiated with launch() it propagates to its parent
        // the parent coroutine in turn propagates it to its parent until it reaches the root coroutine
        // the root coroutine is then cancelled with the unhandled exception
        // root coroutines can handle these exceptions by specifying a `CoroutineExceptionHandler` in their context
        // note that coroutine exception handlers ONLY apply for root coroutines
        //
        // unhandled exception in coroutines initiated with async() do not propagate to their parent and become visible
        // only when Deferred.await() is invoked
        job = GlobalScope.launch(CoroutineExceptionHandler { _, ex -> println("exception occurred: $ex") }) {
            launch {
                delay(100)
                println("implicitly cancelling parent coroutine by throwing an exception")
                throw Exception("LaunchError")
            }
            launch {
                delay(1000)
                println("this won't be printed since parent coroutine was cancelled by sibling's unhandled exception")
            }
        }
        job.join()

        // when you don't want unhandled exceptions to propagate to their parent
        // you can use SupervisorJob() or supervisorScope()
        // however, you'll have to treat each coroutine as a root coroutine (i.e. by using a
        supervisorScope {
            launch(CoroutineExceptionHandler { _, ex -> println("exception occurred: $ex") }) {
                delay(100)
                throw Exception("MyOtherError")
            }
            launch {
                delay(1000)
                println("this got printed because sibling's unhandled exception no longer cancels parent")
            }
        }

        // cancelling coroutines can also be done implicitly by wrapping them in a withTimeout() invocation
        // if the timeout elapses before the coroutine completes, the coroutine (and any children coroutines)
        // will be cancelled as well
        try {
            withTimeout(500) {
                launch {
                    try {
                        delay(1000)
                    } catch (ex: CancellationException) {
                        println("I got cancelled with a: ${ex::class.qualifiedName}")
                    }
                }
            }
        } catch (ex: CancellationException) {
            println("withTimeout threw a: ${ex::class.qualifiedName}")
        }

        // coroutine cancellation is cooperative, this means coroutines have to check whether they have been cancelled
        // built-in suspend functions like delay() check this by default, but if you have compute-intensive coroutines
        // you'll need periodically check whether the coroutine has been cancelled
        job = launch(Dispatchers.Default) {
            // option 1: check isActive
            // note that this example code wouldn't work with single-threaded dispatchers (like runBlocking()'s default)
            // since `job.cancel()` would never execute given that this coroutine would never yield the thread resource
            // to other coroutines
            launch {
                while (isActive) {
                    continue
                }
                println("coroutine with `isActive` has been cancelled")
            }

            // option 2: use yield
            // this is the preferred method, since it's guaranteed to work even on single-threaded coroutine dispatchers
            launch {
                try {
                    while (true) {
                        yield()
                    }
                } finally {
                    println("coroutine with yield() has been cancelled")
                }
            }
        }
        delay(1000)
        job.cancel()

        // finally, you can opt-out of coroutine cancellation by using NonCancellable
        // this should only be used on withContext() calls to ensure critical pieces of code that should not be
        // cancelled are not interrupted when a cancellation occurs
        // usage on coroutine launchers like launch() and async() would exclude those coroutines from the
        // structured concurrency model which means parent jobs won't wait for child jobs to complete
        job = launch {
            withContext(NonCancellable) {
                delay(1000)
                println("still ran without cancellation")
            }
        }
        delay(500)
        job.cancel()
    }
}

fun customCoroutineScope() {
    // aside from runBlocking() there's another way for the non-coroutine world to interface with the coroutine world
    // a custom CoroutineScope can be created using CoroutineScope()
    // this is very useful for web frameworks with async support (e.g. JAX-RS)
    // be sure to invoke CoroutineScope.cancel() when the request ends (or times out) so that any pending coroutines
    // are cancelled and resources are cleaned up
    class HttpServer {
        private val scope = CoroutineScope(Dispatchers.Default)

        fun beginRequest(done: (Any) -> Unit) {
            scope.launch {
                val one = scope.async {
                    delay(500)
                    1
                }
                val two = scope.async {
                    delay(500)
                    2
                }
                done(one.await() + two.await())
            }
        }

        fun endRequest() {
            scope.cancel()
        }
    }

    val server = HttpServer()
    server.beginRequest {
        println("got $it")
    }
    Thread.sleep(1000)
    server.endRequest()
}

@OptIn(DelicateCoroutinesApi::class)
fun globalScopeCoroutineAsync(): Deferred<Int> = GlobalScope.async {
    // GlobalScope provides an unsupervised mechanism for
    delay(500)
    123
}

fun coroutineThreadLocalData() {
    // thread-local data allows to isolate state on a per-thread basis
    // this is widely used by popular libraries like log4j for storing ThreadContext data,
    // which is commonly used for storing/logging entries associated with their request ID
    // since coroutines can switch their executing thread after a suspension, thread-local data can be lost consequently
    // to ensure thread-local data is retained, kotlin coroutines provide two mechanisms to persist them in coroutines
    Executors.newSingleThreadExecutor().asCoroutineDispatcher().use {
        // mechanism #1: ThreadLocal.asContextElement()
        val threadLocalData = ThreadLocal<Int?>()
        threadLocalData.set(123)
        runBlocking {
            // notice how the thread-local value is lost since the thread changed and we didn't do anything special
            launch(it) {
                println("launch#1 thread-local value: ${threadLocalData.get()}")
            }

            // notice how the thread-local value is retained across threads since we used ThreadLocal.asContextElement()
            launch(it + threadLocalData.asContextElement()) {
                println("launch#2 thread-local value: ${threadLocalData.get()}")
                launch(Dispatchers.Default) {
                    println("launch#2.1 thread-local value: ${threadLocalData.get()}")
                }
            }

            // notice how we can update the thread-local value to something else for the coroutine (and any children)
            launch(it + threadLocalData.asContextElement(456)) {
                println("launch#3 thread-local value: ${threadLocalData.get()}")
                launch(Dispatchers.Default) {
                    println("launch#3.1 thread-local value: ${threadLocalData.get()}")
                }
            }
        }

        // mechanism #2: ThreadContextElement
        LoggerContext.value = 123
        runBlocking {
            // notice how LoggerContext.value is lost since the thread changed and we didn't do anything special
            launch(it) {
                println("launch#1 LoggerContext value: ${LoggerContext.value}")
            }

            // notice how LoggerContext.value is retained across threads since we used CoroutineLoggerContext()
            launch(it + CoroutineLoggerContext()) {
                println("launch#2 LoggerContext value: ${LoggerContext.value}")
                launch(Dispatchers.Default) {
                    println("launch#2.1 LoggerContext value: ${LoggerContext.value}")
                }
            }

            // notice how we can update LoggerContext.value to something else for the coroutine (and any children)
            launch(it + CoroutineLoggerContext(456)) {
                println("launch#3 LoggerContext value: ${LoggerContext.value}")
                launch(Dispatchers.Default) {
                    println("launch#3.1 LoggerContext value: ${LoggerContext.value}")
                }
            }
        }
    }
}

// this is a demonstration of a class that encapsulates a non-accessible ThreadLocal instance
// logger contexts are common examples of this such as: `org.apache.logging.log4j.ThreadContext`
object LoggerContext {
    private val context = ThreadLocal<Int?>()

    var value: Int?
        get() = context.get()
        set(value) = context.set(value)
}

// this is a demonstration of how the LoggerContext can be captured/restored between thread switches
// log4j has an equivalent of this: `org.apache.logging.log4j.kotlin.CoroutineThreadContext`
class CoroutineLoggerContext(
    private val contextValue: Int? = LoggerContext.value
) : ThreadContextElement<Int?> {
    companion object Key : CoroutineContext.Key<CoroutineLoggerContext>

    override val key: CoroutineContext.Key<CoroutineLoggerContext>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext): Int? {
        val oldValue = LoggerContext.value
        LoggerContext.value = contextValue
        return oldValue
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Int?) {
        LoggerContext.value = oldState
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun completableFutureInteroperability() {
    // coroutines are interoperable with Java's `CompletableFuture`
    // a completable future can be awaited in a coroutine by using CompletableFuture.await()
    // likewise transforming a coroutine to a `CompletableFuture` is as easy calling
    // Job.asCompletableFuture() or Deferred.asCompletableFuture()
    val source = CompletableFuture<Int?>()

    val destination = GlobalScope.async {
        val value = source.await() ?: 0
        println("source resolved to: $value")
        value + 1
    }.asCompletableFuture()

    Thread.sleep(500)
    source.complete(1)
    destination.thenAccept {
        println("destination resolved to: $it")
    }.join()
}

main()

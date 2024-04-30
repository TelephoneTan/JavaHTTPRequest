package pub.telephone.javahttprequest.network.http

import pub.telephone.javapromise.async.kpromise.PromiseScope
import pub.telephone.javapromise.async.promise.PromiseCancelledBroadcast
import pub.telephone.javapromise.async.promise.PromiseSemaphore

private fun pub.telephone.javapromise.async.kpromise.PromiseCancelledBroadcast?.toJ() = this?.let {
    object :
        PromiseCancelledBroadcast,
        pub.telephone.javapromise.async.kpromise.PromiseCancelledBroadcast by it {}
}

class ToCurrentScope internal constructor(internal val scope: PromiseScope)

fun PromiseScope.http(builder: HTTPRequest.() -> Unit) = HTTPRequest(
    scopeCancelledBroadcast.toJ()
).apply(builder)

fun PromiseScope.http(semaphore: PromiseSemaphore, builder: HTTPRequest.() -> Unit) = HTTPRequest(
    scopeCancelledBroadcast.toJ(),
    semaphore
).apply(builder)

val PromiseScope.alignScope get() = ToCurrentScope(this)

fun HTTPRequest.clone(clonePolicy: ToCurrentScope): HTTPRequest = clone(
    clonePolicy.scope.scopeCancelledBroadcast.toJ()
)

fun HTTPRequest.clone(clonePolicy: ToCurrentScope, semaphore: PromiseSemaphore): HTTPRequest = clone(
    clonePolicy.scope.scopeCancelledBroadcast.toJ(),
    semaphore
)
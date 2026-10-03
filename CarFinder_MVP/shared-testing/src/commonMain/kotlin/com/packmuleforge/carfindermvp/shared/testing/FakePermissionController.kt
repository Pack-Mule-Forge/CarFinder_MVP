package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.PermissionController
import com.packmuleforge.carfindermvp.shared.platform.PermissionState
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A permission controller driven by the test. Each [request] takes the next scripted answer for its capability
 * (a grant when none is queued). With [holdRequests] set, a request suspends until [releaseRequest].
 */
class FakePermissionController(initial: PermissionState = PermissionState()) : PermissionController {
    private val state = MutableStateFlow(initial)
    override val status: StateFlow<PermissionState> = state.asStateFlow()

    /** Every capability requested, in order. */
    val requests = mutableListOf<Capability>()
    var refreshCount = 0
        private set
    var holdRequests = false
    private val answers = mutableMapOf<Capability, ArrayDeque<Boolean>>()
    private var pending: CompletableDeferred<Boolean>? = null

    val isRequestPending: Boolean get() = pending != null

    fun setStatus(capability: Capability, status: PermissionStatus) {
        state.value = state.value.with(capability, status)
    }

    fun enqueueAnswers(capability: Capability, vararg granted: Boolean) {
        answers.getOrPut(capability) { ArrayDeque() }.addAll(granted.toList())
    }

    fun releaseRequest(granted: Boolean) {
        checkNotNull(pending) { "No request is pending" }.complete(granted)
    }

    override suspend fun request(capability: Capability): Boolean {
        requests += capability
        val granted = if (holdRequests) {
            val deferred = CompletableDeferred<Boolean>()
            pending = deferred
            try {
                deferred.await()
            } finally {
                pending = null
            }
        } else {
            answers[capability]?.removeFirstOrNull() ?: true
        }
        setStatus(capability, if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED)
        return granted
    }

    override fun refresh() {
        refreshCount++
    }
}

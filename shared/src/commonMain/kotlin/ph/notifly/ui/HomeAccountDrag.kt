package ph.notifly.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates

/** The grid owns the gesture so moving or scrolling a handle out of composition cannot cancel it. */
internal class HomeAccountDrag(private val grid: LazyGridState) {
    var coordinates: LayoutCoordinates? = null
    val handles = mutableMapOf<Long, LayoutCoordinates>()
    var order by mutableStateOf<List<AccountBalance>?>(null)
        private set
    var draggedId by mutableStateOf<Long?>(null)
        private set
    private var pointer by mutableStateOf(Offset.Zero)
    private var anchor = Offset.Zero
    private var initialOrder = emptyList<AccountBalance>()
    private var lastTarget: Any? = null

    fun handleAt(point: Offset): Long? = handles.entries.firstOrNull { (_, handle) ->
        handle.isAttached && coordinates?.takeIf { it.isAttached }
            ?.localBoundingBoxOf(handle, clipBounds = false)?.contains(point) == true
    }?.key

    fun start(id: Long, point: Offset, accounts: List<AccountBalance>) {
        val item = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "account:$id" } ?: return
        initialOrder = accounts
        order = accounts
        draggedId = id
        pointer = point
        anchor = point - Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
    }

    fun move(delta: Offset = Offset.Zero) {
        pointer += delta
        val target = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key.toString().startsWith("account:") &&
                Rect(it.offset.x.toFloat(), it.offset.y.toFloat(),
                    (it.offset.x + it.size.width).toFloat(), (it.offset.y + it.size.height).toFloat()).contains(pointer)
        }
        if (target?.key == lastTarget) return
        lastTarget = target?.key
        val accounts = order ?: return
        val from = accounts.indexOfFirst { it.account.id == draggedId }
        val to = accounts.indexOfFirst { "account:${it.account.id}" == target?.key }
        if (from >= 0 && to >= 0 && from != to) {
            order = accounts.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun translation(id: Long): Offset {
        if (draggedId != id) return Offset.Zero
        val item = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "account:$id" } ?: return Offset.Zero
        return pointer - anchor - Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
    }

    suspend fun scrollAtEdge(edge: Float, step: Float) {
        val info = grid.layoutInfo
        val speed = when {
            pointer.y < info.viewportStartOffset + edge -> -step * ((info.viewportStartOffset + edge - pointer.y) / edge).coerceIn(0f, 1f)
            pointer.y > info.viewportEndOffset - edge -> step * ((pointer.y - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
        if (speed != 0f && grid.scrollBy(speed) != 0f) {
            lastTarget = null
            move()
        }
    }

    fun finish(save: (List<Long>) -> Unit) {
        order?.takeIf { it != initialOrder }?.let { save(it.map { account -> account.account.id }) }
        cancel()
    }

    fun cancel() {
        draggedId = null
        order = null
        lastTarget = null
    }
}

internal fun Modifier.accountDragGestures(
    state: HomeAccountDrag,
    enabled: Boolean,
    accounts: () -> List<AccountBalance>,
    save: (List<Long>) -> Unit,
): Modifier = pointerInput(state, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        // Read handles before LazyVerticalGrid's scroll detector consumes vertical movement.
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val id = state.handleAt(down.position) ?: return@awaitEachGesture
        down.consume()
        var dragging = false
        try {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (dragging && !change.isConsumed) { change.consume(); state.finish(save) }
                    break
                }
                if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    state.start(id, down.position, accounts())
                    state.move(change.position - down.position)
                    dragging = true
                } else if (dragging) state.move(change.position - change.previousPosition)
                if (dragging) change.consume()
            }
        } finally {
            state.cancel()
        }
    }
}

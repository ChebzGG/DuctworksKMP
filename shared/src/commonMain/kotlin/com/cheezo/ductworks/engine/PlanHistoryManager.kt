package com.cheezo.ductworks.engine

import com.cheezo.ductworks.model.PlanDocument

/**
 * Manages Undo and Redo snapshots with transaction support for smooth 60 FPS drags.
 */
class PlanHistoryManager(
    initialDocument: PlanDocument,
    private val maxHistorySize: Int = 100
) {
    private val undoStack = mutableListOf<PlanDocument>()
    private val redoStack = mutableListOf<PlanDocument>()
    private var current: PlanDocument = initialDocument
    private var transactionBaseline: PlanDocument? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val baselineState: PlanDocument? get() = transactionBaseline

    fun getCurrentDocument(): PlanDocument = current

    /**
     * Records a single discrete state change into the undo history.
     */
    fun pushState(newDocument: PlanDocument) {
        if (newDocument == current) return
        undoStack.add(current)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        current = newDocument
    }

    /**
     * Begins an interactive drag transaction. Remembers baseline document.
     */
    fun beginTransaction() {
        if (transactionBaseline == null) {
            transactionBaseline = current
        }
    }

    /**
     * Updates the current document during an active drag without pushing to undo stack.
     */
    fun updateDraft(draftDocument: PlanDocument) {
        current = draftDocument
    }

    /**
     * Commits the completed transaction into the undo stack.
     */
    fun commitTransaction(finalDocument: PlanDocument) {
        val baseline = transactionBaseline ?: current
        transactionBaseline = null

        if (baseline != finalDocument) {
            undoStack.add(baseline)
            if (undoStack.size > maxHistorySize) {
                undoStack.removeAt(0)
            }
            redoStack.clear()
        }
        current = finalDocument
    }

    /**
     * Cancels an active transaction, restoring baseline.
     */
    fun cancelTransaction(): PlanDocument {
        val baseline = transactionBaseline
        transactionBaseline = null
        if (baseline != null) {
            current = baseline
        }
        return current
    }

    fun undo(): PlanDocument {
        if (!canUndo) return current
        redoStack.add(current)
        current = undoStack.removeAt(undoStack.size - 1)
        return current
    }

    fun redo(): PlanDocument {
        if (!canRedo) return current
        undoStack.add(current)
        current = redoStack.removeAt(redoStack.size - 1)
        return current
    }

    fun reset(document: PlanDocument) {
        undoStack.clear()
        redoStack.clear()
        transactionBaseline = null
        current = document
    }
}

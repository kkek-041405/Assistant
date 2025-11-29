package com.kkek.assistant.System.touch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.kkek.assistant.model.ScrollDirection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityHelper @Inject constructor() {
    private var service: AccessibilityService? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tag = "AccessibilityHelper"

    fun setService(service: AccessibilityService?) {
        this.service = service
    }

    // --- HIGH-LEVEL ACTION COMMANDS ---

    fun click(x: Int, y: Int): Boolean {
        val service = this.service ?: return false
        Log.d(tag, "Performing click at ($x, $y)")
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        return service.dispatchGesture(gesture, null, null)
    }

    fun findAndClick(text: String): Boolean {
        val node = findNodeByText(text, clickable = true)
        return if (node != null) {
            val success = performClick(node)
            node.recycle()
            success
        } else {
            Log.w(tag, "Could not find clickable node with text: '$text'")
            false
        }
    }

    fun findAndClickById(viewId: String): Boolean {
        val node = findNodeById(viewId)
        return if (node != null) {
            val success = performClick(node)
            node.recycle()
            success
        } else {
            Log.w(tag, "Could not find node with ID: '$viewId'")
            false
        }
    }

    fun inputText(viewId: String?, text: String): Boolean {
        val service = this.service ?: return false
        val rootNode = service.rootInActiveWindow ?: return false

        val targetNode: AccessibilityNodeInfo? = if (viewId != null) {
            findNodeById(viewId)
        } else {
            rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        rootNode.recycle()

        return if (targetNode != null) {
            val success = performInputText(targetNode, text)
            targetNode.recycle()
            success
        } else {
            Log.w(tag, "Could not find node to input text.")
            false
        }
    }

    fun scroll(viewId: String, direction: ScrollDirection): Boolean {
        val node = findNodeById(viewId)
        return if (node != null) {
            val success = performScroll(node, direction)
            node.recycle()
            success
        } else {
            Log.w(tag, "Could not find node with ID: '$viewId' for scrolling")
            false
        }
    }

    // --- LOW-LEVEL ACCESSIBILITY ACTIONS ---

    fun performClick(node: AccessibilityNodeInfo): Boolean {
        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(tag, "Clicked on node: ${node.viewIdResourceName}. Success: $success")
        return success
    }

    fun performInputText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.d(tag, "Input text on node: ${node.viewIdResourceName}. Success: $success")
        return success
    }

    fun performScroll(node: AccessibilityNodeInfo, direction: ScrollDirection): Boolean {
        val action = when (direction) {
            ScrollDirection.FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val success = node.performAction(action)
        Log.d(tag, "Scrolled on node: ${node.viewIdResourceName}. Direction: $direction. Success: $success")
        return success
    }

    fun performGlobalAction(action: Int): Boolean {
        val service = this.service ?: return false
        val success = service.performGlobalAction(action)
        Log.d(tag, "Performed global action: $action. Success: $success")
        return success
    }

    // --- FINDER METHODS ---

    fun findNodeByText(text: String, clickable: Boolean = false): AccessibilityNodeInfo? {
        val service = this.service ?: return null
        val rootNode = service.rootInActiveWindow ?: return null
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        rootNode.recycle()

        return nodes.firstOrNull { !clickable || it.isClickable }?.also {
            nodes.forEach { node -> if (node != it) node.recycle() }
        }
    }

    fun findNodeById(viewId: String): AccessibilityNodeInfo? {
        val service = this.service ?: return null
        val rootNode = service.rootInActiveWindow ?: return null
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        rootNode.recycle()
        return nodes.firstOrNull()?.also {
            nodes.forEach { node -> if (node != it) node.recycle() }
        }
    }

    // --- INSPECTION METHODS ---

    fun inspectCurrentScreen() {
        val service = this.service ?: return
        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
            Log.w(tag, "Root node is null, cannot inspect screen.")
            return
        }
        logNodeHierarchy(rootNode, 0)
        rootNode.recycle()
    }

    private fun logNodeHierarchy(node: AccessibilityNodeInfo, depth: Int) {
        val indent = "  ".repeat(depth)
        val nodeText = node.text?.let { "text: \"$it\"" } ?: ""
        val contentDesc = node.contentDescription?.let { "desc: \"$it\"" } ?: ""
        val viewId = node.viewIdResourceName ?: ""

        Log.d(tag, "$indent[${node.className}] $viewId $nodeText $contentDesc Clickable: ${node.isClickable}")

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                logNodeHierarchy(child, depth + 1)
                child.recycle()
            }
        }
    }
}

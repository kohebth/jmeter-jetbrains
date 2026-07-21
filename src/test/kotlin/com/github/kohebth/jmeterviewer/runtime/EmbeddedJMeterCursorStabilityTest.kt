package com.github.kohebth.jmeterviewer.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.event.TreeSelectionListener
import javax.swing.text.JTextComponent
import javax.swing.tree.TreeModel

class EmbeddedJMeterCursorStabilityTest {
    @Test
    fun dirtyChecksDoNotReconfigureTheVisibleForm() {
        ExternalJMeterTestSupport.openRuntime().use { runtime ->
            onEdt {
                runtime.withContextClassLoader {
                    val modelType = runtime.classLoader.loadClass(
                        "org.apache.jmeter.gui.tree.JMeterTreeModel",
                    )
                    val listenerType = runtime.classLoader.loadClass(
                        "org.apache.jmeter.gui.tree.JMeterTreeListener",
                    )
                    val guiPackageType = runtime.classLoader.loadClass(
                        "org.apache.jmeter.gui.GuiPackage",
                    )
                    val actionNames = runtime.classLoader.loadClass(
                        "org.apache.jmeter.gui.action.ActionNames",
                    )
                    val dirtyType = runtime.classLoader.loadClass(
                        "org.apache.jmeter.gui.action.CheckDirty",
                    )

                    val model = modelType.getDeclaredConstructor().newInstance()
                    val listener = listenerType.getConstructor(modelType).newInstance(model)
                    val tree = JTree(model as TreeModel)
                    listenerType.getMethod("setJTree", JTree::class.java).invoke(listener, tree)
                    listenerType.getMethod("setActionHandler", ActionListener::class.java).invoke(
                        listener,
                        ActionListener { },
                    )
                    tree.addTreeSelectionListener(listener as TreeSelectionListener)

                    guiPackageType.getMethod("initInstance", listenerType, modelType).invoke(
                        null,
                        listener,
                        model,
                    )
                    val guiPackage = guiPackageType.getMethod("getInstance").invoke(null)
                    try {
                        tree.setSelectionRow(1)
                        val currentGui = guiPackageType.getMethod("getCurrentGui").invoke(guiPackage)
                            as JComponent
                        val dirty = dirtyType.getDeclaredConstructor().newInstance()
                        val doAction = dirtyType.getMethod("doAction", ActionEvent::class.java)
                        val treeSnapshot = modelType.getMethod("getTestPlan").invoke(model)
                        doAction.invoke(
                            dirty,
                            ActionEvent(
                                treeSnapshot,
                                0x4a4d58,
                                actionNames.getField("SUB_TREE_SAVED").get(null) as String,
                            ),
                        )

                        val field = findEditableTextArea(currentGui).also { textArea ->
                            textArea.text = "prefix-middle-suffix"
                            textArea.caretPosition = 7
                            textArea.moveCaretPosition(13)
                        }

                        guiPackageType.getMethod("updateCurrentNodePreservingEditorState")
                            .invoke(guiPackage)
                        doAction.invoke(
                            dirty,
                            ActionEvent(
                                this,
                                0x4a4d58,
                                actionNames.getField("CHECK_DIRTY").get(null) as String,
                            ),
                        )

                        assertEquals("prefix-middle-suffix", field.text)
                        assertEquals(13, field.caret.dot)
                        assertEquals(7, field.caret.mark)
                    } finally {
                        guiPackageType.getMethod("disposeInstance", guiPackageType)
                            .invoke(null, guiPackage)
                    }
                }
            }
        }
    }

    private fun findEditableTextArea(root: JComponent): JTextComponent {
        val field = descendants(root)
            .filterIsInstance<JTextArea>()
            .firstOrNull { it.isEditable && it.isVisible }
        assertNotNull(field, "Expected the native Test Plan form to contain an editable text area")
        return checkNotNull(field)
    }

    private fun descendants(root: Component): Sequence<Component> = sequence {
        yield(root)
        if (root is Container) {
            root.components.forEach { child ->
                yieldAll(descendants(child))
            }
        }
    }

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return action()
        }
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {
            try {
                result.set(action())
            } catch (throwable: Throwable) {
                failure.set(throwable)
            }
        }
        failure.get()?.let { throw it }
        return result.get()
    }
}

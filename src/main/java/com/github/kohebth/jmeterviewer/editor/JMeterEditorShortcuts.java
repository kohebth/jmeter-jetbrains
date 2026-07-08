package com.github.kohebth.jmeterviewer.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.KeyboardFocusManager;
import java.awt.event.*;
import java.util.function.Supplier;

public final class JMeterEditorShortcuts {
    private JMeterEditorShortcuts() {
    }

    public static void install(JComponent component,
                        Disposable parent,
                        Runnable save,
                        Runnable reload,
                        Runnable run,
                        Runnable stop,
                        Runnable validate,
                        Runnable commands) {
        bind(component, parent, KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, "jmeter.save", save);
        bind(component, parent, KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK, "jmeter.reload", reload);
        bind(component, parent, KeyEvent.VK_F5, 0, "jmeter.run", run);
        bind(component, parent, KeyEvent.VK_F5, InputEvent.SHIFT_DOWN_MASK, "jmeter.stop", stop);
        bind(component, parent, KeyEvent.VK_F8, 0, "jmeter.validate", validate);
        bind(component, parent, KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                "jmeter.commands", commands);
    }

    public static void installTreeEditing(JComponent component,
                                   Disposable parent,
                                   Supplier<JMeterTreeActions> actions) {
        bindWhenSafe(component, parent, shortcutSet(IdeActions.ACTION_COPY, KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK),
                "jmeter.copy", () -> withActions(actions, JMeterTreeActions::copySelected));
        bindWhenSafe(component, parent, shortcutSet(IdeActions.ACTION_CUT, KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK),
                "jmeter.cut", () -> withActions(actions, JMeterTreeActions::cutSelected));
        bindWhenSafe(component, parent, shortcutSet(IdeActions.ACTION_PASTE, KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK),
                "jmeter.paste", () -> withActions(actions, JMeterTreeActions::pasteIntoSelected));
        bindWhenSafe(component, parent, shortcutSet(IdeActions.ACTION_DELETE, KeyEvent.VK_DELETE, 0),
                "jmeter.delete", () -> withActions(actions, JMeterTreeActions::deleteSelected));
        bindWhenSafe(component, parent,
                shortcutSet(IdeActions.ACTION_SELECT_ALL, KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
                "jmeter.selectAll", () -> withActions(actions, JMeterTreeActions::selectAllVisible));
    }

    private static void withActions(Supplier<JMeterTreeActions> supplier, ActionConsumer action) {
        JMeterTreeActions actions = supplier.get();
        if (actions != null) {
            action.accept(actions);
        }
    }

    private static void bind(JComponent component,
                             Disposable parent,
                             int key,
                             int modifiers,
                             String name,
                             Runnable action) {
        bind(component, parent, explicit(key, modifiers), name, action, false);
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(key, modifiers), name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private static void bindWhenSafe(JComponent component,
                                     Disposable parent,
                                     ShortcutSet shortcuts,
                                     String name,
                                     Runnable action) {
        bind(component, parent, shortcuts, name, action, true);
    }

    private static void bind(JComponent component,
                             Disposable parent,
                             ShortcutSet shortcuts,
                             String name,
                             Runnable action,
                             boolean skipTextEditing) {
        new AnAction(name) {
            @Override
            public void update(@NotNull AnActionEvent event) {
                event.getPresentation().setEnabled(!skipTextEditing || !isTextEditingActive());
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                if (skipTextEditing && isTextEditingActive()) {
                    return;
                }
                action.run();
            }
        }.registerCustomShortcutSet(shortcuts, component, parent);
    }

    private static boolean isTextEditingActive() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent;
    }

    private static ShortcutSet shortcutSet(String actionId, int fallbackKey, int fallbackModifiers) {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        ShortcutSet shortcuts = action == null ? null : action.getShortcutSet();
        return shortcuts != null && shortcuts.getShortcuts().length > 0
                ? shortcuts
                : explicit(fallbackKey, fallbackModifiers);
    }

    private static ShortcutSet explicit(int key, int modifiers) {
        return new CustomShortcutSet(KeyStroke.getKeyStroke(key, modifiers));
    }

    private interface ActionConsumer {
        public void accept(JMeterTreeActions actions);
    }
}

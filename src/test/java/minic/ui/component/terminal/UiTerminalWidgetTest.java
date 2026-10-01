package minic.ui.component.terminal;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.TerminalPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.SwingUtilities;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.awt.font.TextHitInfo;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.AttributedString;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real JediTerm keyboard processing, without a window, native process, or OS input injection. */
@EnabledIfSystemProperty(named = "minic.ui.test", matches = "true")
final class UiTerminalWidgetTest {
    @ParameterizedTest
    @ValueSource(ints = { KeyEvent.VK_UNDEFINED, 229 })
    void undefinedImeKeyPressesDoNotSendNulOrSwallowCommittedChinese(int keyCode) throws Exception {
        try (Fixture terminal = new Fixture()) {
            terminal.onPanel(panel -> {
                // SwingNode maps FX CHAR_UNDEFINED to char 0 while Windows IME owns the key.
                for (int i = 0; i < 5; i++) pressed(panel, keyCode, '\0', 0);
                typed(panel, '中', 0);
                typed(panel, '文', 0);
            });
            assertEquals("中文", terminal.output(), "only the committed text must reach the program, once");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { KeyEvent.VK_UNDEFINED, 229 })
    void imeKeyPressResetsThePreviousHandledControlKeyState(int keyCode) throws Exception {
        try (Fixture terminal = new Fixture()) {
            terminal.onPanel(panel -> {
                pressed(panel, KeyEvent.VK_C, '\3', InputEvent.CTRL_DOWN_MASK);
                typed(panel, '\3', InputEvent.CTRL_DOWN_MASK);
                pressed(panel, keyCode, '\0', 0);
                typed(panel, '你', 0);
                typed(panel, '好', 0);
            });
            assertEquals("\3你好", terminal.output(),
                    "normalizing the IME press must reset JediTerm's ignore-next-typed state");
        }
    }

    @Test
    void characterlessNavigationAndFunctionKeysStillUseTerminalMappings() throws Exception {
        try (Fixture terminal = new Fixture()) {
            int[] keys = { KeyEvent.VK_UP, KeyEvent.VK_LEFT, KeyEvent.VK_HOME, KeyEvent.VK_F1, KeyEvent.VK_F5 };
            ByteArrayOutputStream expected = new ByteArrayOutputStream();
            onEdt(() -> {
                TerminalPanel panel = terminal.widget.getTerminalPanel();
                for (int key : keys) {
                    byte[] sequence = terminal.widget.getTerminalStarter().getCode(key, 0);
                    assertNotNull(sequence, "the terminal must have a mapping for key " + key);
                    assertTrue(sequence.length > 0);
                    expected.writeBytes(sequence);
                    pressed(panel, key, '\0', 0);
                }
                return null;
            });
            assertArrayEquals(expected.toByteArray(), terminal.outputBytes());
        }
    }

    @ParameterizedTest
    @ValueSource(chars = { '\0', ' ' })
    void intentionalControlSpaceStillSendsExactlyOneNul(char keyChar) throws Exception {
        try (Fixture terminal = new Fixture()) {
            terminal.onPanel(panel -> {
                pressed(panel, KeyEvent.VK_SPACE, keyChar, InputEvent.CTRL_DOWN_MASK);
                typed(panel, '\0', InputEvent.CTRL_DOWN_MASK);
            });
            assertArrayEquals(new byte[] { 0 }, terminal.outputBytes());
        }
    }

    @Test
    void explicitControlAtAndControlTwoKeepTheirNulInput() throws Exception {
        try (Fixture terminal = new Fixture()) {
            terminal.onPanel(panel -> {
                pressed(panel, KeyEvent.VK_AT, '\0', InputEvent.CTRL_DOWN_MASK);
                typed(panel, '\0', InputEvent.CTRL_DOWN_MASK);
                pressed(panel, KeyEvent.VK_2, '\0', InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
                typed(panel, '\0', InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
            });
            assertArrayEquals(new byte[] { 0, 0 }, terminal.outputBytes());
        }
    }

    @Test
    void normalEnglishStillSendsEachTypedCharacterOnce() throws Exception {
        try (Fixture terminal = new Fixture()) {
            terminal.onPanel(panel -> {
                for (char character : "hello world".toCharArray()) {
                    pressed(panel, KeyEvent.getExtendedKeyCodeForChar(character), character, 0);
                    typed(panel, character, 0);
                }
            });
            assertEquals("hello world", terminal.output());
        }
    }

    @Test
    void awtImeCompositionStaysLocalAndCommittedChineseIsSentOnce() throws Exception {
        try (Fixture terminal = new Fixture()) {
            terminal.inputMethod("ni hao", 0);
            assertEquals("", terminal.output(), "uncommitted composition must not be sent to stdin");
            terminal.inputMethod("你好", 2);
            assertEquals("你好", terminal.output());
        }
    }

    private static void pressed(TerminalPanel panel, int key, char character, int modifiers) {
        panel.processKeyEvent(new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                modifiers, key, character));
    }

    private static void typed(TerminalPanel panel, char character, int modifiers) {
        panel.processKeyEvent(new KeyEvent(panel, KeyEvent.KEY_TYPED, System.currentTimeMillis(),
                modifiers, KeyEvent.VK_UNDEFINED, character));
    }

    @FunctionalInterface
    private interface PanelAction {
        void run(TerminalPanel panel) throws Exception;
    }

    private static final class Fixture implements AutoCloseable {
        private final CapturingConnector connector = new CapturingConnector();
        private final UiTerminalWidget widget;

        Fixture() throws Exception {
            widget = onEdt(() -> {
                UiTerminalWidget created = new UiTerminalWidget();
                created.setTtyConnector(connector);
                created.start();
                return created;
            });
            try {
                assertTrue(connector.readerReady.await(5, TimeUnit.SECONDS),
                        "JediTerm must install the real terminal key listener before receiving events");
            } catch (Exception | AssertionError failure) {
                close();
                throw failure;
            }
        }

        void onPanel(PanelAction action) throws Exception {
            onEdt(() -> {
                action.run(widget.getTerminalPanel());
                return null;
            });
        }

        void inputMethod(String text, int committed) throws Exception {
            onPanel(panel -> {
                // Bypass native focus dispatch, not JediTerm's actual protected IME implementation.
                Method process = TerminalPanel.class.getDeclaredMethod("processInputMethodEvent", InputMethodEvent.class);
                process.setAccessible(true);
                process.invoke(panel, new InputMethodEvent(panel, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                        new AttributedString(text).getIterator(), committed, TextHitInfo.leading(0), null));
            });
        }

        byte[] outputBytes() throws Exception {
            // TerminalStarter queues writes here; a FIFO barrier also proves that no extra NUL was sent.
            widget.getExecutorServiceManager().getSingleThreadScheduledExecutor()
                    .submit(() -> { }).get(5, TimeUnit.SECONDS);
            return connector.output();
        }

        String output() throws Exception {
            return new String(outputBytes(), StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws Exception {
            try {
                onEdt(() -> {
                    widget.close();
                    return null;
                });
            } finally {
                connector.close();
            }
        }
    }

    private static final class CapturingConnector implements TtyConnector {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CountDownLatch readerReady = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public int read(char[] buffer, int offset, int length) {
            readerReady.countDown();
            try {
                closed.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }

        @Override public synchronized void write(byte[] data) { bytes.writeBytes(data); }
        @Override public void write(String text) { write(text.getBytes(StandardCharsets.UTF_8)); }
        @Override public boolean isConnected() { return closed.getCount() != 0; }
        @Override public int waitFor() throws InterruptedException { closed.await(); return 0; }
        @Override public boolean ready() { return false; }
        @Override public String getName() { return "captured keyboard input"; }
        @Override public void close() { closed.countDown(); }
        synchronized byte[] output() { return bytes.toByteArray(); }
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return action.call();
        CompletableFuture<T> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(5, TimeUnit.SECONDS);
    }
}

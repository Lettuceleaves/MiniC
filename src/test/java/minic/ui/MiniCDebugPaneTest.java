package minic.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.Parent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCDebugPaneTest {
    private static boolean javafxStarted;

    private static void startJavafx() {
        if (javafxStarted) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        javafxStarted = true;
    }

    @Test
    void exposesSplitControlWithoutCreatingAnotherDebugSession() {
        startJavafx();
        MiniCWorkbenchViewModel viewModel = new MiniCWorkbenchViewModel();
        MiniCDebugPane pane = new MiniCDebugPane(viewModel);

        assertThat(button(pane, "拆分")).isNotNull();
        assertThat(button(pane, "设断点")).isNotNull();
        assertThat(button(pane, "清断点")).isNotNull();
        assertThat(button(pane, "快进")).isNotNull();
        assertThat(button(pane, "运行到断点")).isNotNull();
        assertThat(button(pane, "单步")).isNotNull();
        assertThat(button(pane, "步入")).isNotNull();
        assertThat(button(pane, "步返")).isNotNull();
        assertThat(button(pane, "暂停")).isNotNull();
        assertThat(button(pane, "重启")).isNotNull();
        assertThat(button(pane, "关闭")).isNotNull();
        assertThat(button(pane, "单退")).isNotNull();
        assertThat(button(pane, "步退")).isNotNull();
        assertThat(button(pane, "返回调用处")).isNotNull();
        assertThat(viewModel.debugStartedProperty().get()).isFalse();

        button(pane, "拆分").fire();

        assertThat(viewModel.debugStartedProperty().get()).isFalse();
    }

    private static Button button(javafx.scene.Node node, String text) {
        if (node instanceof Button button && button.getText().equals(text)) {
            return button;
        }
        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Button found = button(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

package minic.ui;

import minic.ui.control.MiniCCommandRegistry;
import minic.ui.control.MiniCControlCommand;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MiniCCommandRegistryTest {
    @Test
    void executesRegisteredEnabledCommand() {
        MiniCCommandRegistry registry = new MiniCCommandRegistry();
        AtomicInteger calls = new AtomicInteger();

        registry.register(new MiniCControlCommand(
                "debug.stepOver",
                "本层下一句",
                () -> true,
                calls::incrementAndGet));

        assertThat(registry.enabled("debug.stepOver")).isTrue();
        assertThat(registry.execute("debug.stepOver")).isTrue();
        assertThat(calls).hasValue(1);
    }

    @Test
    void missingCommandIsDisabledAndDoesNotExecute() {
        MiniCCommandRegistry registry = new MiniCCommandRegistry();

        assertThat(registry.command("missing")).isEmpty();
        assertThat(registry.enabled("missing")).isFalse();
        assertThat(registry.execute("missing")).isFalse();
    }

    @Test
    void disabledCommandDoesNotRun() {
        MiniCCommandRegistry registry = new MiniCCommandRegistry();
        AtomicInteger calls = new AtomicInteger();

        registry.register(new MiniCControlCommand(
                "compiler.next",
                "下一步",
                () -> false,
                calls::incrementAndGet));

        assertThat(registry.enabled("compiler.next")).isFalse();
        assertThat(registry.execute("compiler.next")).isFalse();
        assertThat(calls).hasValue(0);
    }

    @Test
    void commandLookupReturnsRegisteredMetadata() {
        MiniCCommandRegistry registry = new MiniCCommandRegistry();
        MiniCControlCommand command = new MiniCControlCommand(
                "settings.theme.set",
                "设置主题",
                () -> true,
                () -> {
                });

        registry.register(command);

        assertThat(registry.command("settings.theme.set")).containsSame(command);
        assertThat(registry.command("settings.theme.set").orElseThrow().label()).isEqualTo("设置主题");
    }
}

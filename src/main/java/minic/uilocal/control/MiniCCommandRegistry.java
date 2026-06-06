package minic.uilocal.control;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MiniCCommandRegistry {
    private final Map<String, MiniCControlCommand> commands = new LinkedHashMap<>();

    public void register(MiniCControlCommand command) {
        Objects.requireNonNull(command, "command");
        commands.put(command.id(), command);
    }

    public Optional<MiniCControlCommand> command(String id) {
        return Optional.ofNullable(commands.get(id));
    }

    public boolean enabled(String id) {
        return command(id)
                .map(MiniCControlCommand::enabled)
                .map(supplier -> supplier.getAsBoolean())
                .orElse(false);
    }

    public boolean execute(String id) {
        MiniCControlCommand command = commands.get(id);
        if (command == null || !command.enabled().getAsBoolean()) {
            return false;
        }
        command.action().run();
        return true;
    }
}

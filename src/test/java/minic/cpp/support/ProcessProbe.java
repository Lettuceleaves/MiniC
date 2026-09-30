package minic.cpp.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Isolated process fixture used to exercise pipe, timeout and exit handling. */
public final class ProcessProbe {
    public static List<String> command(String... arguments) {
        return javaCommand(ProcessProbe.class, arguments);
    }

    public static List<String> javaCommand(Class<?> entryPoint, String... arguments) {
        var result = new ArrayList<String>();
        result.add(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString());
        result.add("-Xmx256m");
        result.add("-Dfile.encoding=UTF-8");
        result.add("-cp");
        result.add(Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(path -> Path.of(path).toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator)));
        result.add(entryPoint.getName());
        result.addAll(List.of(arguments));
        return List.copyOf(result);
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "echo" -> {
                System.out.print(args[1] + ":" + new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
                System.err.print("separate stderr");
            }
            case "exit" -> System.exit(Integer.parseInt(args[1]));
            case "sleep" -> Thread.sleep(Long.parseLong(args[1]));
            case "flood" -> {
                int count = Integer.parseInt(args[1]);
                for (int i = 0; i < count; i++) { System.out.print('o'); System.err.print('e'); }
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}

package minic.compiler.nativebuild.machine;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 已完成指令选择、尚未编码为二进制的目标机器模块。
 *
 * @param entrySymbol 程序入口符号
 * @param sections 逻辑 sections
 * @param externalSymbols 尚待链接器解析的外部符号
 */
public record MachineModule(
        String entrySymbol,
        List<MachineSection> sections,
        List<String> externalSymbols
) {
    public MachineModule {
        Objects.requireNonNull(entrySymbol, "entrySymbol");
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(externalSymbols, "externalSymbols");
        if (entrySymbol.isBlank()) {
            throw new IllegalArgumentException("entrySymbol must not be blank");
        }
        sections = List.copyOf(sections);
        externalSymbols = List.copyOf(externalSymbols);
        HashSet<String> names = new HashSet<>();
        for (MachineSection section : sections) {
            if (!names.add(section.name())) {
                throw new IllegalArgumentException("duplicate section: " + section.name());
            }
        }
    }
}

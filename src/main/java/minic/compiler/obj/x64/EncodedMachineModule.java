package minic.compiler.obj.x64;

import minic.compiler.obj.machine.MachineModule;

import java.util.List;
import java.util.Objects;

/** 完成 x64 编码、尚未封装为 COFF 的机器模块。 */
public record EncodedMachineModule(
        MachineModule source,
        List<EncodedMachineSection> sections
) {
    public EncodedMachineModule {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sections, "sections");
        sections = List.copyOf(sections);
        if (source.sections().size() != sections.size()) {
            throw new IllegalArgumentException("encoded section count does not match machine module");
        }
        for (int index = 0; index < sections.size(); index++) {
            if (!source.sections().get(index).name().equals(sections.get(index).name())) {
                throw new IllegalArgumentException("encoded section order does not match machine module");
            }
        }
    }
}

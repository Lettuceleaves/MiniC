package craken.visualization.adapter.pipeline.projector;

import craken.compiler.*;
import craken.compiler.obj.*;
import craken.compiler.obj.coff.CoffObjectReader;
import craken.visualization.adapter.pipeline.*;
import java.util.*;

public final class ObjectProjector implements PipelineStageProjector<ObjResult> {
    @Override public Class<? extends Stage> stageType() { return ObjBuilder.class; }
    @Override public Class<ObjResult> contextType() { return ObjResult.class; }
    @Override public StageProjection project(PipelineStepObservation observation, ObjResult context, PipelineProjectionPlan input) {
        var rows = new ArrayList<PipelinePlans.Row>();
        context.assemblyPathOptional().ifPresent(path -> rows.add(row(path.toString(), Map.of("assemblyPath",path.toString()))));
        context.objectPathOptional().ifPresent(path -> rows.add(row(path.toString(), Map.of("objectPath",path.toString()))));
        if (context.objectFile() != null) {
            var parsed = new CoffObjectReader().read(context.objectFile());
            for (var section : parsed.sections()) {
                rows.add(row(section.name() + " · " + section.data().length + " bytes", Map.of("section",section.name(), "size",String.valueOf(section.data().length), "characteristics",Integer.toHexString(section.characteristics()))));
                addBytes(rows, section.name(), section.data(), "coffBytes");
                for (var relocation : section.relocations()) rows.add(row(section.name() + " · " + relocation, Map.of("relocation",relocation.toString(), "sectionName",section.name(), "symbolName",parsed.symbols().get(relocation.symbolIndex()).name())));
            }
            for (var symbol : parsed.symbols()) rows.add(row(symbol.name(), Map.of("symbol",symbol.name(), "value",String.valueOf(symbol.value()), "sectionNumber",String.valueOf(symbol.sectionNumber()), "storageClass",String.valueOf(symbol.storageClass()))));
        } else if (observation.encodedModule() != null) {
            for (var section : observation.encodedModule().sections()) {
                addBytes(rows, section.name(), section.bytes(), "encodedBytes");
                section.symbols().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> rows.add(row(entry.getKey(), Map.of("encodedSymbol",entry.getKey(), "offset",String.valueOf(entry.getValue())))));
                for (var relocation : section.relocations()) rows.add(row(relocation.toString(), Map.of("encodedRelocation",relocation.toString())));
            }
        } else if (observation.machineModule() != null) {
            for (var section : observation.machineModule().sections()) {
                rows.add(row(section.name(), Map.of("machineSection",section.name(), "kind",section.kind().name(), "alignment",String.valueOf(section.alignment()))));
                for (var item : section.items()) rows.add(row(item.toString(), Map.of("machineItem",item.toString())));
            }
        }
        return new StageProjection(input == null ? PipelinePlans.empty("汇编") : input, PipelinePlans.sequence("目标文件",rows));
    }
    private static PipelinePlans.Row row(String label, Map<String,String> fields) { return new PipelinePlans.Row(label,fields,null); }
    static void addBytes(List<PipelinePlans.Row> rows, String section, byte[] bytes, String field) {
        for (int offset=0; offset<bytes.length; offset+=16) {
            String hex=HexFormat.ofDelimiter(" ").formatHex(bytes,offset,Math.min(bytes.length,offset+16));
            rows.add(row(section + " " + String.format("%04x",offset) + ": " + hex,
                    Map.of(field,hex, "sectionName",section, "offset",String.valueOf(offset))));
        }
    }
}

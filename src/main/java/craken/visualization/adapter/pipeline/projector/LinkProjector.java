package craken.visualization.adapter.pipeline.projector;

import craken.compiler.*;
import craken.compiler.link.*;
import craken.visualization.adapter.pipeline.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LinkProjector implements PipelineStageProjector<LinkResult> {
    @Override public Class<? extends Stage> stageType() { return Linker.class; }
    @Override public Class<LinkResult> contextType() { return LinkResult.class; }
    @Override public StageProjection project(PipelineStepObservation observation, LinkResult context, PipelineProjectionPlan input) {
        var rows = new ArrayList<PipelinePlans.Row>();
        context.objectPathOptional().ifPresent(path -> rows.add(row(path.toString(), Map.of("object",path.toString()))));
        context.executableArtifactOptional().ifPresent(artifact -> rows.add(row(artifact.path().toString(), Map.of("executable",artifact.path().toString()))));
        byte[] image=observation.peImage();
        if (image != null) {
            if (image.length<64 || image[0]!='M' || image[1]!='Z') throw new IllegalArgumentException("Invalid captured DOS header");
            ByteBuffer buffer=ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
            int pe=buffer.getInt(0x3c);
            if (pe<0 || pe>image.length-24 || buffer.getInt(pe)!=0x00004550) throw new IllegalArgumentException("Invalid captured PE header");
            int sections=Short.toUnsignedInt(buffer.getShort(pe+6));
            int optional=Short.toUnsignedInt(buffer.getShort(pe+20));
            rows.add(row("PE · " + image.length + " bytes",Map.of("imageSize",String.valueOf(image.length), "machine",Integer.toHexString(Short.toUnsignedInt(buffer.getShort(pe+4))), "sections",String.valueOf(sections))));
            int table=pe+24+optional;
            if (table<0 || (long)table+40L*sections>image.length) throw new IllegalArgumentException("Invalid captured section table");
            for (int index=0; index<sections; index++) {
                int offset=table+40*index;
                int length=0; while (length<8 && image[offset+length]!=0) length++;
                String name=new String(image,offset,length,StandardCharsets.US_ASCII);
                rows.add(row(name,Map.of("peSection",name, "virtualSize",Integer.toUnsignedString(buffer.getInt(offset+8)), "virtualAddress",Integer.toUnsignedString(buffer.getInt(offset+12)), "rawSize",Integer.toUnsignedString(buffer.getInt(offset+16)))));
            }
            ObjectProjector.addBytes(rows,"PE",image,"peBytes");
        }
        return new StageProjection(input == null ? PipelinePlans.empty("目标文件") : input,PipelinePlans.sequence("链接",rows));
    }
    private static PipelinePlans.Row row(String label, Map<String,String> fields) { return new PipelinePlans.Row(label,fields,null); }
}

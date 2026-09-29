package minic.compiler.ir.manager;

import minic.compiler.ir.model.IrStringData;
import minic.compiler.ir.value.IrValue.IrStringLiteral;

import java.util.ArrayList;
import java.util.List;

public final class StringLiteralRegistry {
    private final ArrayList<IrStringData> stringData = new ArrayList<>();

    IrStringLiteral define(String value) {
        return define(value, minic.compiler.parser.node.Expression.LiteralEncoding.ORDINARY);
    }

    IrStringLiteral define(String value, minic.compiler.parser.node.Expression.LiteralEncoding encoding) {
        String label = "__minic$str$" + stringData.size();
        stringData.add(new IrStringData(label, value, encode(value, encoding)));
        return new IrStringLiteral(label);
    }

    private static byte[] encode(String value, minic.compiler.parser.node.Expression.LiteralEncoding encoding) {
        if (encoding == minic.compiler.parser.node.Expression.LiteralEncoding.ORDINARY
                || encoding == minic.compiler.parser.node.Expression.LiteralEncoding.UTF8) {
            byte[] content = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.Arrays.copyOf(content, content.length + 1);
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        if (encoding == minic.compiler.parser.node.Expression.LiteralEncoding.UTF16) {
            byte[] content = value.getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
            output.writeBytes(content); output.write(0); output.write(0);
        } else {
            value.codePoints().forEach(codePoint -> {
                output.write(codePoint); output.write(codePoint >>> 8);
                output.write(codePoint >>> 16); output.write(codePoint >>> 24);
            });
            output.write(0); output.write(0); output.write(0); output.write(0);
        }
        return output.toByteArray();
    }

    public List<IrStringData> stringData() {
        return stringData;
    }
}

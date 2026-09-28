package minic.compiler.ir.manager;

import minic.compiler.ir.model.IrStringData;
import minic.compiler.ir.value.IrValue.IrStringLiteral;

import java.util.ArrayList;
import java.util.List;

public final class StringLiteralRegistry {
    private final ArrayList<IrStringData> stringData = new ArrayList<>();

    IrStringLiteral define(String value) {
        String label = "__minic$str$" + stringData.size();
        stringData.add(new IrStringData(label, value));
        return new IrStringLiteral(label);
    }

    public List<IrStringData> stringData() {
        return stringData;
    }
}

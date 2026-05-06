package minic.runtime.debug.visual;

import java.util.Objects;

/**
 * 数组/表格形状。
 *
 * @param rows 行数
 * @param columns 列数
 * @param capacity 容量
 * @param logicalLength 逻辑长度
 */
public record ArrayShape(int rows, int columns, int capacity, int logicalLength) {
    public ArrayShape {
        if (rows < 1 || columns < 1) {
            throw new IllegalArgumentException("rows and columns must be positive");
        }
        if (capacity < 0 || logicalLength < 0) {
            throw new IllegalArgumentException("capacity and logicalLength must not be negative");
        }
    }

    /**
     * 一维形状。
     *
     * @param length 长度
     * @return 形状
     */
    public static ArrayShape oneDimensional(int length) {
        return new ArrayShape(1, length, length, length);
    }

    /**
     * 二维形状。
     *
     * @param rows 行数
     * @param columns 列数
     * @return 形状
     */
    public static ArrayShape twoDimensional(int rows, int columns) {
        return new ArrayShape(rows, columns, rows * columns, rows * columns);
    }
}

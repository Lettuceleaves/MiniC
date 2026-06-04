package minic.runtime.debug.dataflow;

/**
 * 数据流事件类型。
 */
public enum DataFlowEventType {
    WRITE_LOCAL,
    POINTER_RETARGET,
    FIELD_WRITE,
    ARRAY_ELEMENT_WRITE,
    LOAD_POINTER,
    STORE_POINTER,
    DECLARE_LOCAL,
    ADDRESS_OF_LOCAL,
    ELEMENT_ADDRESS,
    FIELD_ADDRESS
}

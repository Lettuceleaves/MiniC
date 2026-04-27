package minic;

/**
 * MiniC 命令行入口占位类型。
 */
public final class Main {
    private Main() {
    }

    /**
     * 返回项目名称。
     *
     * @return 项目名称
     */
    public static String name() {
        return "MiniC";
    }

    /**
     * 打印当前项目名称。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        System.out.println(name());
    }
}

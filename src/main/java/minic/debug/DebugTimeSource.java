package minic.debug;

import java.time.ZoneId;

/** Debug runtime 使用的可替换时间源；测试可提供完全确定的时钟和时区。 */
interface DebugTimeSource {
    long clockTicks();

    long epochSeconds();

    ZoneId localZone();

    static DebugTimeSource system() {
        long startedNanos = System.nanoTime();
        ZoneId zone = ZoneId.systemDefault();
        return new DebugTimeSource() {
            @Override
            public long clockTicks() {
                return (System.nanoTime() - startedNanos) / 1_000_000L;
            }

            @Override
            public long epochSeconds() {
                return System.currentTimeMillis() / 1_000L;
            }

            @Override
            public ZoneId localZone() {
                return zone;
            }
        };
    }
}

package minic.debug;

import minic.compiler.ir.model.IrType;
import minic.debug.DebugLibraryCallResult.Returned;
import minic.debug.DebugRuntime.Value;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Windows x64 time.h 中可由 Debug runtime 确定解释的函数。 */
final class DebugTimeLibraryProvider implements DebugLibraryProvider {
    private static final int TM_FIELD_COUNT = 9;
    private static final long MAX_TIME = 32_534_351_999L; // 3000-12-31 23:59:59 UTC

    private final Map<String, DebugLibraryFunction> functions = Map.of(
            "clock", this::clock,
            "difftime", this::difftime,
            "time", this::time,
            "mktime", this::mktime,
            "gmtime", this::gmtime,
            "localtime", this::localtime,
            "strftime", this::strftime
    );

    @Override
    public String name() {
        return "time";
    }

    @Override
    public Map<String, DebugLibraryFunction> functions() {
        return functions;
    }

    private DebugLibraryCallResult clock(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("clock", arguments, 0);
        return returned(IrType.LONG, runtime.readClockTicks());
    }

    private DebugLibraryCallResult difftime(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("difftime", arguments, 2);
        double later = arguments.get(0).integer();
        double earlier = arguments.get(1).integer();
        return returned(IrType.DOUBLE, later - earlier);
    }

    private DebugLibraryCallResult time(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("time", arguments, 1);
        long value = runtime.readEpochSeconds();
        long destination = arguments.getFirst().integer();
        if (destination != 0) {
            runtime.write(destination, Value.of(IrType.LONG_LONG, value));
        }
        return returned(IrType.LONG_LONG, value);
    }

    private DebugLibraryCallResult gmtime(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("gmtime", arguments, 1);
        return calendarTime(runtime, arguments.getFirst().integer(), ZoneOffset.UTC);
    }

    private DebugLibraryCallResult localtime(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("localtime", arguments, 1);
        return calendarTime(runtime, arguments.getFirst().integer(), runtime.localTimeZone());
    }

    private DebugLibraryCallResult calendarTime(
            DebugRuntime runtime,
            long timePointer,
            ZoneId zone
    ) {
        if (timePointer == 0) {
            runtime.setErrno(DebugLibrarySupport.EINVAL);
            return returned(IrType.POINTER, 0);
        }
        long epoch = runtime.read(timePointer, IrType.LONG_LONG).integer();
        if (!validTime(epoch)) {
            runtime.setErrno(DebugLibrarySupport.EINVAL);
            return returned(IrType.POINTER, 0);
        }
        try {
            ZonedDateTime value = Instant.ofEpochSecond(epoch).atZone(zone);
            long address = runtime.timeStructAddress();
            writeTm(runtime, address, fields(value));
            return returned(IrType.POINTER, address);
        } catch (DateTimeException exception) {
            runtime.setErrno(DebugLibrarySupport.EINVAL);
            return returned(IrType.POINTER, 0);
        }
    }

    private DebugLibraryCallResult mktime(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("mktime", arguments, 1);
        long address = arguments.getFirst().integer();
        if (address == 0) {
            runtime.setErrno(DebugLibrarySupport.EINVAL);
            return returned(IrType.LONG_LONG, -1);
        }
        try {
            TmFields source = readTm(runtime, address);
            LocalDateTime local = LocalDateTime.of(
                            Math.addExact(source.year(), 1900),
                            1,
                            1,
                            0,
                            0
                    )
                    .plusMonths(source.month())
                    .plusDays((long) source.monthDay() - 1)
                    .plusHours(source.hour())
                    .plusMinutes(source.minute())
                    .plusSeconds(source.second());
            ZonedDateTime normalized = resolveLocal(local, runtime.localTimeZone(), source.isDst());
            long epoch = normalized.toEpochSecond();
            if (!validTime(epoch)) {
                runtime.setErrno(DebugLibrarySupport.EINVAL);
                return returned(IrType.LONG_LONG, -1);
            }
            writeTm(runtime, address, fields(normalized));
            return returned(IrType.LONG_LONG, epoch);
        } catch (ArithmeticException | DateTimeException exception) {
            runtime.setErrno(DebugLibrarySupport.EINVAL);
            return returned(IrType.LONG_LONG, -1);
        }
    }

    private DebugLibraryCallResult strftime(DebugRuntime runtime, List<Value> arguments) {
        DebugLibrarySupport.requireCount("strftime", arguments, 4);
        long destination = arguments.get(0).integer();
        long maximumSize = arguments.get(1).integer();
        String format = runtime.readCString(arguments.get(2).integer());
        TmFields value = readTm(runtime, arguments.get(3).integer());
        String rendered = formatTime(format, value);
        long required = rendered.length() + 1L;
        if (maximumSize == 0 || Long.compareUnsigned(maximumSize, required) < 0) {
            return returned(IrType.UNSIGNED_LONG_LONG, 0);
        }
        runtime.writeCString(destination, rendered, maximumSize);
        return returned(IrType.UNSIGNED_LONG_LONG, rendered.length());
    }

    private String formatTime(String format, TmFields value) {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < format.length(); index++) {
            char character = format.charAt(index);
            if (character != '%') {
                output.append(character);
                continue;
            }
            if (++index >= format.length()) {
                throw new IllegalStateException("Incomplete strftime conversion");
            }
            char conversion = format.charAt(index);
            output.append(switch (conversion) {
                case '%' -> "%";
                case 'Y' -> decimal(value.year() + 1900, 4);
                case 'y' -> decimal(Math.floorMod(value.year(), 100), 2);
                case 'm' -> decimal(value.month() + 1, 2);
                case 'd' -> decimal(value.monthDay(), 2);
                case 'H' -> decimal(value.hour(), 2);
                case 'M' -> decimal(value.minute(), 2);
                case 'S' -> decimal(value.second(), 2);
                case 'j' -> decimal(value.yearDay() + 1, 3);
                case 'w' -> Integer.toString(value.weekDay());
                default -> throw new IllegalStateException(
                        "Unsupported strftime conversion: %" + conversion
                );
            });
        }
        return output.toString();
    }

    private String decimal(int value, int width) {
        return String.format(Locale.ROOT, "%0" + width + "d", value);
    }

    private ZonedDateTime resolveLocal(LocalDateTime local, ZoneId zone, int isDst) {
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(local);
        if (offsets.size() == 1) {
            return ZonedDateTime.ofLocal(local, zone, offsets.getFirst());
        }
        if (offsets.size() == 2) {
            ZoneOffset selected = offsets.getFirst();
            if (isDst >= 0) {
                boolean requestedDst = isDst > 0;
                for (ZoneOffset offset : offsets) {
                    boolean candidateDst = rules.isDaylightSavings(local.toInstant(offset));
                    if (candidateDst == requestedDst) {
                        selected = offset;
                        break;
                    }
                }
            }
            return ZonedDateTime.ofLocal(local, zone, selected);
        }
        ZoneOffsetTransition transition = rules.getTransition(local);
        if (transition == null) {
            throw new DateTimeException("local time has no zone transition");
        }
        LocalDateTime shifted = local.plusSeconds(transition.getDuration().getSeconds());
        return ZonedDateTime.ofLocal(shifted, zone, transition.getOffsetAfter());
    }

    private TmFields fields(ZonedDateTime value) {
        boolean daylight = value.getZone().getRules().isDaylightSavings(value.toInstant());
        return new TmFields(
                value.getSecond(),
                value.getMinute(),
                value.getHour(),
                value.getDayOfMonth(),
                value.getMonthValue() - 1,
                value.getYear() - 1900,
                value.getDayOfWeek().getValue() % 7,
                value.getDayOfYear() - 1,
                daylight ? 1 : 0
        );
    }

    private TmFields readTm(DebugRuntime runtime, long address) {
        if (address == 0) {
            throw new IllegalStateException("struct tm pointer is null");
        }
        int[] fields = new int[TM_FIELD_COUNT];
        for (int index = 0; index < fields.length; index++) {
            fields[index] = (int) runtime.read(address + index * 4L, IrType.INT).integer();
        }
        return new TmFields(
                fields[0], fields[1], fields[2], fields[3], fields[4],
                fields[5], fields[6], fields[7], fields[8]
        );
    }

    private void writeTm(DebugRuntime runtime, long address, TmFields fields) {
        int[] values = {
                fields.second(), fields.minute(), fields.hour(), fields.monthDay(), fields.month(),
                fields.year(), fields.weekDay(), fields.yearDay(), fields.isDst()
        };
        for (int index = 0; index < values.length; index++) {
            runtime.write(address + index * 4L, Value.of(IrType.INT, values[index]));
        }
    }

    private boolean validTime(long epoch) {
        return epoch >= 0 && epoch <= MAX_TIME;
    }

    private Returned returned(IrType type, Number value) {
        return new Returned(Value.of(type, value));
    }

    private record TmFields(
            int second,
            int minute,
            int hour,
            int monthDay,
            int month,
            int year,
            int weekDay,
            int yearDay,
            int isDst
    ) {
    }
}

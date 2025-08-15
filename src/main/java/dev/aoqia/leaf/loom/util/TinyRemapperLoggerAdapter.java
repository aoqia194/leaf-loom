package dev.aoqia.leaf.loom.util;

import net.fabricmc.tinyremapper.api.TrLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TinyRemapperLoggerAdapter implements TrLogger {
    public static final TinyRemapperLoggerAdapter INSTANCE = new TinyRemapperLoggerAdapter();

    private static final Logger LOGGER = LoggerFactory.getLogger("TinyRemapper");

    private TinyRemapperLoggerAdapter() {}

    @Override
    public void log(Level level, String message) {
        switch (level) {
            case ERROR:
                LOGGER.error(message);
                break;
            case WARN:
                LOGGER.warn(message);
                break;
            case INFO:
                LOGGER.info(message);
                break;
            case DEBUG:
                LOGGER.debug(message);
                break;
        }
    }
}

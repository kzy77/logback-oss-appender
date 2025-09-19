package io.github.osslogback.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON Lines 编码器：将 ILoggingEvent 编码为一行 JSON。
 */
public final class JsonLinesLayout extends LayoutBase<ILoggingEvent> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private boolean includeLoggerName = true;
    private boolean includeThreadName = true;
    private boolean includeMdc = true;
    private boolean includeException = true;

    /**
     * 将事件编码为一行 JSON 字符串。
     */
    @Override
    public String doLayout(ILoggingEvent event) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("ts", event.getTimeStamp());
            m.put("level", String.valueOf(event.getLevel()));
            m.put("message", event.getFormattedMessage());
            if (includeLoggerName) {
                m.put("logger", event.getLoggerName());
            }
            if (includeThreadName) {
                m.put("thread", event.getThreadName());
            }
            if (includeMdc && event.getMDCPropertyMap() != null && !event.getMDCPropertyMap().isEmpty()) {
                m.put("mdc", event.getMDCPropertyMap());
            }
            if (includeException && event.getThrowableProxy() != null) {
                m.put("exception", event.getThrowableProxy().getClassName() + ": " + event.getThrowableProxy().getMessage());
            }
            return MAPPER.writeValueAsString(m) + "\n";
        } catch (Exception e) {
            return "{\"malformed\":true,\"error\":\"" + e.getMessage() + "\"}\n";
        }
    }

    // region setters for logback config
    public void setIncludeLoggerName(boolean includeLoggerName) {
        this.includeLoggerName = includeLoggerName;
    }
    public void setIncludeThreadName(boolean includeThreadName) {
        this.includeThreadName = includeThreadName;
    }
    public void setIncludeMdc(boolean includeMdc) {
        this.includeMdc = includeMdc;
    }
    public void setIncludeException(boolean includeException) {
        this.includeException = includeException;
    }
    // endregion
}

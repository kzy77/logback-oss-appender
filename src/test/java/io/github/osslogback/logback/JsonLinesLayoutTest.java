package io.github.osslogback.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonLinesLayout 编码测试：验证关键字段存在与换行结尾。
 */
public class JsonLinesLayoutTest {
    @Test
    public void testLayoutEncodesEvent() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("test.logger");
        logger.setLevel(Level.INFO);

        JsonLinesLayout layout = new JsonLinesLayout();
        layout.setContext(context);
        layout.start();

        // 使用 ListAppender 获取 ILoggingEvent
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.setContext(context);
        listAppender.start();
        logger.addAppender(listAppender);

        logger.info("hello {}", "world");
        assertFalse(listAppender.list.isEmpty());
        ILoggingEvent evt = listAppender.list.get(0);

        String line = layout.doLayout(evt);
        assertTrue(line.endsWith("\n"));
        assertTrue(line.contains("\"message\":\"hello world\""));
        assertTrue(line.contains("\"level\":\"INFO\""));
        assertTrue(line.contains("\"logger\":\"test.logger\""));
    }
}



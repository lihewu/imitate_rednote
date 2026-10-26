package com.rednote.framework.biz.operationlog.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rednote.framework.biz.operationlog.config.ApiOperationLogAutoConfiguration;
import com.rednote.framework.common.util.JsonUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.Assert.*;

public class ApiOperationLogAspectTest {

    private AnnotationConfigApplicationContext context;
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;
    private TestService service;

    @Before
    public void setUp() {
        JsonUtils.init(new ObjectMapper());
        logger = (Logger) LoggerFactory.getLogger(ApiOperationLogAspect.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        context = new AnnotationConfigApplicationContext(
                AopAutoConfiguration.class, ApiOperationLogAutoConfiguration.class, TestConfig.class);
        service = context.getBean(TestService.class);
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.close();
        }
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    public void logsArgumentsResultAndDurationAndExecutesOnce() {
        assertEquals("hello rednote", service.greet("rednote"));
        assertEquals(1, service.getCalls());
        assertEquals(2, appender.list.size());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("description=测试问候"));
        assertTrue(appender.list.get(0).getFormattedMessage().contains("args=[\"rednote\"]"));
        assertTrue(appender.list.get(1).getFormattedMessage().contains("result=\"hello rednote\""));
        assertTrue(appender.list.get(1).getFormattedMessage().contains("elapsedMs="));
    }

    @Test
    public void logsFailureAndRethrowsOriginalException() {
        IllegalStateException expected = new IllegalStateException("业务失败");
        assertSame(expected, assertThrows(IllegalStateException.class, () -> service.fail(expected)));
        assertEquals(1, service.getCalls());
        assertEquals(2, appender.list.size());
        ILoggingEvent failure = appender.list.get(1);
        assertEquals(Level.ERROR, failure.getLevel());
        assertNotNull(failure.getThrowableProxy());
        assertEquals("业务失败", failure.getThrowableProxy().getMessage());
    }

    @Test
    public void serializationFailureDoesNotChangeBusinessResult() {
        BrokenPayload payload = new BrokenPayload();
        assertSame(payload, service.echo(payload));
        assertEquals(1, service.getCalls());
        assertEquals(2, appender.list.size());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("<JSON序列化失败>"));
        assertTrue(appender.list.get(1).getFormattedMessage().contains("<JSON序列化失败>"));
    }

    @Test
    public void unannotatedMethodsAreNotLogged() {
        assertEquals("plain", service.plain());
        assertTrue(appender.list.isEmpty());
    }

    @Test
    public void starterDeclaresAutoConfigurationForDiscovery() {
        assertTrue(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates().contains(ApiOperationLogAutoConfiguration.class.getName()));
        assertEquals(1, context.getBeansOfType(ApiOperationLogAspect.class).size());
    }

    @Configuration(proxyBeanMethods = false)
    public static class TestConfig {
        @Bean
        public TestService testService() {
            return new TestService();
        }
    }

    public static class TestService {
        private int calls;

        public int getCalls() {
            return calls;
        }

        @ApiOperationLog(description = "测试问候")
        public String greet(String name) {
            calls++;
            return "hello " + name;
        }

        @ApiOperationLog(description = "测试异常")
        public void fail(IllegalStateException exception) {
            calls++;
            throw exception;
        }

        @ApiOperationLog(description = "测试序列化失败")
        public BrokenPayload echo(BrokenPayload payload) {
            calls++;
            return payload;
        }

        public String plain() {
            return "plain";
        }
    }

    public static class BrokenPayload {
        public String getValue() {
            throw new IllegalStateException("不能序列化");
        }
    }
}

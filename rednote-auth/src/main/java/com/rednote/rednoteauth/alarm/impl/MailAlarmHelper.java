package com.rednote.rednoteauth.alarm.impl;

import com.rednote.rednoteauth.alarm.AlarmInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 邮件告警测试实现，不实际发送邮件。 */
public class MailAlarmHelper implements AlarmInterface {
    private static final Logger log = LoggerFactory.getLogger(MailAlarmHelper.class);

    @Override
    public void send(String message) {
        log.warn("[测试邮件告警] {}", message);
    }
}

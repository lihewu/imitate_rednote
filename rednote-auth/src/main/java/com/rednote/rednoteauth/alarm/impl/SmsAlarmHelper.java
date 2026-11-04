package com.rednote.rednoteauth.alarm.impl;

import com.rednote.rednoteauth.alarm.AlarmInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 短信告警测试实现，不实际发送短信。 */
public class SmsAlarmHelper implements AlarmInterface {
    private static final Logger log = LoggerFactory.getLogger(SmsAlarmHelper.class);

    @Override
    public void send(String message) {
        log.warn("[测试短信告警] {}", message);
    }
}

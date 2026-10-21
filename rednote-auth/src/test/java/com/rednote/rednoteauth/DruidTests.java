package com.rednote.rednoteauth;

import com.alibaba.druid.filter.config.ConfigTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 使用 Druid ConfigTools 生成数据库密码密文，无需启动 Spring 或连接数据库。
 */
class DruidTests {

    @Test
    void encryptPassword() throws Exception {
        String plainPassword = "123456";

        // Druid 使用私钥加密，数据源配置公钥解密；每次运行生成一组新密钥。
        String[] keyPair = ConfigTools.genKeyPair(2048);
        String privateKey = keyPair[0];
        String publicKey = keyPair[1];
        String encryptedPassword = ConfigTools.encrypt(privateKey, plainPassword);

        assertNotEquals(plainPassword, encryptedPassword);
        assertEquals(plainPassword, ConfigTools.decrypt(publicKey, encryptedPassword));

        // 公钥与密文必须配套使用，分别用于 config.decrypt.key 和数据源 password。
        System.out.println("publicKey: " + publicKey);
        System.out.println("password: " + encryptedPassword);
    }
}

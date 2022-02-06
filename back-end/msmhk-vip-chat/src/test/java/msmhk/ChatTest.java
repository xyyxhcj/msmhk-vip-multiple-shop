package msmhk;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import vip.msmhk.ChatApplication;
import vip.msmhk.config.ChatProperties;
import vip.msmhk.util.AesUtils;
import vip.msmhk.util.RSAUtils;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.security.KeyPair;

/**
 * @author xyyxhcj@qq.com
 * @since 2020/11/25
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ChatApplication.class)
@TestPropertySource(value = {"classpath:application.yml"})
//@ActiveProfiles("cjTest")
@Slf4j
public class ChatTest {
    @Resource
    private ChatProperties chatProperties;

    @Test
    public void testRsa01() throws Exception {
        KeyPair keyPair = RSAUtils.genKeyPair();
        log.info("公钥：{}", RSAUtils.getPublicKey(keyPair));
        log.info("私钥：{}", RSAUtils.getPrivateKey(keyPair));
        String encrypt = RSAUtils.encryptByPublicKey("{\"loginName\":\"admin\",\"password\":\"123456\"}", RSAUtils.getPublicKey(keyPair));
        log.info("公钥加密：{}", encrypt);
        String decrypt = RSAUtils.decryptByPrivateKey(encrypt, RSAUtils.getPrivateKey(keyPair));
        log.info("私钥解密：{}", decrypt);
    }

    @Test
    public void testRsa02() throws Exception {
        KeyPair keyPair = RSAUtils.genKeyPair();
        log.info("私钥：{}", RSAUtils.getPrivateKey(keyPair));
        log.info("公钥：{}", RSAUtils.getPublicKey(keyPair));
        String encrypt = RSAUtils.encryptByPrivateKey("{\"loginName\":\"admin\",\"password\":\"123456\"}", RSAUtils.getPrivateKey(keyPair));
        log.info("私钥加密：{}", encrypt);
        String decrypt = RSAUtils.decryptByPublicKey(encrypt, RSAUtils.getPublicKey(keyPair));
        log.info("公钥解密：{}", decrypt);
    }

    @Test
    public void testAes01() throws Exception {
        String key = "1234567890123456";
        String input = "{\"loginName\":\"admin\",\"password\":\"123456\"}";
        String encrypt = AesUtils.encrypt(input, key);
        log.info("加密：{}", encrypt);
        log.info("解密：{}", AesUtils.decrypt(encrypt, key));
    }

    @Test
    public void testDecryptKey() throws Exception {
        String firstKey = "cSXi2aCGcIBoEZ6rDa49Cp8FiXzfApLQxG9LHuA0p5SCQuI5pXD456WWmxIFviAQYlL50I/NgPDqs87YNsjGP+W0QGcUy5byOfz0ek6pgRMUpif8OMHO8j9sVBtvz5STZpaULYTocNSh+600biF/ub/MtqPqV7eyYlSOKZugLhs=";

        String input = "9Rj9+fbZ1nVxK6e5BFZ0N1d8Tad1zZSFa2cZgbioxRZ1GOMVzhweFSxXdWgU4tbrtgHT1v45v7iOo4pa213K28XjsXW/CEmFKkWY2bBaHFEJMr4nESuea6N7zNXpoiIr/rewxpbX61n24ZXIyWnSYDxn/hTpbaov3WCFsrKiWsgdZQDC+m6MEaKrYQ/rEeRyhqg1q+lzcuikKDEKGz9hPtaBYW6yS4jOTAWz4Dtt4hR/zIAodCR6X0udwUbG3KNyHfV8uJh7SDrXh4Mb+8lRbj14jnRzvXUJ/R+EeaoEqVTOLqGhLS3Vvl/XOEu1Fgau2AnDT2ivAaEoXZwrTsI5SBWn0cHSKHuNiOw5RclfJiJAiJuSSQxHO+MmspopnIHjjFJ0EHs5Uic+aLgRbn5YtdK394Siq/dP28OxLIEfWvAgCY6Kofb8645nC/4vXQY0eQKOArK9yrRDPWxjfwUyPhT5WzZosT8o2tr7lSpFu8e6iD0OYf8zV6Y1R6udDe6XeQgcJqkTT5OvyXl3m/CIbFzZBdxBWoFWW3fzu02F9rcw9vAyBAZBC325L6ilb1a3";
        String aesKey = RSAUtils.decryptByPrivateKey(firstKey, chatProperties.getRsaPrivateKey());
        System.out.println(AesUtils.decrypt(input, aesKey));
    }
}

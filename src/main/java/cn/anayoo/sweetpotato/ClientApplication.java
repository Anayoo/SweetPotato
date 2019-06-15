package cn.anayoo.sweetpotato;

import cn.anayoo.sweetpotato.db.DatabasePool;
import org.dom4j.DocumentException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

import java.util.logging.Level;
import java.util.logging.Logger;

@EnableEurekaClient
@SpringBootApplication
@EnableDiscoveryClient
public class ClientApplication {
    private static final Logger logger = Logger.getLogger(ClientApplication.class.getName());

    public static void main(String[] args) {
        logger.log(Level.INFO, "服务启动，初始化服务对象...");
        try {
            var xmlLoader = new XmlLoader().read("potatoes.xml");
            new RestCreater(xmlLoader).createModel().createGetter().createPoster().createPutter().createDeleter();
            DatabasePool.getInstance(xmlLoader);
            logger.log(Level.INFO, "初始化服务对象: ok");
        } catch (DocumentException e) {
            // 当断不断反受其乱，这个错都能报说明服务启动也没用了
            e.printStackTrace();
        }
        SpringApplication.run(ClientApplication.class);
    }
}

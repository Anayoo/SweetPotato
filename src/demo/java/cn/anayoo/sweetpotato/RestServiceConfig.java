package cn.anayoo.sweetpotato;

import cn.anayoo.sweetpotato.db.DatabasePool;
import org.dom4j.DocumentException;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RestServiceConfig extends ResourceConfig {
    private static final Logger logger = Logger.getLogger(RestServiceConfig.class.getName());
    private static boolean firstBoot = false;
    private XmlLoader xmlLoader;

    public RestServiceConfig() {
        if (!firstBoot) {
            firstBoot = true;
            logger.log(Level.INFO, "服务启动，初始化服务对象...");
            try {
                xmlLoader = new XmlLoader().read("potatoes.xml");
                new RestServiceCreater(xmlLoader).createRestfulApi();
                DatabasePool.getInstance(xmlLoader);
                logger.log(Level.INFO, "初始化服务对象: ok");
            } catch (DocumentException e) {
                // 当断不断反受其乱，这个错都能报说明服务启动也没用了
                e.printStackTrace();
            }
        }
        packages(xmlLoader.getServicePackage());
        register(MultiPartFeature.class);
        register(JacksonFeature.class);
    }
}

package cn.anayoo.sweetpotato;

import org.dom4j.DocumentException;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class RestServiceConfig extends ResourceConfig {
    private static final Logger logger = Logger.getLogger(RestServiceConfig.class.getName());

    public RestServiceConfig() throws DocumentException {
        var xmlLoader = new XmlLoader().read("potatoes.xml");
        packages("cn.anayoo.sweetpotato.demo.service");
        register(MultiPartFeature.class);
        register(JacksonFeature.class);
    }
}

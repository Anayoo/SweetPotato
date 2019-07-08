package cn.anayoo.sweetpotato;

import cn.anayoo.sweetpotato.db.DatabasePool;
import org.dom4j.DocumentException;
import org.junit.Test;

import javax.ws.rs.*;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MainTest {

    @Test
    public void MainTest() throws DocumentException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        var xmlLoader = new XmlLoader().read("potatoes.xml");
        new RestCreater(xmlLoader).createModel().createGetter().createPoster().createPutter().createDeleter();
        DatabasePool.getInstance(xmlLoader);


    }
}

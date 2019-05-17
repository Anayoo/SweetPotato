package cn.anayoo.sweetpotato;

import cn.anayoo.sweetpotato.db.DatabasePool;
import org.dom4j.DocumentException;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

public class MainTest {

    @Test
    public void MainTest() throws DocumentException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        var xmlLoader = new XmlLoader().read("potatoes.xml");
        new RestServiceCreater(xmlLoader).createRestfulApi();
        DatabasePool.getInstance(xmlLoader);

        var clazz = this.getClass().getClassLoader().loadClass(xmlLoader.getServicePackage() + ".GetterService");
        var obj = clazz.getDeclaredConstructor().newInstance();
        var m = clazz.getMethod("getUser", Integer.class, String.class, String.class);
        var res = m.invoke(obj, 1, "", "");
        var m1 = res.getClass().getMethod("getUsername");
        System.out.println(m1.invoke(res));
    }
}

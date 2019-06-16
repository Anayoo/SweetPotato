package cn.anayoo.sweetpotato;

import cn.anayoo.sweetpotato.db.DatabasePool;
import org.dom4j.DocumentException;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

public class MainTest {

    @Test
    public void MainTest() throws DocumentException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        var xmlLoader = new XmlLoader().read("potatoes.xml");
        new RestCreater(xmlLoader).createModel().createGetter().createPoster().createPutter().createDeleter();
        DatabasePool.getInstance(xmlLoader);

        var clazz = this.getClass().getClassLoader().loadClass(xmlLoader.getServicePackage() + ".GetterService");
        var obj = clazz.getDeclaredConstructor().newInstance();
        var m = clazz.getMethod("getUsers", Long.class, String.class, String.class, int.class, int.class, String.class, String.class, boolean.class);
        var res = m.invoke(obj, null, "", "", 16, 1, "id", "asc", true);
        var m1 = res.getClass().getMethod("getSetting");
        var res1 = m1.invoke(res);
        var m2 = res1.getClass().getMethod("getCount");
        System.out.println(m2.invoke(res1));
    }
}

package cn.anayoo.sweetpotato.util;

import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.MemberValue;

/**
 * 稍微封装一下javassist，不然RestServiceCreater类方法代码行数太多了。。读起来费劲
 * Created by anayoo on 19-05-15
 * @author anayoo
 */
public class JavassistUtil {

    /**
     * 创建类的构造方法
     * @param clazz         目标类
     * @param args          构造方法入参
     * @param modifiers     构造方法修饰
     * @param body          构造方法体
     * @return              构造方法
     */
    public static CtConstructor createConstructor(CtClass clazz, CtClass[] args, int modifiers, String body) throws CannotCompileException {
        var ctConstructor = new CtConstructor(args, clazz);
        ctConstructor.setModifiers(modifiers);
        ctConstructor.setBody(body);
        return ctConstructor;
    }

    /**
     * 为目标类添加类注解
     * @param classFile             目标类
     * @param annotationClasses     注解类名
     * @param memberNames           注解类属性的名称
     * @param memberValues          注解类属性的值
     */
    public static void addAnnotation(ClassFile classFile, String[] annotationClasses, String[][] memberNames, MemberValue[][] memberValues) {
        classFile.addAttribute(getAnnotation(classFile.getConstPool(), annotationClasses, memberNames, memberValues));
    }

    /**
     * 为目标类的目标方法添加方法注解
     * @param constPool             目标类的常量池
     * @param method                目标方法
     * @param annotationClasses     注解类名
     * @param memberNames           注解类属性的名称
     * @param memberValues          注解类属性的值
     */
    public static void addAnnotation(ConstPool constPool, CtMethod method, String[] annotationClasses, String[][] memberNames, MemberValue[][] memberValues) {
        method.getMethodInfo().addAttribute(getAnnotation(constPool, annotationClasses, memberNames, memberValues));
    }

    /**
     * 为目标类的目标参数添加参数注解
     * @param constPool             目标类的常量池
     * @param field                 目标参数
     * @param annotationClasses     注解类名
     * @param memberNames           注解类属性的名称
     * @param memberValues          注解类属性的值
     */
    public static void addAnnotation(ConstPool constPool, CtField field, String[] annotationClasses, String[][] memberNames, MemberValue[][] memberValues) {
        field.getFieldInfo().addAttribute(getAnnotation(constPool, annotationClasses, memberNames, memberValues));
    }

    /**
     * 获取注解
     * @param constPool             目标类的常量池
     * @param annotationClasses     注解类名
     * @param memberNames           注解类属性的名称
     * @param memberValues          注解类属性的值
     */
    private static AnnotationsAttribute getAnnotation(ConstPool constPool, String[] annotationClasses, String[][] memberNames, MemberValue[][] memberValues) {
        var attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        for (int i = 0; i < annotationClasses.length; i ++) {
            String annotationClass = annotationClasses[i];
            var annot = new Annotation(annotationClass, constPool);
            String[] memberName = memberNames[i];
            MemberValue[] memberValue = memberValues[i];
            for (int j = 0; j < memberName.length; j ++) {
                annot.addMemberValue(memberName[j], memberValue[j]);
            }
            attr.addAnnotation(annot);
        }
        return attr;
    }

    public static CtClass getCtClass(ClassPool classPool, String type) throws NotFoundException {
        var ctClass = (CtClass) null;
        switch (type) {
            case "double" : ctClass = classPool.get("java.lang.Double"); break;
            case "number" : ctClass = classPool.get("java.math.BigDecimal"); break;
            case "string" : ctClass = classPool.get("java.lang.String"); break;
            case "binary" : ctClass = classPool.get("java.lang.String"); break;
            default : ctClass = classPool.get(type); break;
        }
        return ctClass;
    }

}

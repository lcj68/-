package com.lagou.edu.annotation;

import com.alibaba.druid.util.StringUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手写springIOC----注解版本
 *
 */
public class MyAnnotationConfigApplicationContext {
    public String setPackageName(String packageName) {
        this.packageName = packageName;
        return packageName;
    }

    private String packageName = "com.lagou.edu.service.impl";
    private ConcurrentHashMap<String,Class<?>> beans = null;

    public MyAnnotationConfigApplicationContext() throws Exception{
        beans = new ConcurrentHashMap<>();
        initBeans();
    }

    //初始化对象
    public void initBeans () throws Exception {
        //1.使用java反射机制扫包，获取当前包下所有类
        List<Class<?>> classes = ClassUtil.getClasses(packageName);
        System.out.println("初始化");
        //2.判断类上面是否有注解,返回一个map集合，里面包含了，所有带ExtService注解的类的信息
        ConcurrentHashMap<String,Class<?>>  classHasExtServiceAnnotation = findClassIsHasAnnotation(classes);
        if (classHasExtServiceAnnotation == null || classHasExtServiceAnnotation.isEmpty()){
            System.out.println("该包下所有类都没有ExtServiceAnnotation");
            throw new Exception("该包下所有类都没有ExtServiceAnnotation");
        }
    }
    public Object getBean (String beanId) throws Exception {
        if (StringUtils.isEmpty(beanId)){
            throw new Exception("bean Id 不能为空");
        }
        //从spring 容器初始化对像
        Class<?> classInfo = beans.get(beanId);
        if (classInfo == null) {
            throw  new Exception("Class not found");
        }
        //2.使用反射机制初始化对像
        return newInstance(classInfo);
    }

    //通过反射解析对象
    private Object newInstance(Class<?> classInfo) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        //    Class<?> name = Class.forName(className);
        return classInfo.newInstance();
    }
    /*
     * 参数：通过工具类扫描的改包下所有的类信息
     * 返回值：返回一个map集合，里面包含了，所有带ExtService注解的类的信息
     *
     * */
    public  ConcurrentHashMap<String,Class<?>> findClassIsHasAnnotation (  List<Class<?>> classes){
        for (Class<?> classInfo:classes) {
            //判断类上是否有自定义ExtService注解
            ExtService annotation = classInfo.getAnnotation(ExtService.class);
            if (annotation !=null){
                //beans(类名小写,classInfo)
                //获取当前类名
                String className = classInfo.getSimpleName();
                //将类名首字母变为小写
                String beanID =toLowerCaseFirstOne(className);
                System.out.println("beanID = " + beanID);
                //如果当前类上有ExtService注解，将该类的信息，添加到map集合
                beans.put(beanID,classInfo);
            }
        }
        return beans;
    }

    // 首字母转小写
    public static String toLowerCaseFirstOne(String s) {
        if (Character.isLowerCase(s.charAt(0))) {
            return s;
        } else {
            return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
        }
    }

    // 初始化属性
    private void initEntryField() throws Exception {
        // 1.遍历所有的bean容器对象
        for (Map.Entry<String, Class<?>> entry : beans.entrySet()) {
            // 2.判断属性上面是否有加注解@EXTREsource 自动注入
            Object bean = entry.getValue();
            attriAssign(bean);
        }

    }

    // 依赖注入注解原理
    public void attriAssign(Object object) throws Exception {

        // 1.使用反射机制,获取当前类的所有属性
        Class<? extends Object> classInfo = object.getClass();
        Field[] declaredFields = classInfo.getDeclaredFields();

        // 2.判断当前类属性是否存在注解
        for (Field field : declaredFields) {
            ExtResource extResource = field.getAnnotation(ExtResource.class);
            if (extResource != null) {
                // 获取属性名称
                String beanId = field.getName();
                Object bean = getBean(beanId);
                if (bean != null) {
                    // 3.默认使用属性名称，查找bean容器对象 1参数 当前对象 2参数给属性赋值
                    field.setAccessible(true); // 允许访问私有属性
                    field.set(object, bean);
                }

            }
        }

    }
}
package com.lagou.edu.factory;

import com.lagou.edu.anno.*;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 应癫
 *
 * 工厂类，生产对象（使用反射技术）
 */
public class BeanFactory {

    /**
     * 任务一：读取解析xml，通过反射技术实例化对象并且存储待用（map集合）
     * 任务二：对外提供获取实例对象的接口（根据id获取）
     */

    private String packageName;
    private ConcurrentHashMap<String,Object> beans = null;
    //需要实例化对象的Class对象集合

    public BeanFactory(String packageName) throws Exception{
        this.packageName = packageName;
        beans = new ConcurrentHashMap<>();
        initBeans();
        initEntryField();
    }

    // 初始化属性
    private void initEntryField() throws Exception {
        // 1.遍历所有的bean容器对象
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            // 2.判断属性上面是否有加注解@EXTREsource 自动注入
            Object bean = entry.getValue();
            attriAssign(bean);
        }
    }

    // 依赖注入注解原理
    public void attriAssign(Object object) throws Exception {

        // 1.使用反射机制,获取当前类的所有属性
        Class classInfo = object.getClass();
        Field[] declaredFields = classInfo.getDeclaredFields();

        // 2.判断当前类属性是否存在注解
        for (Field field : declaredFields) {
            MyAutowired myAutowired = field.getAnnotation(MyAutowired.class);
            if (myAutowired != null) {
                // 获取属性名称,直接使用属性名称来取，避免自定义变量不对
                String s = field.getGenericType().toString();
                String[] fields = s.split("\\.");
                String beanId = toLowerCaseFirstOne(fields[fields.length-1]);
                System.out.println("beanID = " + beanId);
                Object bean = beans.get(beanId);
                if (bean != null) {
                    // 3.默认使用属性名称，查找bean容器对象 1参数 当前对象 2参数给属性赋值
                    System.out.println("object="+object+",bean="+bean);
                    field.setAccessible(true); // 允许访问私有属性
                    field.set(object,bean );
                }
                //递归当前实例化的对象的属性注入,依赖传递
                attriAssign(beans.get(beanId));
            }
        }
    }

    //初始化对象
    public void initBeans () throws Exception {
        //1.使用java反射机制扫包，获取当前包下所有类
        List<Class<?>> classes = ClassUtil.getClasses(packageName);
        System.out.println("初始化");
        //2.判断类上面是否有注解,返回一个map集合，里面包含了，所有带ExtService注解的类的信息
        ConcurrentHashMap<String,Object>  classHasExtServiceAnnotation = findClassIsHasAnnotation(classes);
        if (classHasExtServiceAnnotation == null || classHasExtServiceAnnotation.isEmpty()){
            System.out.println("该包下所有类都没有ExtServiceAnnotation");
            throw new Exception("该包下所有类都没有ExtServiceAnnotation");
        }
    }

    public Object getBean (String beanId) throws Exception {
        if (beanId.equals("")||beanId==null){
            throw new Exception("bean Id 不能为空");
        }
        //从spring 容器初始化对像
        Object object = beans.get(beanId);
        if (object == null) {
            throw  new Exception("Class not found");
        }
        // 判断当前对象上是否存在事务注解,返回代理对象
        if (object.getClass().isAnnotationPresent(MyTransactional.class)) {
            // 可以去判断字节码对象中的所有方法是否有MyTransactional注解,若有就生成代理对象进行处理
            // 获取代理工厂
            ProxyFactory proxyFactory = (ProxyFactory) beans.get("proxyFactory");
            // 判断委托对象是否实现接口
            Class<?>[] interfaces = object.getClass().getInterfaces();
            if (interfaces.length==0) {
                return proxyFactory.getJdkProxy(object);
            }
            return proxyFactory.getCglibProxy(object);
        }
        //2.使用反射机制初始化对像
        return object;
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
    public  ConcurrentHashMap<String,Object> findClassIsHasAnnotation (  List<Class<?>> classes) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        for (Class<?> classInfo:classes) {
            //判断类上是否有自定义MyService注解
            MyService Serviceannotation = classInfo.getAnnotation(MyService.class);
            if (Serviceannotation !=null){
                //beans(类名小写,classInfo)
                //获取当前类名
                String className = "";
                if(Serviceannotation.value()!=null&&!Serviceannotation.value().equals(""))
                {
                    className = Serviceannotation.value();
                }else {
                    className =  classInfo.getSimpleName();
                }
                //将类名首字母变为小写
                String beanID =toLowerCaseFirstOne(className);
                System.out.println("beanID = " + beanID);
                //如果当前类上有MyService注解，将该类的信息，添加到map集合
                Object o = newInstance(classInfo);
                beans.put(beanID,o);
                //把接口名注入
                Class<?>[] inter = classInfo.getInterfaces();
                for(Class<?> in:inter){
                    String[] ins = in.getName().split("\\.");
                    beans.put(toLowerCaseFirstOne(ins[ins.length-1]),o);
                }
            }

            MyRepository repositoryAnnotation = classInfo.getAnnotation(MyRepository.class);
            if (repositoryAnnotation !=null){
                //beans(类名小写,classInfo)
                //获取当前类名
                String className = "";
                if(repositoryAnnotation.value()!=null&&!repositoryAnnotation.value().equals(""))
                {
                    className = repositoryAnnotation.value();
                }else {
                    className =  classInfo.getSimpleName();
                }
                //将类名首字母变为小写
                String beanID =toLowerCaseFirstOne(className);
                System.out.println("beanID = " + beanID);
                //如果当前类上有MyService注解，将该类的信息，添加到map集合
                Object o = newInstance(classInfo);
                beans.put(beanID,o);
                //把接口名注入
                Class<?>[] inter = classInfo.getInterfaces();
                for(Class<?> in:inter){
                    String[] ins = in.getName().split("\\.");
                    beans.put(toLowerCaseFirstOne(ins[ins.length-1]),o);
                }
            }

            MyComponent myComponent = classInfo.getAnnotation(MyComponent.class);
            if (myComponent !=null){
                //beans(类名小写,classInfo)
                //获取当前类名
                String className = "";
                if(myComponent.value()!=null&&!myComponent.value().equals(""))
                {
                    className = myComponent.value();
                }else {
                    className =  classInfo.getSimpleName();
                }
                //将类名首字母变为小写
                String beanID =toLowerCaseFirstOne(className);
                System.out.println("beanID = " + beanID);
                //如果当前类上有MyService注解，将该类的信息，添加到map集合
                Object o = newInstance(classInfo);
                beans.put(beanID,o);
                //把接口名注入
                Class<?>[] inter = classInfo.getInterfaces();
                for(Class<?> in:inter){
                    String[] ins = in.getName().split("\\.");
                    beans.put(toLowerCaseFirstOne(ins[ins.length-1]),o);
                }
            }
        }
        return beans;
    }


    // 首字母转小写
    public static String toLowerCaseFirstOne(String s) {
        if (Character.isLowerCase(s.charAt(0)))
            return s;
        else
            return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
    }
}

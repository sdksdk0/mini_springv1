package cn.tf.spring.framework.servlet.v1;

import cn.tf.spring.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TFDispatchServlet extends HttpServlet {

    private Properties contextConfig = new Properties();
    private Map<String,Object> ioc = new ConcurrentHashMap<String,Object>();
    private List<String> classNames = new ArrayList<String>();
    private Map<String,Method> handlerMapping = new ConcurrentHashMap<String,Method>();

    @Override
    public void init(ServletConfig config) throws ServletException {

        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //3、初始化扫描到的类，并且将他们放入到IOC容器之中
        doInstance();
        //4、完成依赖注入
        doAutowired();
        //5、初始化handleMapping
        initHandlerMapping();

        System.out.println("init Finish");
    }

    private void initHandlerMapping() {

        if(ioc.isEmpty()){return;}
        for(Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(TFController.class)){continue;}
            String baseUrl = "";
            if(clazz.isAnnotationPresent(TFRequestMapping.class)){
               TFRequestMapping requestMapping = clazz.getAnnotation((TFRequestMapping.class));
               baseUrl = requestMapping.value();
            }

            for (Method method : clazz.getMethods()){
                if(!method.isAnnotationPresent(TFRequestMapping.class)){continue;}
                TFRequestMapping requestMapping = method.getAnnotation(TFRequestMapping.class);
                String url = ("/" + baseUrl + "/" + requestMapping.value())
                        .replaceAll("/+","/");
                handlerMapping.put(url,method);
                System.out.println("Method:"+url+","+method);
            }
        }
    }

    private void doAutowired() {
        if(ioc.isEmpty()){return;}
        for(Map.Entry<String,Object> entry : ioc.entrySet()){
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field:fields){
                if(!field.isAnnotationPresent(TFAutowried.class)){ continue; }
                TFAutowried autowried = field.getAnnotation(TFAutowried.class);
                String beanName = autowried.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doInstance() {
        if(classNames.isEmpty()){return ;}

        try{
            for (String className : classNames){
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(TFController.class)){
                    Object instance = clazz.newInstance();

                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);

                }else if(clazz.isAnnotationPresent(TFService.class)){
                    TFService service = clazz.getAnnotation(TFService.class);

                    //默认用类名首字母注入
                    //如果自己定义了beanName，那么优先使用自己定义的beanName
                    //如果是一个接口，使用接口的类型去自动注入
                    String beanName = service.value();
                    if("".equals(beanName.trim())){
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    for(Class<?> i:clazz.getInterfaces()){
                        if(ioc.containsKey(i.getName())){
                            throw  new Exception("The" +i.getName()+" is exists!");
                        }
                        ioc.put(i.getName(),instance);
                    }
                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void doScanner(String packageName) {
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.","/"));

        File classDir = new File(url.getFile());

        for (File file : classDir.listFiles()){
            if(file.isDirectory()){
                doScanner(packageName + "." +file.getName());
            }else {
                classNames.add(packageName + "." + file.getName().replace(".class",""));
            }
        }


    }

    private void doLoadConfig(String config) {
        //在Spring中是通过Reader去查找和定位对不对
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(config.replace("classpath:",""));

        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if(null != is){is.close();}
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6、调用
        try{
            doDispatch(req,resp);
        }catch (Exception e){
            e.printStackTrace();
            resp.getWriter().write("500"+ Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!!!");
            return;
        }
        Method method = this.handlerMapping.get(url);
        String beanName  = lowerFirstCase( method.getDeclaringClass().getSimpleName());
        Map<String,String[]> params = req.getParameterMap();

        Class<?>[] paramterTypes = method.getParameterTypes();

        Object[] paramValues = new Object[paramterTypes.length];
        for(int i=0;i<paramterTypes.length;i++){
            Class paramterType = paramterTypes[i];
            //paramterType是行参
            if( HttpServletRequest.class == paramterType){
                paramValues[i] = req;
                continue;
            }else if(HttpServletResponse.class == paramterType ){
                paramValues[i] = resp;
                continue;
            }



                Annotation[][] pa = method.getParameterAnnotations();
                for(int j=0;j<pa.length;j++){
                    for(Annotation a:pa[j]){
                        if(a instanceof  TFRequestParam){
                            String paramName = ((TFRequestParam) a).value();
                            if(params.containsKey(paramName)){
                                for(Map.Entry<String,String[]> param : params.entrySet()){

                                    String  value =  Arrays.toString(params.get(paramName))
                                            .replaceAll("\\[|\\]","")
                                            .replaceAll("\\s",",");
                                    paramValues[i] = convert(paramterType,value);
                                }
                            }
                        }
                    }
                }
        }
        method.invoke(ioc.get(beanName),paramValues);

    }

    private Object convert(Class<?> type,String value){
        //多个参数类型的可以用策略模式
        if(Double.class == type){
            return Double.parseDouble(value);
        }else if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }

    private String lowerFirstCase(String str){
        char [] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

}

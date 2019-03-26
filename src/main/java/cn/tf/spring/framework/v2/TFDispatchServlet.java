package cn.tf.spring.framework.v2;

import cn.tf.spring.framework.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class TFDispatchServlet extends HttpServlet {

    private Properties contextConfig = new Properties();
    private Map<String,Object> ioc = new ConcurrentHashMap<String,Object>();
    private List<String> classNames = new ArrayList<String>();
    private List<Handler> handlerMapping = new ArrayList<Handler>();

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
                String regex = ("/" + baseUrl + requestMapping.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern,entry.getValue(),method));
                System.out.println("mapping " + regex + "," + method);
            }
        }
    }

    //自动依赖注入
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
        this.doPost(req, resp);
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
        try {
            Handler handler = getHandler(req);
            if(handler == null){
                resp.getWriter().write("404 Not Found");
                return;
            }
            Class<?>[] paramTypes = handler.getParamTypes();
            Object[] paramValues = new Object[paramTypes.length];
            Map<String,String[]> params = req.getParameterMap();
            //循环请求中的参数
            for(Map.Entry<String,String[]> param:params.entrySet()){
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]","")
                        .replaceAll("\\s",",");
                if(!handler.paramIndexMapping.containsKey((param.getKey()))){continue;};
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index],value);
            }

            if(handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())){
                int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
                paramValues[reqIndex] = req;
            }
            if(handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())){
                int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
                paramValues[respIndex] = resp;
            }
            System.out.println(handler.controller);
            System.out.println(paramValues.toString());
            Object returnValue = handler.method.invoke(handler.controller,paramValues);
            if(null == returnValue || returnValue instanceof  Void){return ;}
            resp.getWriter().write(returnValue.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private Handler getHandler(HttpServletRequest req) throws Exception{
        if(handlerMapping.isEmpty()){ return null; }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : this.handlerMapping) {
            try{
                Matcher matcher = handler.getPattern().matcher(url);
                //如果没有匹配上继续下一个匹配
                if(!matcher.matches()){ continue; }
                return handler;
            }catch(Exception e){
                throw e;
            }
        }
        return null;
    }

    private Object convert(Class<?> type,String value){
        //多个参数类型的可以用策略模式
        if(Double.class == type){
            return Double.valueOf(value);
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

    //保存了一个url和method一一对应的关系
    public class Handler{
        private Object controller;
        private Method method;
        protected Pattern pattern;
        //参数的名字作为key
        protected Map<String,Integer> paramIndexMapping;	//参数顺序
        private Class<?> [] paramTypes;

        /**
         * 构造一个Handler基本的参数
         * @param controller
         * @param method
         */
        public Handler(Pattern pattern, Object controller, Method method){
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramTypes = method.getParameterTypes();
            paramIndexMapping = new HashMap<String,Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method){
            //提取方法中加了注解的参数
            Annotation [] [] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length ; i ++) {
                for(Annotation a : pa[i]){
                    if(a instanceof TFRequestParam){
                        String paramName = ((TFRequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            //提取方法中的request和response参数
            Class<?> [] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length ; i ++) {
                Class<?> type = paramsTypes[i];
                if(type == HttpServletRequest.class ||
                        type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }
            }
        }

        public Object getController() {
            return controller;
        }

        public Method getMethod() {
            return method;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public Map<String, Integer> getParamIndexMapping() {
            return paramIndexMapping;
        }

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }
    }


}

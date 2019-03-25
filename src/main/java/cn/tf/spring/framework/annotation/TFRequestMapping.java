package cn.tf.spring.framework.annotation;

import java.lang.annotation.*;


@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TFRequestMapping {
    String value() default  "";

}

package cn.tf.spring.framework.annotation;

import java.lang.annotation.*;


@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TFAutowried {
    String value() default  "";
}

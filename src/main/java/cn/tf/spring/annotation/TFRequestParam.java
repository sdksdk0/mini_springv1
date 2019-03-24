package cn.tf.spring.annotation;

import java.lang.annotation.*;


@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TFRequestParam {
    String value() default  "";
}

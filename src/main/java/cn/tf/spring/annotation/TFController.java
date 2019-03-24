package cn.tf.spring.annotation;

import java.lang.annotation.*;


@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TFController {

    String value() default  "";
}

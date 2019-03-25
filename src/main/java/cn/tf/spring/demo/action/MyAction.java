package cn.tf.spring.demo.action;


import cn.tf.spring.framework.annotation.TFAutowried;
import cn.tf.spring.framework.annotation.TFController;
import cn.tf.spring.framework.annotation.TFRequestMapping;
import cn.tf.spring.demo.service.IDemoService;

@TFController
public class MyAction {

		@TFAutowried
		IDemoService demoService;
	
		@TFRequestMapping("/index.html")
		public void query(){

		}
	
}

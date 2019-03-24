package cn.tf.spring.demo.action;


import cn.tf.spring.annotation.TFAutowried;
import cn.tf.spring.annotation.TFController;
import cn.tf.spring.annotation.TFRequestMapping;
import cn.tf.spring.demo.service.IDemoService;

@TFController
public class MyAction {

		@TFAutowried
		IDemoService demoService;
	
		@TFRequestMapping("/index.html")
		public void query(){

		}
	
}

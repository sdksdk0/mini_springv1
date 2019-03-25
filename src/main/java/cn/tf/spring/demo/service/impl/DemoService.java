package cn.tf.spring.demo.service.impl;


import cn.tf.spring.framework.annotation.TFService;
import cn.tf.spring.demo.service.IDemoService;

@TFService
public class DemoService implements IDemoService {

	public String get(String name) {
		return "My name is " + name;
	}

}

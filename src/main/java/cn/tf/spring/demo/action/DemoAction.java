package cn.tf.spring.demo.action;



import cn.tf.spring.annotation.TFAutowried;
import cn.tf.spring.annotation.TFController;
import cn.tf.spring.annotation.TFRequestMapping;
import cn.tf.spring.annotation.TFRequestParam;
import cn.tf.spring.demo.service.IDemoService;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@TFController
@TFRequestMapping("/demo")
public class DemoAction {
	
	@TFAutowried
	private IDemoService demoService;
	
	@TFRequestMapping("/query.json")
	public void query(HttpServletRequest req,HttpServletResponse resp,
		   @TFRequestParam("name") String name){
		String result = demoService.get(name);
		System.out.println(result);
		try {
			resp.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@TFRequestMapping("/edit.json")
	public void edit(HttpServletRequest req,HttpServletResponse resp,Integer id){

	}
	
}

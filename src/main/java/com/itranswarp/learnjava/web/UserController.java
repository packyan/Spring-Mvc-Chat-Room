package com.itranswarp.learnjava.web;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.tomcat.util.log.UserDataHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import com.itranswarp.learnjava.entity.User;
import com.itranswarp.learnjava.service.UserService;

@Controller
public class UserController {

	public static final String KEY_USER = "__user__";

	final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	UserService userService;

	@PostMapping(value = "/rest",
			consumes = "application/json;charset=UTF-8",
			produces = "application/json;charset=UTF-8")
	@ResponseBody
	public String rest(@RequestBody User user) {
		return "{\"restSupport\":true}";
	}


	@GetMapping("/")
	public ModelAndView index(HttpSession session) {
		User user = (User) session.getAttribute(KEY_USER);
		Map<String, Object> model = new HashMap<>();
		if (user != null) {
			model.put("user", model);
		}
		return new ModelAndView("index.html", model);
	}
	@GetMapping("/ChangePassword")
	public ModelAndView changePassword(){
		return  new ModelAndView("ChangePassword.html");
	}

	@PostMapping("/ChangePassword")
	public ModelAndView dochangePassword(@RequestParam("email") String email,
										 @RequestParam("password") String password,
										 @RequestParam("New password") String newPassword,
										 @RequestParam("Confirmed password") String comfiredPassword){

		if( !newPassword.equals(comfiredPassword)){
			return new ModelAndView("ChangePassword.html", Map.of("password", email, "error", "two password are not the same"));
		}
		try{
			User user = userService.signin(email,password);
			try{

				userService.updateUserpassword(user, newPassword);
				return new ModelAndView("redirect:/profile");
			}
			catch ( RuntimeException e
			){
				return new ModelAndView("ChangePassword.html", Map.of("password", email, "error", "change password wrong! "));
			}
		}
		catch (
				RuntimeException e
		){
			return new ModelAndView("ChangePassword.html", Map.of("password", email, "error", "old password wrong! "));
		}
	}

	@GetMapping("/register")
	public ModelAndView register() {
		return new ModelAndView("register.html");
	}

	@PostMapping("/register")
	public ModelAndView doRegister(@RequestParam("email") String email, @RequestParam("password") String password,
			@RequestParam("name") String name) {
		try {
			if(userService.getUserByEmail(email) != null){
				return new ModelAndView("register.html", Map.of("email", email, "error", "email exists"));
			}
			User user = userService.register(email, password, name);
			logger.info("user registered: {}", user.getEmail());
		} catch (RuntimeException e) {
			return new ModelAndView("register.html", Map.of("email", email, "error", "Register failed"));
		}
		return new ModelAndView("redirect:/signin");
	}

	@GetMapping("/signin")
	public ModelAndView signin(HttpSession session) {
		User user = (User) session.getAttribute(KEY_USER);
		if (user != null) {
			return new ModelAndView("redirect:/profile");
		}
		return new ModelAndView("signin.html");
	}

	@PostMapping("/signin")
	public ModelAndView doSignin(@RequestParam("email") String email, @RequestParam("password") String password,
			HttpSession session) {
		try {
			User user = userService.signin(email, password);
			session.setAttribute(KEY_USER, user);
		} catch (RuntimeException e) {
			return new ModelAndView("signin.html", Map.of("email", email, "error", "Signin failed"));
		}
		return new ModelAndView("redirect:/profile");
	}

	@GetMapping("/profile")
	public ModelAndView profile(HttpSession session) {
		User user = (User) session.getAttribute(KEY_USER);
		if (user == null) {
			return new ModelAndView("redirect:/signin");
		}
		return new ModelAndView("profile.html", Map.of("user", user));
	}

	@GetMapping("/signout")
	public String signout(HttpSession session) {
		session.removeAttribute(KEY_USER);
		return "redirect:/signin";
	}
}

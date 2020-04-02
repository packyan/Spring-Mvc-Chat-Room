package com.itranswarp.learnjava.web;

import com.itranswarp.learnjava.entity.User;
import com.itranswarp.learnjava.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


//REST，通常输入输出都是JSON，便于第三方调用或者使用页面JavaScript与之交互。
@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    UserService userService;

    @GetMapping("/user/{id}")
    public User users(@PathVariable("id") long id){
        return userService.getUserById(id);
    }

    @PostMapping("/user/signin")
    public Map<String, Object> signin(@RequestBody SignInRequest signinRequest) {
        try {
            User user = userService.signin(signinRequest.email, signinRequest.password);
            return Map.of("user", user);
        } catch (Exception e) {
            return Map.of("error", "SIGNIN_FAILED", "message", e.getMessage());
        }
    }

    public static class SignInRequest {
        public String email;
        public String password;
    }



}

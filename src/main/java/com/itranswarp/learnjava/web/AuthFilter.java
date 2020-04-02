package com.itranswarp.learnjava.web;

import com.itranswarp.learnjava.entity.User;
import com.itranswarp.learnjava.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;


//只是一个普通容器，servlet 并不之情，需要在servlet实例化filter。

@Component
@Qualifier("myAuthFilter")
public class AuthFilter implements Filter {
    @Autowired
    UserService userService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String authHeader = req.getHeader("Authorization");
        if(authHeader != null &&
        authHeader.startsWith("Basic ")){
            System.out.println("get auth header");
            String reqstr = authHeader.split(" ")[1];
            String[] emailAndpassword = reqstr.split(":");
            String email = emailAndpassword[0];
            String password = emailAndpassword[1];
            User userlogin = userService.signin(email,password);
            System.out.println("auth login : " + email);
            req.getSession().setAttribute(UserController.KEY_USER, userlogin);
        }

        chain.doFilter(request,response);

    }


}

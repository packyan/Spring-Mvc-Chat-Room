package com.itranswarp.learnjava;

import java.io.File;
import java.net.http.WebSocket;
import java.util.*;

import javax.servlet.ServletContext;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itranswarp.learnjava.web.ChatHandler;
import com.itranswarp.learnjava.web.ChatHandshakeInterceptor;
import com.mitchellbosecke.pebble.extension.AbstractExtension;
import com.mitchellbosecke.pebble.extension.Extension;
import com.mitchellbosecke.pebble.spring.extension.SpringExtension;
import com.mitchellbosecke.pebble.template.EvaluationContext;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import com.mitchellbosecke.pebble.extension.Function;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.*;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.loader.ServletLoader;
import com.mitchellbosecke.pebble.spring.servlet.PebbleViewResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@ComponentScan
@EnableWebMvc
@EnableTransactionManagement
@EnableWebSocket
@PropertySource("classpath:/jdbc.properties")
public class AppConfig {

	public static void main(String[] args) throws Exception {
		Tomcat tomcat = new Tomcat();
		tomcat.setPort(Integer.getInteger("port", 8080));
		tomcat.getConnector();
		Context ctx = tomcat.addWebapp("", new File("src/main/webapp").getAbsolutePath());
		WebResourceRoot resources = new StandardRoot(ctx);
		resources.addPreResources(
				new DirResourceSet(resources, "/WEB-INF/classes", new File("target/classes").getAbsolutePath(), "/"));
		ctx.setResources(resources);
		tomcat.start();
		tomcat.getServer().await();
	}

	@Bean("localeResolver")
	LocaleResolver createLocaleResolver() {
		var clr = new CookieLocaleResolver();
		clr.setDefaultLocale(Locale.ENGLISH);
		clr.setDefaultTimeZone(TimeZone.getDefault());
		return clr;
	}

	@Bean
	@Qualifier("i18n")
	@Primary
	MessageSource createMessageSource() {
		var messageSource = new ResourceBundleMessageSource();

		// 指定文件是UTF-8编码:
		messageSource.setDefaultEncoding("UTF-8");
		// 指定主文件名:
		messageSource.setBasename("messages");
		System.out.println("print message source" + messageSource.toString());
		return messageSource;
	}
	// 注意注入的MessageSource名称是i18n:
	@Bean
	ObjectMapper createObjectMapper() {
		var om = new ObjectMapper();
		om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		return om;
	}
	// -- Mvc configuration ---------------------------------------------------

	@Bean
	WebMvcConfigurer createWebMvcConfigurer(@Autowired @Qualifier("locale_interceptor") HandlerInterceptor interceptor) {
		return new WebMvcConfigurer() {
			@Override
			public void addResourceHandlers(ResourceHandlerRegistry registry) {
				registry.addResourceHandler("/static/**").addResourceLocations("/static/");
			}

			@Override
			public void addInterceptors(InterceptorRegistry registry) {
				registry.addInterceptor(interceptor);

			}

		};
	}

	@Bean
	WebSocketConfigurer createWebSocketConfigurer(
			@Autowired ChatHandler chatHandler,
			@Autowired ChatHandshakeInterceptor chatInterceptor)
	{
		return new WebSocketConfigurer() {
			public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
				// 把URL与指定的WebSocketHandler关联，可关联多个:
				registry.addHandler(chatHandler, "/chat").addInterceptors(chatInterceptor);
			}
		};
	}

	// -- pebble view configuration -------------------------------------------

/*	@Bean
	ViewResolver createViewResolver(@Autowired ServletContext servletContext) {
		PebbleEngine engine = new PebbleEngine.Builder().autoEscaping(true)
				// cache:
				.cacheActive(false)
				// loader:
				.loader(new ServletLoader(servletContext))
				// extension:
				.extension(new SpringExtension())
				// build:
				.build();
		PebbleViewResolver viewResolver = new PebbleViewResolver();
		viewResolver.setPrefix("/WEB-INF/templates/");
		viewResolver.setSuffix("");
		viewResolver.setPebbleEngine(engine);
		return viewResolver;
	}*/
@Bean
public SpringExtension springExtension() {
	return new SpringExtension();
}
@Bean
ViewResolver createViewResolver(@Autowired ServletContext servletContext, @Autowired @Qualifier("i18n") MessageSource messageSource) {
	PebbleEngine engine = new PebbleEngine.Builder()
			.autoEscaping(true)
			.cacheActive(false)
			.loader(new ServletLoader(servletContext))
			// 添加扩展:
			.extension(createExtension(messageSource))//this.springExtension()
			.build();
	PebbleViewResolver viewResolver = new PebbleViewResolver();
	viewResolver.setPrefix("/WEB-INF/templates/");
	viewResolver.setSuffix("");
	viewResolver.setPebbleEngine(engine);
	return viewResolver;
}

	private Extension createExtension(MessageSource messageSource) {
		return new AbstractExtension() {
			@Override
			public Map<String, Function> getFunctions() {
				return Map.of("_", new Function() {
					public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
						String key = (String) args.get("0");
						List<Object> arguments = this.extractArguments(args);
						Locale locale = (Locale) context.getVariable("__locale__");
						return messageSource.getMessage(key, arguments.toArray(), "???" + key + "???", locale);
					}
					private List<Object> extractArguments(Map<String, Object> args) {
						int i = 1;
						List<Object> arguments = new ArrayList<>();
						while (args.containsKey(String.valueOf(i))) {
							Object param = args.get(String.valueOf(i));
							arguments.add(param);
							i++;
						}
						return arguments;
					}
					public List<String> getArgumentNames() {
						return null;
					}
				});
			}
		};
	}

	// -- jdbc configuration --------------------------------------------------

	@Value("${jdbc.url}")
	String jdbcUrl;

	@Value("${jdbc.username}")
	String jdbcUsername;

	@Value("${jdbc.password}")
	String jdbcPassword;

	@Bean
	DataSource createDataSource() {
		HikariConfig config = new HikariConfig();
		System.out.println(jdbcUrl + " " + jdbcUsername);
		config.setJdbcUrl(jdbcUrl);
		config.setUsername("root");
		config.setPassword(jdbcPassword);
		config.addDataSourceProperty("autoCommit", "false");
		config.addDataSourceProperty("connectionTimeout", "5");
		config.addDataSourceProperty("idleTimeout", "60");
		return new HikariDataSource(config);
	}

	@Bean
	JdbcTemplate createJdbcTemplate(@Autowired DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	PlatformTransactionManager createTxManager(@Autowired DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}
}

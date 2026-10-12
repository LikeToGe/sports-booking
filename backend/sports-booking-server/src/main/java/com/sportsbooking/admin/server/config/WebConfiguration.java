package com.sportsbooking.admin.server.config;

import com.sportsbooking.admin.server.interceptor.AuthInterceptor;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 功能描述: Web 拦截器、本地开发 CORS 和上传目录映射配置。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final Path uploadDirectory;

    public WebConfiguration(AuthInterceptor authInterceptor, @Value("${app.upload.directory}") String uploadDirectory) {
        this.authInterceptor = authInterceptor;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    /**
     * 功能描述: 注册后台和小程序接口的认证拦截范围。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/platform/**", "/venue-admin/**", "/mini/**", "/staff/**",
                        "/auth/admin/logout", "/auth/mini/logout", "/auth/mini/profile/**", "/auth/mini/close-account");
    }

    /**
     * 功能描述: 配置本地管理后台允许访问的跨域规则。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*");
    }

    /**
     * 功能描述: 映射场馆上传文件的公开访问目录。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDirectory.toUri().toString());
    }
}

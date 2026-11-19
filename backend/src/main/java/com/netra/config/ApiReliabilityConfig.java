package com.netra.config;

import jakarta.servlet.http.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import java.util.Set;

/** Local classroom deployment: reject cross-origin writes and fail closed on database outages. */
@Configuration
public class ApiReliabilityConfig implements WebMvcConfigurer {
    private final DataSource source;
    public ApiReliabilityConfig(DataSource source) { this.source = source; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                String origin = request.getHeader("Origin");
                if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                    && ((origin != null && !Set.of("http://127.0.0.1:5173", "http://localhost:5173").contains(origin))
                        || "cross-site".equals(request.getHeader("Sec-Fetch-Site")))) {
                    response.sendError(403, "Use the local dashboard or terminal"); return false;
                }
                if (request.getRequestURI().equals("/api/health")) return true;
                try (var connection = source.getConnection()) {
                    if (!connection.isValid(2)) throw new IllegalStateException();
                } catch (Exception e) {
                    response.setStatus(503); response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"PostgreSQL unavailable. No durable operation was performed.\"}");
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}

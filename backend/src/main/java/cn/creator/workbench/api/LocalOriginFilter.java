package cn.creator.workbench.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

/** 本地单用户版：监听回环地址，拒绝其他网页发起写操作；不等同于多用户身份认证。 */
@Component
public class LocalOriginFilter extends OncePerRequestFilter {
    private final Set<String> allowed;
    @Value("${workbench.test-instance-id:}") private String testInstanceId;
    public LocalOriginFilter(@Value("${workbench.allowed-origins}") String origins){allowed=new HashSet<>(Arrays.asList(origins.split(",")));}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(!testInstanceId.isBlank())response.setHeader("X-Workbench-Test-Instance",testInstanceId);
        String origin=request.getHeader("Origin");
        if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())&&origin!=null&&!allowed.contains(origin)){response.sendError(403,"请求来源未获允许");return;}
        response.setHeader("X-Content-Type-Options","nosniff");chain.doFilter(request,response);
    }
}

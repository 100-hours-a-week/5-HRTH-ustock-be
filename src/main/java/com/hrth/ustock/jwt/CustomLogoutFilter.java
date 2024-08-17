package com.hrth.ustock.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

@RequiredArgsConstructor
public class CustomLogoutFilter extends GenericFilterBean {
    private final JWTUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void doFilter(
            ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        doFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
    }

    private void doFilter(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {

        //path and method verify
        String requestUri = request.getRequestURI();
        if (!requestUri.matches("^\\/logout$")) {

            filterChain.doFilter(request, response);
            return;
        }
        String requestMethod = request.getMethod();
        if (!requestMethod.equals("POST")) {

            filterChain.doFilter(request, response);
            return;
        }

        //get refresh token
        String refresh = null;
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {

            if (cookie.getName().equals("refresh")) {

                refresh = cookie.getValue();
            }
        }

        //refresh null check
        if (refresh == null) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        //expired check
        try {
            jwtUtil.isExpired(refresh);
        } catch (ExpiredJwtException e) {

            //response status code
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // 토큰이 refresh인지 확인 (발급시 페이로드에 명시)
        String category = jwtUtil.getCategory(refresh);
        if (!category.equals("refresh")) {

            //response status code
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        //DB에 저장되어 있는지 확인
        // 1. refresh에서 username get(null check)
        Long userId = jwtUtil.getUserId(refresh);
        if (userId == null) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // 2. redis에서 username refresh get(null check)
        String currentRefresh = (String) redisTemplate.opsForValue().get("RT:"+userId);
        if(currentRefresh == null) {

            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // 3. refresh와 redis-refresh 동일 체크
        if(!currentRefresh.equals(refresh)){

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        //로그아웃 진행
        //Refresh 토큰 redis에서 제거
        redisTemplate.delete(currentRefresh);

        //Access, Refresh 토큰 Cookie 값 0
        Cookie accessLogout = new Cookie("access", null);
        accessLogout.setMaxAge(0);
        accessLogout.setPath("/");

        Cookie refreshLogout = new Cookie("refresh", null);
        refreshLogout.setMaxAge(0);
        refreshLogout.setPath("/");

        response.addCookie(accessLogout);
        response.addCookie(refreshLogout);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}

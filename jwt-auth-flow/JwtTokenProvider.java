package com.hooney.lab.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;

public class JwtTokenProvider {
    /*
     * 전문적인 JWT 토큰 관리 로직
     * - HS512 알고리즘 사용
     * - 클레임(Claim) 기반 권한 정보 포함
     */
    public String createToken(String userId, String roles) {
        long now = (new Date()).getTime();
        Date validity = new Date(now + 3600000); // 1시간 만료

        return Jwts.builder()
                .setSubject(userId)
                .claim("roles", roles)
                .signWith(SignatureAlgorithm.HS512, "[YOUR_SECRET]")
                .setExpiration(validity)
                .compact();
    }
}
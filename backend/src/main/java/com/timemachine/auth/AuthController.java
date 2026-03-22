package com.timemachine.auth;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String password = body.get("password");
        String username = body.get("username");

        if (email == null || password == null || username == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "모든 필드를 입력해주세요."));
        }
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 사용 중인 이메일입니다."));
        }

        User user = userRepository.save(User.builder()
            .email(email).password(password).username(username).build());

        return ResponseEntity.ok(Map.of("message", "회원가입 완료", "userId", user.getId()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String email    = body.get("email");
        String password = body.get("password");

        return userRepository.findByEmail(email)
            .filter(u -> u.getPassword().equals(password))
            .map(u -> {
                session.setAttribute("userId", u.getId());
                session.setAttribute("username", u.getUsername());
                return ResponseEntity.ok(Map.of(
                    "userId", u.getId(),
                    "username", u.getUsername(),
                    "email", u.getEmail()
                ));
            })
            .orElse(ResponseEntity.status(401).body(Map.of("error", "이메일 또는 비밀번호가 올바르지 않습니다.")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "로그아웃 완료"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        return ResponseEntity.ok(Map.of("userId", userId, "username", session.getAttribute("username")));
    }
}

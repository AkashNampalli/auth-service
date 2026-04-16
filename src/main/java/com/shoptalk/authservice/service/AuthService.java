package com.shoptalk.authservice.service;

import com.shoptalk.authservice.dto.AuthResponse;
import com.shoptalk.authservice.dto.LoginRequest;
import com.shoptalk.authservice.dto.RefreshTokenRequest;
import com.shoptalk.authservice.dto.RegisterRequest;
import com.shoptalk.authservice.entity.User;
import com.shoptalk.authservice.exception.DuplicateEmailException;
import com.shoptalk.authservice.exception.InvalidCredentialException;
import com.shoptalk.authservice.exception.InvalidTokenException;
import com.shoptalk.authservice.exception.UserNotFoundException;
import com.shoptalk.authservice.repository.UserRepository;
import com.shoptalk.authservice.security.JwtService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RedisTemplate<String,String> redisTemplate;


    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RedisTemplate<String,String> redisTemplate){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
    }
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        //check duplicate email.
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateEmailException(request.getEmail());
        }

        // Build and Save User
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();
        User savedUser = userRepository.save(user);
        // Generate tokens.
        String userId = savedUser.getId().toString();

        String accessToken = jwtService.generateAccessToken(userId, savedUser.getRole().name(), savedUser.getEmail());
        String refreshToken = jwtService.generateRefreshToken(userId);

        //store refreshtoken
        redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, 7, TimeUnit.DAYS);

        //return response
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .role(savedUser.getRole().name())
                .build();
    }
    @Transactional(readOnly = true)
    public  AuthResponse login(LoginRequest loginRequest){
        //find user by email.
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(()-> new UserNotFoundException("No User found with this email "+loginRequest.getEmail()));
        //check password
        if(!passwordEncoder.matches(loginRequest.getPassword(),user.getPassword())){
            throw new InvalidCredentialException("Inavalid email or password:");
        }

        //Generate token
        String userId = user.getId().toString();

        String accessToken = jwtService.generateAccessToken(userId,user.getRole().name(),user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(userId);

        //stpre refresh token

        redisTemplate.opsForValue().set("refresh:"+userId, refreshToken, 7, TimeUnit.DAYS);

        //return response
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse refresh(RefreshTokenRequest refreshTokenRequest){
        String refreshToken = refreshTokenRequest.getRefreshToken();

        if(!jwtService.isTokenValid(refreshToken)){
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }
        // Extract userID from token

        String userId = jwtService.extractClaims(refreshToken).getSubject();

        String storedToken = redisTemplate.opsForValue().get("refresh:" +userId);

        //check redis does this refresh token exist for user or not
        if(storedToken == null || !storedToken.equals(refreshToken)){
            throw new InvalidTokenException("refresh token not recognised");
        }

        // 4. Find user to get role and email for new access token
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));
        // Generate new access token only - refresh token stays same.
        String newAccessToken = jwtService.generateAccessToken(userId,user.getRole().name(),user.getEmail());

        return AuthResponse.builder()
                .role(user.getRole().name())
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .build();
    }

    public void logout(String token){

        //Extract userId from the access token
        String userId = jwtService.extractClaims(token).getSubject();

        //delete refresh token from redis
        //now even if someone has the refresh token it work.
        redisTemplate.delete("refresh:" +userId);

    }
}

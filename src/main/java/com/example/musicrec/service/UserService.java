package com.example.musicrec.service;

import com.example.musicrec.domain.User;
import com.example.musicrec.dto.user.CreateUserRequest;
import com.example.musicrec.exception.NotFoundException;
import com.example.musicrec.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User create(CreateUserRequest req) {
        User u = new User();
        u.setDisplayName(req.getDisplayName());
        return userRepository.save(u);
    }

    public User get(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}

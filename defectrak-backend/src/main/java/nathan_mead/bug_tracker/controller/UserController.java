package nathan_mead.bug_tracker.controller;

import nathan_mead.bug_tracker.dto.UserDto;
import nathan_mead.bug_tracker.model.User;
import nathan_mead.bug_tracker.model.UserRole;
import nathan_mead.bug_tracker.repository.UserRepository;
import nathan_mead.bug_tracker.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Optional;
import java.util.Map;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Helper function to hash the password using BCrypt
    private String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    // GET endpoint to list all users
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/active")
    public List<User> getActiveUsers() {
        return userRepository.findAllActive();
    }

    // GET endpoint to get a user by ID
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        return userOpt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // POST endpoint to create a new user using UserDto
    @PostMapping("/register")
    public ResponseEntity<?> createUser(@Valid @RequestBody UserDto userDto) {
        logger.info("new user = {}", userDto);
        logger.info("Registering new user at /api/users/register");

        // Normalize the email and check if the user already exists
        String email = userDto.getEmail().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("A user with this email already exists.");
        }

        User user = new User();
        user.setEmail(email);
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setPassword(hashPassword(userDto.getPassword()));
        user.setStatus(userDto.getStatus());

        // Set User Role if provided
        if (userDto.getUserRoleId() != null) {
            Optional<UserRole> userRoleOpt = userRoleRepository.findById(userDto.getUserRoleId());
            if (userRoleOpt.isPresent()) {
                user.setRole(userRoleOpt.get());
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Invalid user role.");
            }
        }

        User createdUser = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    // PUT endpoint to update an existing users password
    @PutMapping("/{id}/password")
    public ResponseEntity<User> updateUserPassword(@PathVariable Long id, @RequestBody String newPassword) {
        String authenticatedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        Optional<User> userOpt = userRepository.findById(id);
        if (!userOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();

        // Compare the authenticated user's email with the user's email in the DB
        if (!user.getEmail().equals(authenticatedUserEmail)) {
            // If they don't match, the user is trying to change someone else's password.
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        user.setPassword(hashPassword(newPassword));
        User updatedUser = userRepository.save(user);
        return ResponseEntity.ok(updatedUser);
    }

    // PUT endpoint to update an existing users status
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUserStatus(@PathVariable Long id, @RequestBody String status) {

        Optional<User> userOpt = userRepository.findById(id);
        if (!userOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();

        user.setStatus(status);
        User updatedUser = userRepository.save(user);
        return ResponseEntity.ok(updatedUser);
    }

    // PUT endpoint to update an existing user's role
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, Long> requestBody) {
        // Retrieve the new role id from the request body
        Long userRoleId = requestBody.get("userRoleId");
        if (userRoleId == null) {
            return ResponseEntity.badRequest().body("Missing userRoleId in request body.");
        }
        
        // Find the user by id
        Optional<User> userOpt = userRepository.findById(id);
        if (!userOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        // Find the role by the provided role id
        Optional<UserRole> roleOpt = userRoleRepository.findById(userRoleId);
        if (!roleOpt.isPresent()) {
            return ResponseEntity.badRequest().body("Invalid userRoleId.");
        }
        
        // Update the user's role and save
        User user = userOpt.get();
        user.setRole(roleOpt.get());
        User updatedUser = userRepository.save(user);
        
        return ResponseEntity.ok(updatedUser);
    }

    // DELETE endpoint to delete a user by ID
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            userRepository.delete(userOpt.get());
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}

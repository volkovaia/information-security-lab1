package org.example.infseclab1;


import org.example.infseclab1.entity.User;
import org.example.infseclab1.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, PasswordEncoder encoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                userRepository.save(new User("admin", encoder.encode("password123")));
            }
        };
    }
}
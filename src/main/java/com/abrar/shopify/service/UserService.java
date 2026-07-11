package com.abrar.shopify.service;

import com.abrar.shopify.entity.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    private final List<User> users = new ArrayList<>();

    public UserService() {

        users.add(new User(
                1L,
                "Abrar",
                "abrar@gmail.com",
                "9876543210",
                "password123"));

        users.add(new User(
                2L,
                "John",
                "john@gmail.com",
                "9876500000",
                "john123"));

        users.add(new User(
                3L,
                "Sara",
                "sara@gmail.com",
                "9876511111",
                "sara123"));
    }

    public List<User> getAllUsers() {
        return users;
    }

    public User getUserById(Long id) {

        for (User user : users) {

            if (user.getId().equals(id)) {
                return user;
            }

        }

        return null;
    }

    public User addUser(User user) {

        users.add(user);

        return user;
    }

    public User updateUser(Long id, User updatedUser) {

        for (User user : users) {

            if (user.getId().equals(id)) {

                user.setName(updatedUser.getName());
                user.setEmail(updatedUser.getEmail());
                user.setPhone(updatedUser.getPhone());
                user.setPassword(updatedUser.getPassword());

                return user;
            }

        }

        return null;
    }

    public User deleteUser(Long id) {

        for (User user : users) {

            if (user.getId().equals(id)) {

                users.remove(user);

                return user;
            }

        }

        return null;
    }
}
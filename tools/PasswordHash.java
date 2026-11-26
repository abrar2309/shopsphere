package com.abrar.shopsphere.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.io.Console;

public class PasswordHash {
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Run this directly in PowerShell.");
            System.exit(1);
        }
        char[] password = console.readPassword("Enter a new ShopSphere password: ");
        System.out.println(new BCryptPasswordEncoder().encode(new String(password)));
        java.util.Arrays.fill(password, '\0');
    }
}

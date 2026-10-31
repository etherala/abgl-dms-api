package com.abgl.dms;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        System.out.println("Admin: " + encoder.encode("Admin@123"));
        System.out.println("John:  " + encoder.encode("John@123"));
        System.out.println("Jane:  " + encoder.encode("Jane@123"));
    }
}

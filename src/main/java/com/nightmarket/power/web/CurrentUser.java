package com.nightmarket.power.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static String name() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "anonymous" : a.getName();
    }

    public static boolean is(String role) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_" + role));
    }
}

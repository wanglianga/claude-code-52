package com.nightmarket.power.web;

import com.nightmarket.power.model.Role;
import com.nightmarket.power.service.DirectoryService;
import com.nightmarket.power.store.RedisStore;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final DirectoryService directory;
    private final RedisStore store;

    public PageController(DirectoryService directory, RedisStore store) {
        this.directory = directory;
        this.store = store;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Authentication auth, Model model) {
        var user = directory.find(auth.getName());
        model.addAttribute("username", auth.getName());
        model.addAttribute("displayName", user == null ? auth.getName() : user.getName());
        model.addAttribute("roleLabel", user == null ? "" : user.getRole().getLabel());
        model.addAttribute("role", user == null ? "" : user.getRole().name());
        return switch (user == null ? Role.VENDOR : user.getRole()) {
            case ELECTRICIAN -> "console-electrician";
            case ADMIN -> "console-admin";
            case SECURITY -> "console-security";
            case CASHIER -> "console-cashier";
            case FIREFIGHTER -> "console-fire";
            default -> "console-vendor";
        };
    }

    @GetMapping("/page/electrician")
    public String electrician() {
        return "console-electrician";
    }

    @GetMapping("/page/admin")
    public String admin() {
        return "console-admin";
    }

    @GetMapping("/page/security")
    public String security() {
        return "console-security";
    }

    @GetMapping("/page/cashier")
    public String cashier() {
        return "console-cashier";
    }

    @GetMapping("/page/fire")
    public String firefighter() {
        return "console-fire";
    }
}

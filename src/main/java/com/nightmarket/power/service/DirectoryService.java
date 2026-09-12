package com.nightmarket.power.service;

import com.nightmarket.power.model.Role;
import com.nightmarket.power.model.User;
import com.nightmarket.power.store.RedisStore;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DirectoryService implements UserDetailsService {

    private final RedisStore store;

    public DirectoryService(RedisStore store) {
        this.store = store;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User u = store.getUser(username);
        if (u == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return org.springframework.security.core.userdetails.User.builder()
                .username(u.getUsername())
                .password(u.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(u.getRole().authority())))
                .build();
    }

    public User find(String username) {
        return store.getUser(username);
    }

    public List<User> byRole(Role role) {
        return store.allUsers().stream().filter(u -> u.getRole() == role).toList();
    }

    /** 值班人员：取该角色的第一位（演示环境） */
    public User duty(Role role) {
        return byRole(role).stream().findFirst().orElse(null);
    }
}

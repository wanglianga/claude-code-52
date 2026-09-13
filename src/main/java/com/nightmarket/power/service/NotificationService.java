package com.nightmarket.power.service;

import com.nightmarket.power.model.Notification;
import com.nightmarket.power.model.Role;
import com.nightmarket.power.store.RedisStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 站内通知：临时加电审批结果、接线任务、收费提醒等 */
@Service
public class NotificationService {

    private final RedisStore store;

    public NotificationService(RedisStore store) {
        this.store = store;
    }

    public Notification push(String targetVendor, Role role, String category, String boxId,
                             String sourceStall, String title, String content,
                             LocalDateTime riskFrom, String riskUntil) {
        Notification n = new Notification("N" + store.nextId("notice"));
        n.setTargetVendor(targetVendor);
        n.setTargetRole(role);
        n.setCategory(category);
        n.setBoxId(boxId);
        n.setSourceStall(sourceStall);
        n.setTitle(title);
        n.setContent(content);
        n.setRiskFrom(riskFrom);
        n.setRiskUntil(riskUntil);
        store.saveNotification(n);
        return n;
    }
}

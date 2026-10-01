package org.lolaf.staffix.admin.server.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.lolaf.staffix.admin.server.web.NotImplemented.notImplemented;

@RestController
public class UserController {

    @GetMapping("/api/me")
    public CurrentUser getCurrentUser() {
        throw notImplemented();
    }
}

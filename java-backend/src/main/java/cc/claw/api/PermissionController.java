package cc.claw.api;

import cc.claw.permission.PermissionInterceptor;
import cc.claw.permission.PermissionResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PermissionController {

    private final PermissionInterceptor permissionInterceptor;

    public PermissionController(PermissionInterceptor permissionInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
    }

    @PostMapping("/permission/respond")
    public void respond(@RequestBody PermissionResponse response) {
        permissionInterceptor.respond(response);
    }
}
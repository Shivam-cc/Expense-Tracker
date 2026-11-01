package com.expensetracker.controller.v1;

import com.expensetracker.dto.v1.MinVersionV1Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
public class AppV1Controller {

    private final MinVersionV1Response minVersion;

    public AppV1Controller(
            @Value("${expenseflow.min-version.android:1.0.0}") String android,
            @Value("${expenseflow.min-version.ios:1.0.0}") String ios) {
        this.minVersion = new MinVersionV1Response(android, ios);
    }

    /** Public: clients call it before sign-in to decide whether to force an update. */
    @GetMapping("/min-version")
    public MinVersionV1Response minVersion() {
        return minVersion;
    }
}

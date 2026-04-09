package com.guardian.hadoop.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendRouteController {

    @GetMapping(value = {
        "/",
        "/incidents",
        "/incidents/{id:[0-9]+}",
        "/settings"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}

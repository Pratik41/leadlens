package com.leadlens.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The Angular app routes /today and /insights in the browser. When one of those URLs is opened
 * directly (bookmark, reload, shared link), the server must answer with the app, not a 404.
 */
@Controller
public class SpaRoutes {

    @GetMapping({"/today", "/insights"})
    public String app() {
        return "forward:/index.html";
    }
}

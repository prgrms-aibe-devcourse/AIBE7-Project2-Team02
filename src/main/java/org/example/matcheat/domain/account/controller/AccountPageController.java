package org.example.matcheat.domain.account.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountPageController {
    @GetMapping("/login")
    public String login() {
        return "account/login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "account/signup";
    }

    @GetMapping("/suspended")
    public String suspended() {
        return "account/suspended";
    }

    @GetMapping({
            "/mypage",
            "/mypage/requests",
            "/mypage/products",
            "/mypage/purchases",
            "/mypage/sales",
            "/mypage/offers",
            "/mypage/chats",
            "/mypage/reports"
    })
    public String mypage() {
        return "account/mypage";
    }
}

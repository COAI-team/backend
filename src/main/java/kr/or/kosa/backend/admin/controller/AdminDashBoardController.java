package kr.or.kosa.backend.admin.controller;

import kr.or.kosa.backend.admin.service.AdminDashBoardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminDashBoardController {
    private final AdminDashBoardService adminDashBoardService;
    public AdminDashBoardController(AdminDashBoardService adminDashBoardService) {
        this.adminDashBoardService = adminDashBoardService;
    }

    @GetMapping("/usersignupcount")
    public int userSignupCount() {
        System.out.println("??");
        return adminDashBoardService.dashBoards();
    }

}

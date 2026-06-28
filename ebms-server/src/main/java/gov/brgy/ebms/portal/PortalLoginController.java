package gov.brgy.ebms.portal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/portal")
public class PortalLoginController {

    @GetMapping("/login")
    public String loginPage(
        @RequestParam(required = false) String error,
        @RequestParam(required = false) String logout,
        Model model
    ) {
        if (error != null) {
            model.addAttribute("loginError", true);
        }
        if (logout != null) {
            model.addAttribute("loggedOut", true);
        }
        return "portal/login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        return "portal/dashboard";
    }
}

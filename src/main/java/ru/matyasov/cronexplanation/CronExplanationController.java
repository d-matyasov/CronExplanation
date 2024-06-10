package ru.matyasov.cronexplanation;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class CronExplanationController implements ErrorController {

    @GetMapping("/error")
    public String handleError() {
        return "error-page";
    }

    @GetMapping("/")
    public String index() {
        return "main-page";
    }

    @PostMapping("/")
    public String explain(@ModelAttribute("string") String string, Model model) {

        model.addAttribute("string", string);

        model.addAttribute("result", CronExplanation.explain(string));

        return "main-page";
    }

}

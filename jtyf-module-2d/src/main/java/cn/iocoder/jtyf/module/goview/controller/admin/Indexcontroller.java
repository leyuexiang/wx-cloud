package cn.iocoder.jtyf.module.goview.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;

@RestController
public class Indexcontroller {

    @GetMapping("/")
    public void index(HttpServletResponse response) throws IOException {
        response.setContentType("text/html; charset=utf-8");
        PrintWriter out = response.getWriter();
        out.println("<p style='color:orange'>goview后台首页</p>");
        out.flush();
        out.close();

    }
}

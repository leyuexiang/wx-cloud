/*
package cn.iocoder.jtyf.module.semi.controller.admin.common.presentation;

import cn.iocoder.jtyf.module.semi.controller.admin.common.util.ApiUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class PageNotFoundController {
    @RequestMapping(value="/**")
    public String main(HttpServletRequest req, HttpServletResponse res) throws Exception {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        ApiUtil.setBadRequestResponse(req, res);
        return null;
    }
}
*/

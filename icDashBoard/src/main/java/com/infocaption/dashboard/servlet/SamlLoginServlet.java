package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.util.SamlConfigUtil;
import com.onelogin.saml2.Auth;
import com.onelogin.saml2.settings.Saml2Settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * SAML2 SSO Login Servlet.
 * GET /saml/login — Builds a SAML AuthnRequest and redirects to the IdP (Entra ID).
 */
public class SamlLoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SamlLoginServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // If already logged in, go straight to dashboard
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            resp.sendRedirect(req.getContextPath() + "/dashboard.jsp");
            return;
        }

        try {
            Saml2Settings settings = SamlConfigUtil.getSaml2Settings(getServletContext());
            Auth auth = new Auth(settings, req, resp);
            auth.login();
        } catch (Exception e) {
            log.error("Failed to initiate SAML login redirect", e);
            resp.sendRedirect(req.getContextPath() + "/login?error=sso");
        }
    }
}

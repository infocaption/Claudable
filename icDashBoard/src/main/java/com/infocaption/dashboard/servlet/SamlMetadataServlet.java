package com.infocaption.dashboard.servlet;

import com.infocaption.dashboard.util.SamlConfigUtil;
import com.onelogin.saml2.settings.Saml2Settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * SAML2 SP Metadata Servlet.
 * GET /saml/metadata — Returns the Service Provider metadata XML.
 * Use this URL when configuring the Enterprise Application in Azure Entra ID.
 */
public class SamlMetadataServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SamlMetadataServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        try {
            Saml2Settings settings = SamlConfigUtil.getSaml2Settings(getServletContext());
            String metadata = settings.getSPMetadata();

            resp.setContentType("application/xml; charset=UTF-8");
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            PrintWriter out = resp.getWriter();
            out.write(metadata);
            out.flush();

        } catch (Exception e) {
            log.error("Failed to generate SP metadata", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getWriter().write("Error generating SP metadata: " + e.getMessage());
        }
    }
}

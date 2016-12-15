/**
 * Copyright (c) 2016, University of South Africa and/or its affiliates. All
 * rights reserved. DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE
 * HEADER.
 * 
* This code is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 only, as published by
 * the Free Software Foundation.
 * 
* This code is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License version 2 for more
 * details (a copy is included in the LICENSE file that accompanied this code).
 * 
* You should have received a copy of the GNU General Public License version 2
 * along with this work; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
*
 */
package za.co.eon.econtentsolutions.component.abstractlticomponent;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tsugi.Launch;
import org.tsugi.Output;
import org.tsugi.Tsugi;
import org.tsugi.TsugiFactory;
import org.tsugi.util.TsugiUtils;

/**
 * AbstractLTIComponentServlet is the abstract base class for all servlet
 * component contexts.
 * 
 * @author Quintin De Clercq
 * @version %I%, %G%
 * @since 1.0
 *
 */
public class AbstractLTIComponentServlet extends HttpServlet {

    private Log log = LogFactory.getLog(AbstractLTIComponentServlet.class);
    private Tsugi tsugi = null;

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try (PrintWriter out = response.getWriter()) {
            Launch launch = tsugi.getLaunch(request, response);
            if (launch.isComplete()) {
                launch.getOutput().flashSuccess("LTI Launch validated and redirected");
                log.info("LTI Launch validated and redirected...");
                return;
            }
            if (!launch.isValid()) {
                out.println("<pre>");
                out.println("Launch is not valid but nowhere to redirect");
                out.println(launch.getErrorMessage());
                out.println("Base String:");
                out.println(launch.getBaseString());
                out.println("</pre>");
                out.close();

                throw new RuntimeException(launch.getErrorMessage());
            }

            HttpSession session = request.getSession();
            Output o = launch.getOutput();

            Properties versions = o.header(out);
            o.bodyStart(out);
            o.flashMessages(out);

            //
            out.println("<pre>");

            // Dump out some stuff from the Request Object
            out.println("");
            out.println("<a href=\"http://docs.oracle.com/javaee/6/api/javax/servlet/http/HttpServletRequest.html\" target=\"_blank\">HttpServletRequest</a> data:");
            out.println("req.getRequestURL()=" + request.getRequestURL());
            out.println("req.getMethod()=" + request.getMethod());
            out.println("req.getServletPath()=" + request.getServletPath());
            out.println("req.getPathInfo()=" + request.getPathInfo());
            out.println("req.getQueryString()=" + request.getQueryString());

            out.println("");
            out.print("<a href=\"");
            out.print(launch.getGetUrl(null) + "/zap");
            out.println("\">Click here to see if we stay logged in with a GET</a>");

            out.println("");
            out.println("Using the <a href=\"http://csev.github.io/tsugi-java/apidocs/index.html\" target=\"_blank\">Tsugi API</a>:");
            out.println("Content Title: " + launch.getContext().getTitle());
            out.println("Context Settings: " + launch.getContext().getSettings().getSettingsJson());
            out.println("User Email: " + launch.getUser().getEmail());
            out.println("isInstructor()=" + launch.getUser().isInstructor());
            out.println("isTenantAdmin()=" + launch.getUser().isTenantAdmin());
            out.println("Link Title: " + launch.getLink().getTitle());
            out.println("Link Settings: " + launch.getLink().getSettings().getSettingsJson());
            out.println("Sourcedid: " + launch.getResult().getSourceDID());
            out.println("Service URL: " + launch.getService().getURL());
            out.println("");
            out.println("JavaScript library versions:");
            out.println(TsugiUtils.dumpProperties(versions));

            out.println("");
            out.println("Using the provided JDBC connection:");
            Connection c = null;
            try {
                c = launch.getConnection();
                out.println("Connection: " + c);
                DatabaseMetaData meta = c.getMetaData();
                String productName = meta.getDatabaseProductName();
                String productVersion = meta.getDatabaseProductVersion();
                String URL = meta.getURL();
                out.println("Connection product=" + productName + " version=" + productVersion);
                out.println("Connection URL=" + URL);
            }
            catch (Exception ex) {
                log.error("Unable to get connection metadata", ex);
                out.println("Unable to get connection metadata:" + ex.getMessage());
            }

            // Do a simple query just to see how it is done
            if (c != null) {
                Statement stmt = null;
                String query = "SELECT plugin_id, plugin_path FROM lms_plugins;";

                try {
                    stmt = c.createStatement();
                    ResultSet rs = stmt.executeQuery(query);
                    int num = 0;
                    while (rs.next()) {
                        String plugin_path = rs.getString("plugin_path");
                        out.println("plugin_path=" + plugin_path);
                        num++;
                    }
                    out.println("Successfully read " + num + " rows from the database");
                }
                catch (SQLException e) {
                    out.println("Problems reading database");
                    out.println("INSERT INTO mjjs (name) VALUES ('tsugi');");
                    e.printStackTrace();
                }
            }

            // Cheat and look at the internal data Tsugi maintains - this depends on
            // the JDBC implementation
            Properties sess_row = (Properties) session.getAttribute("lti_row");
            if (sess_row != null) {
                out.println("");
                out.println("Tsugi-managed internal session data (Warning: org.tsugi.impl.jdbc.Tsugi_JDBC only)");
                String x = TsugiUtils.dumpProperties(sess_row);
                out.println(x);
            }

            out.println("</pre>");

            // Do the Footer
            o.footerStart(out);
            out.println("<!-- App footer stuff goes here -->");
            o.footerEnd(out);

            out.close();
        }
        catch (RuntimeException re) {
            try (PrintWriter out = response.getWriter()) {
                /* TODO output your page here. You may use following sample code. */
                out.println("<!DOCTYPE html>");
                out.println("<html>");
                out.println("<head>");
                out.println("<title>AbstractLTIComponentServlet Error</title>");
                out.println("</head>");
                out.println("<body>");
                out.println("<h1>AbstractLTIComponentServlet</h1>");
                out.println("<h3 style=\"color: red;\">Error</h3>");
                out.println("<p>Servlet AbstractLTIComponentServlet at " + request.getContextPath() + " threw an exception.</p>");
                out.println("<p>Exception: " + re.toString() + "<br />");
                out.println("Message: " + re.getMessage() + "<br />");
                out.println("Stacktrace:</p>");
                out.println("<p>" + re.getStackTrace().toString() + "</p>");
                out.println("</body>");
                out.println("</html>");
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }

    /**
     * Initializes the servlet for use with the Tsugi LTI wrapper.
     *
     * @param config servlet configuration
     * @throws ServletException if a servlet-specific error occurs
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        if (tsugi == null) {
            tsugi = TsugiFactory.getTsugi();
        }
        System.out.println("Tsugi init=" + tsugi);
    }// </editor-fold>
}

package com.codex.dialog.controller;

import com.codex.dialog.dao.AuthorDAO;
import com.codex.dialog.model.Auther;
import com.codex.dialog.model.Forgot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;


@Controller
@RequestMapping("/")
public class AuthorController {

    @Autowired
    AuthorDAO authorDAO;

    @RequestMapping(value = "addAuther", method = RequestMethod.POST)
    public String addQuestion(HttpServletRequest request) throws SQLException, ClassNotFoundException {
        Auther auther = new Auther("", request.getParameter("designation"), request.getParameter("form-email"), request.getParameter("password"), request.getParameter("name"));
        boolean isAdded = false;
        boolean isAuthExists = authorDAO.isAuther(request.getParameter("form-email"));
        String msg;
        if (!isAuthExists) {
            isAuthExists = authorDAO.addAuther(auther);
            Auther addedAuther = authorDAO.getAuther(request.getParameter("form-email"));
            HttpSession session = request.getSession();
            session.setAttribute("authId", addedAuther.getAuthId());
            session.setAttribute("authName", addedAuther.getName());
            msg = "Author Added Successfully";
        } else {
            msg = "Failed To Add Author";
        }
        request.setAttribute("msg", msg);
        return "Question/home";
    }

    @RequestMapping(value = "mailTaken", method = RequestMethod.GET)
    public void mailValidation(HttpServletRequest request, HttpServletResponse response) throws SQLException, ClassNotFoundException, IOException {
        PrintWriter out = response.getWriter();
        boolean isAuthExists = authorDAO.isAuther(request.getParameter("email"));
        if (!isAuthExists) {
            out.print("1");
        } else {
            out.print("0");
        }
    }

    @RequestMapping(value = "auth", method = RequestMethod.POST)
    public String authenticateAuthor(HttpServletRequest request) throws SQLException, ClassNotFoundException {
        String email = request.getParameter("email");
        String pass = request.getParameter("pass");

        String authId = authorDAO.authenticate(pass, email);

        if (!authId.equals("")) {
            String authName = authorDAO.getAuthor(authId).getName();
            HttpSession session = request.getSession();
            session.setAttribute("authId", authId);
            session.setAttribute("authName", authName);
            return "Question/home";

        } else {
            String msg = "Wrong Credentials";
            request.setAttribute("msg", msg);
            return "Question/index";
        }
    }

    @RequestMapping(value = "forgot", method = RequestMethod.POST)
    @ResponseBody
    public String forgetCheck(HttpServletRequest request) throws SQLException, ClassNotFoundException {
        String email = request.getParameter("email");
        Auther forgot = authorDAO.getAuthorByEmail(email);
        if (forgot != null) {
            return "1";
        } else {
            return "0";
        }
    }

    @RequestMapping(value = "forgotSend", method = RequestMethod.POST)
    @ResponseBody
    public String forgetSendEmail(HttpServletRequest request, HttpServletResponse response) throws SQLException, ClassNotFoundException, IOException {
        String email = request.getParameter("email");
        Auther forgot = authorDAO.getAuthorByEmail(email);
        String name = forgot.getName();
        String uuid = UUID.randomUUID().toString();
        Forgot user = new Forgot(forgot.getAuthId(), uuid);
        authorDAO.addForgotUser(user);

        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com"); //SMTP Host
        props.put("mail.smtp.port", "587"); //TLS Port
        props.put("mail.smtp.auth", "true"); //enable authentication
        props.put("mail.smtp.starttls.enable", "true"); //enable

        final String from = "tcodex7@gmail.com";
        final String password = "codexfamily123";
        final String to = email;

        //get Session
        Session session = Session.getDefaultInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(from, password);
                    }
                });

        //compose message
        try {
            MimeMessage message = new MimeMessage(session);
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject("Password Reset  Request");
            BodyPart messageBodyPart = new MimeBodyPart();
            String textToSend = "<h1>Hi " + name + ", It seems to be you are trying to reset password</h1><p>" +
                    "Please click on this link to continue (This link will be expired after 15 minutes) </p> " +
                    "<pre style=\"background: #f6f6f3; border: 1px solid #d8d7d4; border-radius: 3px; display: inline-block; font-family: monospace; font-size: 1rem; font-weight: 600; line-height: 1em; margin: 0; padding: 5px 8px;\">"
                    + "<a href='http://chemistrytutor.ddns.net/forgotPassword/" + uuid + "'>Reset Password Now</a>" +
                    "</pre><h4>(This link can be used one time only.)</h4>" +
                    "<hr><h5>Powered by CODEX</h5>";
            messageBodyPart.setContent(textToSend, "text/html");
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            message.setContent(multipart);

            //send message
            Transport.send(message);
            return "1";
        } catch (MessagingException e) {
            return "0";
        }
    }

    @RequestMapping(value = "forgotPassword/{uuid}", method = RequestMethod.GET)
    public String resetPassword(@PathVariable String uuid, ModelMap model) throws SQLException, ClassNotFoundException {
        final long duration = 15 * 60 * 1000;//15 minutes
        Forgot forgot = authorDAO.getForgotUser(uuid);
        if (forgot != null) {
            long timeDiff = new Date().getTime() - forgot.getTimestamp().getTime();
            if (forgot.isUsed()) {
                model.put("msg", "Sorry your link is expired as it is used before!!!");
                return "Question/invalidUUID";
            } else if (timeDiff > duration) {
                model.put("msg", "Sorry your link is expired as it is too old!!!");
                return "Question/invalidUUID";
            } else {
                String userId = authorDAO.getForgotUser(uuid).getUserId();
                Auther user = authorDAO.getAuthor(userId);
                model.put("auther", user);
                authorDAO.used(uuid);
                return "Question/forgotPassword";
            }

        } else {
            model.put("msg", "Sorry your link is invalid!!!");
            return "Question/invalidUUID";
        }

    }

    @RequestMapping(value = "resetPasswordNow", method = RequestMethod.POST)
    @ResponseBody
    public String resetPasswordNow(HttpServletRequest request) throws SQLException, ClassNotFoundException {
        String id = request.getParameter("authId");
        String password = request.getParameter("password");
        if (authorDAO.resetPassword(password, id)) {
            return "1";
        } else {
            return "0";
        }

    }

    @RequestMapping(value = "getAuthor", method = RequestMethod.POST)
    public String getAuthor(HttpServletRequest request) throws SQLException, ClassNotFoundException {
        HttpSession session = request.getSession();
        String authId = (String) session.getAttribute("authId");

        if (!authId.equals("")) {
            //String msg = "Authenticated User :"+authId ;
            Auther auther = authorDAO.getAuthor(authId);
            auther.getDesig();
            request.setAttribute("auther", auther);
            return "Question/viewAuther";
        } else {
            return "";
        }
    }

    @RequestMapping(value = "updateAuther", method = RequestMethod.POST)
    public String updateAuthor(HttpServletRequest request) throws SQLException, ClassNotFoundException {
        Auther auther = new Auther(request.getParameter("authId"), request.getParameter("designation"), request.getParameter("email"), request.getParameter("password"), request.getParameter("name"));
        String msg;
        if (!auther.getPassword().equals("")) {
            if (authorDAO.updateAuthor(auther)) {
                msg = "Author Updated Successfully";
            } else {
                msg = "Update Unsuccessful";
            }
        } else {
            if (authorDAO.updateAuthorWithoutPassword(auther)) {
                msg = "Author Updated Successfully";
            } else {
                msg = "Update Unsuccessful";
            }
        }
        request.setAttribute("msg", msg);
        return "Question/home";
    }

    @RequestMapping(value = "checkOldVal", method = RequestMethod.GET)
    public void confirmOldPassword(HttpServletRequest request, HttpServletResponse response) throws SQLException, ClassNotFoundException, IOException {
        String email = request.getParameter("email");
        String pass = request.getParameter("pass");
        PrintWriter out = response.getWriter();
        String authId = authorDAO.authenticate(pass, email);
        if (authId.equals("")) {
            out.print("0");
        } else {
            out.print("1");
        }
    }

    @RequestMapping(value = "verify", method = RequestMethod.GET)
    public void confirmEmail(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter out = response.getWriter();
        Random random = new Random();

        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com"); //SMTP Host
        props.put("mail.smtp.port", "587"); //TLS Port
        props.put("mail.smtp.auth", "true"); //enable authentication
        props.put("mail.smtp.starttls.enable", "true"); //enable

        final String from = "tcodex7@gmail.com";
        final String password = "codexfamily123";
        final String to = request.getParameter("email");
        final String id = String.format("%04d", random.nextInt(10000));
        //get Session
        Session session = Session.getDefaultInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(from, password);
                    }
                });
        //compose message
        try {
            MimeMessage message = new MimeMessage(session);
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject("Welcome to Chemistry Tutor Web");
            BodyPart messageBodyPart = new MimeBodyPart();
            String textToSend = "<h1>Thanks for joining Chemistry Tutor Web!</h1><p>" +
                    "You may enter your confirmation code to continue: </p> " +
                    "<pre style=\"background: #f6f6f3; border: 1px solid #d8d7d4; border-radius: 3px; display: inline-block; font-family: monospace; font-size: 1rem; font-weight: 600; line-height: 1em; margin: 0; padding: 5px 8px;\">" + id + "</pre><hr><h5>Powered by CODEX</h5>";
            messageBodyPart.setContent(textToSend, "text/html");
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            message.setContent(multipart);

            //send message
            Transport.send(message);
            out.print(id);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        } finally {
            out.close();
        }
    }
}

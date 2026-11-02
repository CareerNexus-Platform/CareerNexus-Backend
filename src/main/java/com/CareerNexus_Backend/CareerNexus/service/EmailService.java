package com.CareerNexus_Backend.CareerNexus.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${spring.mail.display-name}")
    private String senderName;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Sends a professional HTML invitation for the exam slot.
     */
    @Async
    public void sendExamInvite(String toEmail, String studentId, String studentName, String jobTitle, String roundName, String slotStart, String slotEnd, Long assessmentId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(senderEmail, senderName));
            helper.setTo(toEmail);
            helper.setSubject("Assessment Invitation: " + jobTitle);

            // Professional HTML Template
            String htmlBody = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; border: 1px solid #eee; padding: 20px;">
                    <div style="background-color: #004a99; color: white; padding: 10px; text-align: center; border-radius: 5px 5px 0 0;">
                        <h2>Career Nexus Assessment</h2>
                    </div>
                    <p>Dear <b>%s</b>,</p>
                    <p>An assessment has been scheduled for the position of <b>%s</b>.</p>
                    <p> Round Name <b>%s</b>.</p>
                    <div style="background-color: #f9f9f9; padding: 15px; border-left: 5px solid #004a99; margin: 20px 0;">
                        <p><strong>Date & Time Slot:</strong></p>
                        <p>Start: %s</p>
                        <p>End: %s</p>
                    </div>
                    
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #004a99; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; font-weight: 600; font-size: 15px; display: inline-block;">Start Assessment</a>
                    </div>
                    
                    <p>Please click the button above and log in to your dashboard during the active slot to start the test. The test will only be accessible during the specified time window.</p>
                    <br>
                    <p>Regards,<br><b>Career Nexus Team</b></p>
                </div>
                """.formatted(studentName, jobTitle,roundName, slotStart, slotEnd, frontendUrl.replaceAll("/+$", "") + "/student/" + studentId + "/test/" + assessmentId);

            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Email dispatch failed for: " + toEmail + " Error: " + e.getMessage());
        }
    }

    /**
     * Sends a professional HTML confirmation receipt when a student applies for a job.
     */
    @Async
    public void sendApplicationConfirmationEmail(String toEmail, String studentName, String companyName, String jobTitle, String location) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(senderEmail, senderName));
            helper.setTo(toEmail);
            helper.setSubject("Application Received: " + jobTitle + " at " + companyName);

            // Dynamic frontend link based on environment
            String applicationsLink = frontendUrl.replaceAll("/+$", "") + "/student/" + studentName + "/applications";

            String htmlBody = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; border: 1px solid #ddd; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1);">
                    <div style="background: linear-gradient(135deg, #004a99, #00234b); color: white; padding: 25px 20px; text-align: center;">
                        <h2 style="margin: 0; font-size: 24px; font-weight: 600;">Application Received</h2>
                        <p style="margin: 10px 0 0; opacity: 0.9; font-size: 14px;">Career Nexus platform</p>
                    </div>
                    
                    <div style="padding: 30px 20px; background-color: #ffffff; color: #333;">
                        <p style="font-size: 16px; line-height: 1.6;">Dear <b>%s</b>,</p>
                        
                        <p style="font-size: 16px; line-height: 1.6;">Great news! Your application for the <b>%s</b> position at <b>%s</b> has been successfully submitted to the recruitment team.</p>
                        
                        <div style="background-color: #f4f7f6; padding: 20px; border-radius: 6px; margin: 25px 0;">
                            <h3 style="margin-top: 0; font-size: 18px; color: #004a99; border-bottom: 2px solid #e0e0e0; padding-bottom: 10px;">Application Summary</h3>
                            <p style="margin: 10px 0;"><strong>Company:</strong> %s</p>
                            <p style="margin: 10px 0;"><strong>Role:</strong> %s</p>
                            <p style="margin: 10px 0;"><strong>Location:</strong> %s</p>
                            <p style="margin: 10px 0;"><strong>Status:</strong> <span style="background-color: #e3f2fd; color: #1976d2; padding: 4px 8px; border-radius: 4px; font-weight: bold; font-size: 12px;">SUBMITTED</span></p>
                        </div>
                        
                        <p style="font-size: 15px; line-height: 1.6; color: #555;">The recruitment team will review your profile and reach out to you if you are shortlisted for the next rounds of the hiring process. You will be notified via email of any scheduled assessments.</p>
                        
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="background-color: #004a99; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; font-weight: 600; font-size: 15px; display: inline-block;">View My Applications</a>
                        </div>
                        
                        <p style="font-size: 15px; margin-top: 30px; border-top: 1px solid #eee; padding-top: 20px;">
                            Best wishes for your career,<br>
                            <strong style="color: #004a99;">Career Nexus Team</strong>
                        </p>
                    </div>
                </div>
                """.formatted(studentName, jobTitle, companyName, companyName, jobTitle, location != null ? location : "Not Specified", applicationsLink);

            helper.setText(htmlBody, true);
            mailSender.send(message);
            System.out.println("Dispatched Application Confirmation Email to: " + toEmail);
        } catch (Exception e) {
            System.err.println("Application Confirmation Email dispatch failed for: " + toEmail + " Error: " + e.getMessage());
        }
    }

    /**
     * Sends a festive and informative welcome email to newly created users.
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String userId, String rawPassword, String role) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress(senderEmail, senderName));
            helper.setTo(toEmail);
            helper.setSubject("Welcome to Career Nexus - Your Account is Ready!");

            String roleSpecificText = switch (role.toLowerCase()) {
                case "student" -> "your gateway to career growth, skill assessments, and top job opportunities.";
                case "recruiter" -> "your platform for finding the best talent and managing your recruitment pipeline efficiently.";
                case "tpo" -> "your dashboard for coordinating placements, tracking student progress, and bridging ties with industry.";
                default -> "your comprehensive career development platform.";
            };

            String loginUrl = frontendUrl.replaceAll("/+$", "") + "/" + ("admin".equalsIgnoreCase(role) ? "tpo" : role) + "/login";

            String htmlBody = """
                <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: auto; border: 1px solid #e0e0e0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.08);">
                    <div style="background: linear-gradient(135deg, #6366f1, #4f46e5); color: white; padding: 40px 20px; text-align: center;">
                        <div style="font-size: 40px; margin-bottom: 10px;">🚀</div>
                        <h1 style="margin: 0; font-size: 28px; font-weight: 800; letter-spacing: -0.5px;">Welcome to Career Nexus!</h1>
                        <p style="margin: 10px 0 0; opacity: 0.9; font-size: 16px;">The search ends here.</p>
                    </div>
                    
                    <div style="padding: 40px 30px; background-color: #ffffff; color: #1e293b;">
                        <p style="font-size: 18px; line-height: 1.6; margin-bottom: 20px;">Hello!</p>
                        
                        <p style="font-size: 16px; line-height: 1.6; color: #475569;">
                            We are thrilled to have you on board. Your account has been successfully created as a <b>%s</b>. Career Nexus is %s
                        </p>
                        
                        <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; padding: 25px; border-radius: 12px; margin: 30px 0;">
                            <h3 style="margin-top: 0; font-size: 14px; color: #6366f1; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 15px;">Your Login Credentials</h3>
                            
                            <table style="width: 100%%; border-collapse: collapse;">
                                <tr>
                                    <td style="padding: 8px 0; color: #64748b; font-size: 14px; width: 100px;">Username:</td>
                                    <td style="padding: 8px 0; color: #1e293b; font-weight: 700;">%s</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Email:</td>
                                    <td style="padding: 8px 0; color: #1e293b; font-weight: 700;">%s</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0; color: #64748b; font-size: 14px;">Password:</td>
                                    <td style="padding: 8px 0; color: #4f46e5; font-family: monospace; font-size: 18px; font-weight: 900; letter-spacing: 1px;">%s</td>
                                </tr>
                            </table>
                        </div>
                        
                        <div style="text-align: center; margin: 40px 0;">
                            <a href="%s" style="background-color: #4f46e5; color: white; padding: 16px 32px; text-decoration: none; border-radius: 12px; font-weight: 700; font-size: 16px; display: inline-block; box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);">Login to Dashboard</a>
                        </div>
                        
                        <p style="font-size: 14px; line-height: 1.6; color: #94a3b8; text-align: center;">
                            For security purposes, we recommend that you change your password after your first login.
                        </p>
                        
                        <div style="margin-top: 40px; padding-top: 30px; border-top: 1px solid #f1f5f9; text-align: center;">
                            <p style="margin: 0; color: #475569; font-weight: 600;">The Career Nexus Team</p>
                            <p style="margin: 5px 0 0; color: #94a3b8; font-size: 12px;">Bridging Professionals & Opportunities</p>
                        </div>
                    </div>
                </div>
                """.formatted(role.substring(0,1).toUpperCase()+role.substring(1), roleSpecificText, userId, toEmail, rawPassword, loginUrl);

            helper.setText(htmlBody, true);
            mailSender.send(message);
            System.out.println("Dispatched Welcome Email to: " + toEmail + " with credentials.");
        } catch (Exception e) {
            System.err.println("Welcome Email dispatch failed for: " + toEmail + " Error: " + e.getMessage());
        }
    }
}
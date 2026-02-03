package com.robin.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

@Service
@Slf4j
public class CertService {

    public String getCertificateHash(String certPath) {
        try (FileInputStream fis = new FileInputStream(certPath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
            byte[] publicKeyEncoded = cert.getPublicKey().getEncoded();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to generate certificate hash from {}", certPath, e);
            return null;
        }
    }
}

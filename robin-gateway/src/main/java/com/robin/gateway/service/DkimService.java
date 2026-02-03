package com.robin.gateway.service;

import com.robin.gateway.model.DkimKey;
import com.robin.gateway.model.Domain;
import com.robin.gateway.repository.DkimKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DkimService {

    private final DkimKeyRepository dkimKeyRepository;
    private final EncryptionService encryptionService;

    /**
     * Generate a new DKIM key pair for a domain
     */
    @Transactional
    public DkimKey generateKey(Domain domain, String selector) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            String privateKeyPem = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
            String publicKeyPem = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

            DkimKey dkimKey = DkimKey.builder()
                    .domain(domain)
                    .selector(selector)
                    .privateKey(encryptionService.encrypt(privateKeyPem))
                    .publicKey(publicKeyPem)
                    .status(DkimKey.DkimStatus.STANDBY)
                    .build();

            return dkimKeyRepository.save(dkimKey);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate RSA key pair", e);
            throw new RuntimeException("DKIM Key generation failed", e);
        }
    }

    public List<DkimKey> getKeysForDomain(Domain domain) {
        return dkimKeyRepository.findByDomain(domain);
    }

    public Optional<DkimKey> getActiveKey(Domain domain) {
        return dkimKeyRepository.findByDomainAndStatus(domain, DkimKey.DkimStatus.ACTIVE);
    }

    @Transactional
    public void activateKey(Long keyId) {
        DkimKey key = dkimKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("Key not found: " + keyId));

        // Deactivate old active key
        dkimKeyRepository.findByDomainAndStatus(key.getDomain(), DkimKey.DkimStatus.ACTIVE)
                .ifPresent(oldKey -> {
                    oldKey.setStatus(DkimKey.DkimStatus.DEPRECATED);
                    dkimKeyRepository.save(oldKey);
                });

        key.setStatus(DkimKey.DkimStatus.ACTIVE);
        key.setActivatedAt(java.time.LocalDateTime.now());
        dkimKeyRepository.save(key);
        log.info("Activated DKIM key {} for domain {}", key.getSelector(), key.getDomain().getDomain());
    }
}
